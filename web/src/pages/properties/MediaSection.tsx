import { useCallback, useEffect, useRef, useState, type DragEvent } from "react";
import { useTranslation } from "react-i18next";
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  TouchSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import { SortableContext, arrayMove, rectSortingStrategy, sortableKeyboardCoordinates, useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { api, ApiError, uploadWithProgress } from "../../api/client";
import type { MediaKind, MediaView } from "../../api/types";
import { Lightbox } from "./Lightbox";

const MAX_IMAGE_BYTES = 20 * 1024 * 1024;
const MAX_VIDEO_BYTES = 200 * 1024 * 1024;
const PARALLEL_UPLOADS = 3;
const UNDO_MS = 5000;

type Tab = "PHOTO" | "FLOORPLAN" | "VIDEO";

interface Upload {
  tempId: string;
  kind: MediaKind;
  name: string;
  previewUrl: string | null;
  progress: number;
  error: string | null;
}

interface MediaSectionProps {
  token: string;
  propertyId: string | null;
  /** Створює чернетку, якщо її ще нема — медіа потребує id об'єкта. */
  ensurePropertyId: () => Promise<string>;
  media: MediaView[];
  onMediaChange: (media: MediaView[]) => void;
  recommendedPhotos: number;
  notify: (message: string, action?: { label: string; run: () => void }) => void;
}

export function MediaSection({ token, propertyId, ensurePropertyId, media, onMediaChange, recommendedPhotos, notify }: MediaSectionProps) {
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>("PHOTO");
  const [uploads, setUploads] = useState<Upload[]>([]);
  const [lightbox, setLightbox] = useState<{ items: MediaView[]; index: number } | null>(null);
  const mediaRef = useRef(media);
  mediaRef.current = media;
  // Оновлює ref синхронно: кілька завантажень, що завершились до рендера,
  // не перезапишуть одне одного.
  const commit = useCallback(
    (next: MediaView[]) => {
      mediaRef.current = next;
      onMediaChange(next);
    },
    [onMediaChange],
  );
  const pendingDeletes = useRef(new Map<string, { timer: number; run: () => void }>());

  // Відкладені видалення не губляться, якщо агент пішов зі сторінки до кінця "Скасувати".
  useEffect(() => {
    const pending = pendingDeletes.current;
    return () => {
      pending.forEach(({ timer, run }) => {
        window.clearTimeout(timer);
        run();
      });
    };
  }, []);

  const byKind = (kinds: MediaKind[]) => media.filter((m) => kinds.includes(m.kind)).sort((a, b) => a.position - b.position);
  const photos = byKind(["PHOTO"]);
  const floorplans = byKind(["FLOORPLAN"]);
  const videos = byKind(["VIDEO", "VIDEO_LINK"]);
  const current = tab === "PHOTO" ? photos : tab === "FLOORPLAN" ? floorplans : videos;

  const startUploads = useCallback(
    async (files: File[], kind: MediaKind) => {
      const accepted: { file: File; upload: Upload }[] = [];
      for (const file of files) {
        const error = validateFile(file, kind, t);
        const upload: Upload = {
          tempId: crypto.randomUUID(),
          kind,
          name: file.name,
          previewUrl: kind !== "VIDEO" && !error ? URL.createObjectURL(file) : null,
          progress: 0,
          error,
        };
        accepted.push({ file, upload });
      }
      setUploads((u) => [...u, ...accepted.map((a) => a.upload)]);
      const queue = accepted.filter((a) => !a.upload.error);
      if (!queue.length) return;

      let id: string;
      try {
        id = await ensurePropertyId();
      } catch {
        setUploads((u) => u.map((x) => (queue.some((q) => q.upload.tempId === x.tempId) ? { ...x, error: t("propertyForm.media.saveFirst") } : x)));
        return;
      }

      const worker = async () => {
        for (let next = queue.shift(); next; next = queue.shift()) {
          const { file, upload } = next;
          const form = new FormData();
          form.append("kind", kind);
          form.append("file", file);
          try {
            const view = await uploadWithProgress<MediaView>(`/api/properties/${id}/media`, form, token, (p) =>
              setUploads((u) => u.map((x) => (x.tempId === upload.tempId ? { ...x, progress: p } : x))),
            ).promise;
            commit([...mediaRef.current, view]);
            setUploads((u) => u.filter((x) => x.tempId !== upload.tempId));
            if (upload.previewUrl) URL.revokeObjectURL(upload.previewUrl);
          } catch (err) {
            const code = err instanceof ApiError ? err.errors[0]?.code : undefined;
            const message = code ? t(`propertyForm.errors.${code}`, { defaultValue: t("propertyForm.media.uploadFailed") }) : t("propertyForm.media.uploadFailed");
            setUploads((u) => u.map((x) => (x.tempId === upload.tempId ? { ...x, error: message } : x)));
          }
        }
      };
      await Promise.all(Array.from({ length: Math.min(PARALLEL_UPLOADS, queue.length) }, worker));
    },
    [commit, ensurePropertyId, t, token],
  );

  async function reorder(kind: MediaKind, ordered: MediaView[]) {
    const previous = mediaRef.current;
    const positions = new Map(ordered.map((m, i) => [m.id, i]));
    commit(previous.map((m) => (positions.has(m.id) ? { ...m, position: positions.get(m.id)! } : m)));
    if (!propertyId) return;
    try {
      await api.put(`/api/properties/${propertyId}/media/order`, { kind, ids: ordered.map((m) => m.id) }, token);
    } catch {
      commit(previous);
      notify(t("propertyForm.media.reorderFailed"));
    }
  }

  async function makeCover(item: MediaView) {
    if (!propertyId) return;
    try {
      const updated = await api.put<MediaView[]>(`/api/properties/${propertyId}/media/${item.id}/cover`, {}, token);
      commit(updated);
      // Обкладинка — і перше фото в галереї (як її бачать покупці).
      const ordered = updated.filter((m) => m.kind === "PHOTO").sort((a, b) => a.position - b.position);
      const cover = ordered.find((m) => m.cover);
      if (cover && ordered[0].id !== cover.id) {
        await reorder("PHOTO", [cover, ...ordered.filter((m) => m.id !== cover.id)]);
      }
    } catch {
      notify(t("propertyForm.media.actionFailed"));
    }
  }

  async function saveCaption(item: MediaView, caption: string) {
    if (!propertyId || (item.caption ?? "") === caption) return;
    try {
      const updated = await api.patch<MediaView>(`/api/properties/${propertyId}/media/${item.id}`, { caption }, token);
      commit(mediaRef.current.map((m) => (m.id === updated.id ? updated : m)));
    } catch {
      notify(t("propertyForm.media.actionFailed"));
    }
  }

  function remove(item: MediaView) {
    if (!propertyId) return;
    const snapshot = mediaRef.current;
    let remaining = snapshot.filter((m) => m.id !== item.id);
    // Видалили обкладинку — локально позначаємо наступне фото (так зробить і сервер).
    if (item.cover) {
      const next = remaining.filter((m) => m.kind === "PHOTO").sort((a, b) => a.position - b.position)[0];
      if (next) remaining = remaining.map((m) => (m.id === next.id ? { ...m, cover: true } : m));
    }
    commit(remaining);
    const run = () => {
      pendingDeletes.current.delete(item.id);
      api.del(`/api/properties/${propertyId}/media/${item.id}`, token).catch(() => notify(t("propertyForm.media.actionFailed")));
    };
    const timer = window.setTimeout(run, UNDO_MS);
    pendingDeletes.current.set(item.id, { timer, run });
    notify(t("propertyForm.media.deleted"), {
      label: t("propertyForm.media.undo"),
      run: () => {
        window.clearTimeout(timer);
        pendingDeletes.current.delete(item.id);
        commit(snapshot);
      },
    });
  }

  async function addLink(url: string) {
    try {
      const id = await ensurePropertyId();
      const view = await api.post<MediaView>(`/api/properties/${id}/media/links`, { url }, token);
      commit([...mediaRef.current, view]);
      return true;
    } catch (err) {
      const code = err instanceof ApiError ? err.errors[0]?.code : undefined;
      notify(t(`propertyForm.errors.${code ?? "unsupportedVideoHost"}`));
      return false;
    }
  }

  const tabKind: MediaKind = tab === "VIDEO" ? "VIDEO" : tab;
  const tabUploads = uploads.filter((u) => (tab === "VIDEO" ? u.kind === "VIDEO" : u.kind === tab));

  return (
    <div className="media-section">
      <div className="media-tabs" role="tablist">
        {(["PHOTO", "FLOORPLAN", "VIDEO"] as Tab[]).map((key) => {
          const count = key === "PHOTO" ? photos.length : key === "FLOORPLAN" ? floorplans.length : videos.length;
          return (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={tab === key}
              className={`media-tab${tab === key ? " active" : ""}`}
              onClick={() => setTab(key)}
            >
              {t(`propertyForm.media.tabs.${key}`)} <span className="media-tab-count">{count}</span>
            </button>
          );
        })}
      </div>

      {tab === "PHOTO" && photos.length < recommendedPhotos && (
        <p className="hint">{t("propertyForm.media.recommend", { count: recommendedPhotos, have: photos.length })}</p>
      )}

      <DropZone kind={tabKind} onFiles={(files) => startUploads(files, tabKind)} />
      {tab === "VIDEO" && <VideoLinkForm onAdd={addLink} />}

      {tabUploads.length > 0 && (
        <ul className="upload-list">
          {tabUploads.map((u) => (
            <li key={u.tempId} className={`upload-item${u.error ? " upload-failed" : ""}`}>
              {u.previewUrl ? <img src={u.previewUrl} alt="" /> : <span className="upload-icon">🎞</span>}
              <span className="upload-name">{u.name}</span>
              {u.error ? (
                <>
                  <span className="field-error">{u.error}</span>
                  <button type="button" className="link-button" onClick={() => setUploads((x) => x.filter((y) => y.tempId !== u.tempId))}>
                    {t("propertyForm.media.dismiss")}
                  </button>
                </>
              ) : (
                <span className="upload-progress" aria-label={t("propertyForm.media.uploading")}>
                  <span style={{ width: `${Math.round(u.progress * 100)}%` }} />
                </span>
              )}
            </li>
          ))}
        </ul>
      )}

      {current.length > 0 && (
        <SortableGrid
          items={current}
          onReorder={(ordered) => reorder(tab === "VIDEO" ? ordered[0].kind : tabKind, ordered)}
          onOpen={(index) => setLightbox({ items: current, index })}
          onCover={makeCover}
          onCaption={saveCaption}
          onDelete={remove}
          reorderable={tab !== "VIDEO"}
        />
      )}

      {lightbox && <Lightbox items={lightbox.items} startIndex={lightbox.index} onClose={() => setLightbox(null)} />}
    </div>
  );
}

function validateFile(file: File, kind: MediaKind, t: (key: string, opts?: Record<string, unknown>) => string): string | null {
  const name = file.name.toLowerCase();
  if (kind === "VIDEO") {
    if (!(file.type === "video/mp4" || file.type === "video/quicktime" || /\.(mp4|mov)$/.test(name))) return t("propertyForm.errors.unsupportedType");
    if (file.size > MAX_VIDEO_BYTES) return t("propertyForm.errors.fileTooLarge");
    return null;
  }
  if (file.type === "image/heic" || file.type === "image/heif" || /\.(heic|heif)$/.test(name)) return t("propertyForm.errors.heicNotSupported");
  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) return t("propertyForm.errors.unsupportedType");
  if (file.size > MAX_IMAGE_BYTES) return t("propertyForm.errors.fileTooLarge");
  return null;
}

function DropZone({ kind, onFiles }: { kind: MediaKind; onFiles: (files: File[]) => void }) {
  const { t } = useTranslation();
  const [over, setOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const cameraRef = useRef<HTMLInputElement>(null);
  const accept = kind === "VIDEO" ? "video/mp4,video/quicktime,.mp4,.mov" : "image/*";

  function handleDrop(event: DragEvent) {
    event.preventDefault();
    setOver(false);
    onFiles(Array.from(event.dataTransfer.files));
  }

  return (
    <div
      className={`dropzone${over ? " dropzone-over" : ""}`}
      onDragOver={(e) => {
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={handleDrop}
    >
      <span className="dropzone-icon" aria-hidden="true">
        {kind === "VIDEO" ? "🎬" : "🖼"}
      </span>
      <p className="dropzone-title">{t(`propertyForm.media.drop.${kind}`)}</p>
      <p className="hint">{t(`propertyForm.media.limits.${kind}`)}</p>
      <div className="dropzone-actions">
        <button type="button" onClick={() => inputRef.current?.click()}>
          {t("propertyForm.media.browse")}
        </button>
        {kind === "PHOTO" && (
          <button type="button" className="button-secondary camera-button" onClick={() => cameraRef.current?.click()}>
            📷 {t("propertyForm.media.camera")}
          </button>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={accept}
        hidden
        onChange={(e) => {
          onFiles(Array.from(e.target.files ?? []));
          e.target.value = "";
        }}
      />
      {/* Окрема кнопка камери: без capture основний вибір лишає доступ до галереї телефона. */}
      <input
        ref={cameraRef}
        type="file"
        accept="image/*"
        capture="environment"
        hidden
        onChange={(e) => {
          onFiles(Array.from(e.target.files ?? []));
          e.target.value = "";
        }}
      />
    </div>
  );
}

function VideoLinkForm({ onAdd }: { onAdd: (url: string) => Promise<boolean> }) {
  const { t } = useTranslation();
  const [url, setUrl] = useState("");
  return (
    <form
      className="video-link-form"
      onSubmit={async (e) => {
        e.preventDefault();
        if (url.trim() && (await onAdd(url.trim()))) setUrl("");
      }}
    >
      <input
        type="url"
        value={url}
        placeholder="https://www.youtube.com/watch?v=…"
        aria-label={t("propertyForm.media.linkLabel")}
        onChange={(e) => setUrl(e.target.value)}
      />
      <button type="submit" className="button-secondary">
        {t("propertyForm.media.addLink")}
      </button>
    </form>
  );
}

interface GridProps {
  items: MediaView[];
  reorderable: boolean;
  onReorder: (ordered: MediaView[]) => void;
  onOpen: (index: number) => void;
  onCover: (item: MediaView) => void;
  onCaption: (item: MediaView, caption: string) => void;
  onDelete: (item: MediaView) => void;
}

function SortableGrid({ items, reorderable, onReorder, onOpen, onCover, onCaption, onDelete }: GridProps) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    // На телефоні — довге натискання, щоб перетягування не заважало прокрутці.
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const from = items.findIndex((m) => m.id === active.id);
    const to = items.findIndex((m) => m.id === over.id);
    onReorder(arrayMove(items, from, to));
  }

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={items.map((m) => m.id)} strategy={rectSortingStrategy}>
        <ul className="media-grid">
          {items.map((item, index) => (
            <MediaTile
              key={item.id}
              item={item}
              index={index}
              count={items.length}
              reorderable={reorderable}
              onOpen={() => onOpen(index)}
              onCover={() => onCover(item)}
              onCaption={(c) => onCaption(item, c)}
              onDelete={() => onDelete(item)}
              onMove={(delta) => onReorder(arrayMove(items, index, index + delta))}
            />
          ))}
        </ul>
      </SortableContext>
    </DndContext>
  );
}

function MediaTile({
  item,
  index,
  count,
  reorderable,
  onOpen,
  onCover,
  onCaption,
  onDelete,
  onMove,
}: {
  item: MediaView;
  index: number;
  count: number;
  reorderable: boolean;
  onOpen: () => void;
  onCover: () => void;
  onCaption: (caption: string) => void;
  onDelete: () => void;
  onMove: (delta: number) => void;
}) {
  const { t } = useTranslation();
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: item.id, disabled: !reorderable });
  const [caption, setCaption] = useState(item.caption ?? "");
  useEffect(() => setCaption(item.caption ?? ""), [item.caption]);

  return (
    <li
      ref={setNodeRef}
      className={`media-tile${isDragging ? " dragging" : ""}`}
      style={{ transform: CSS.Transform.toString(transform), transition }}
    >
      <button type="button" className="media-thumb" onClick={onOpen} aria-label={t("propertyForm.media.open", { n: index + 1 })}>
        {item.thumbUrl ? (
          <img src={item.thumbUrl} alt={item.caption ?? ""} loading="lazy" />
        ) : (
          <span className="media-placeholder">{item.kind === "VIDEO_LINK" ? "▶︎ " + hostOf(item.externalUrl) : "🎞 " + (item.originalFilename ?? "")}</span>
        )}
        {item.cover && <span className="cover-badge">{t("propertyForm.media.cover")}</span>}
        {reorderable && <span className="position-badge">{index + 1}</span>}
      </button>
      {reorderable && (
        <span className="drag-handle" {...attributes} {...listeners} aria-label={t("propertyForm.media.drag")} title={t("propertyForm.media.drag")}>
          ⠿
        </span>
      )}
      <input
        className="media-caption"
        value={caption}
        maxLength={300}
        placeholder={t("propertyForm.media.captionPlaceholder")}
        aria-label={t("propertyForm.media.captionLabel")}
        onChange={(e) => setCaption(e.target.value)}
        onBlur={() => onCaption(caption.trim())}
        onKeyDown={(e) => e.key === "Enter" && (e.target as HTMLInputElement).blur()}
      />
      <div className="media-actions">
        {item.kind === "PHOTO" && !item.cover && (
          <button type="button" className="link-button" onClick={onCover}>
            {t("propertyForm.media.makeCover")}
          </button>
        )}
        {reorderable && (
          <span className="move-buttons">
            <button type="button" className="icon-button" disabled={index === 0} onClick={() => onMove(-1)} aria-label={t("propertyForm.media.moveLeft")}>
              ←
            </button>
            <button type="button" className="icon-button" disabled={index === count - 1} onClick={() => onMove(1)} aria-label={t("propertyForm.media.moveRight")}>
              →
            </button>
          </span>
        )}
        <button type="button" className="icon-button danger" onClick={onDelete} aria-label={t("propertyForm.media.delete")} title={t("propertyForm.media.delete")}>
          🗑
        </button>
      </div>
    </li>
  );
}

function hostOf(url: string | null): string {
  try {
    return url ? new URL(url).hostname.replace(/^www\./, "") : "";
  } catch {
    return "";
  }
}
