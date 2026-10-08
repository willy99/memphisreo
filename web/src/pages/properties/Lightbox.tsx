import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { MediaView } from "../../api/types";

/** Перегляд медіа: ←/→ гортають, Esc закриває, фокус повертається на плитку. */
export function Lightbox({ items, startIndex, onClose }: { items: MediaView[]; startIndex: number; onClose: () => void }) {
  const { t } = useTranslation();
  const [index, setIndex] = useState(startIndex);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const item = items[index];

  useEffect(() => {
    const dialog = dialogRef.current;
    const opener = document.activeElement as HTMLElement | null;
    dialog?.showModal();
    return () => {
      dialog?.close();
      opener?.focus();
    };
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowRight") setIndex((i) => Math.min(i + 1, items.length - 1));
      if (e.key === "ArrowLeft") setIndex((i) => Math.max(i - 1, 0));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [items.length]);

  // Сусідні фото — заздалегідь, щоб гортання було миттєвим.
  useEffect(() => {
    [items[index - 1], items[index + 1]].forEach((m) => {
      if (m?.url && m.kind !== "VIDEO") new Image().src = m.url;
    });
  }, [index, items]);

  if (!item) return null;

  return (
    <dialog ref={dialogRef} className="lightbox" onCancel={onClose} onClick={(e) => e.target === dialogRef.current && onClose()}>
      <div className="lightbox-stage">
        {item.kind === "VIDEO" && item.url && (
          <video key={item.id} src={item.url} controls preload="metadata" playsInline className="lightbox-media">
            <a href={item.url}>{t("propertyForm.media.downloadVideo")}</a>
          </video>
        )}
        {item.kind === "VIDEO_LINK" && item.externalUrl && embedUrl(item.externalUrl) && (
          <iframe
            className="lightbox-media lightbox-embed"
            src={embedUrl(item.externalUrl)!}
            title={item.caption ?? "video"}
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            referrerPolicy="strict-origin-when-cross-origin"
          />
        )}
        {(item.kind === "PHOTO" || item.kind === "FLOORPLAN") && item.url && (
          <img src={item.url} alt={item.caption ?? ""} className="lightbox-media" />
        )}
      </div>
      <div className="lightbox-bar">
        <span>
          {index + 1} / {items.length}
          {item.caption && <> · {item.caption}</>}
        </span>
        <span className="lightbox-controls">
          <button type="button" className="icon-button" disabled={index === 0} onClick={() => setIndex(index - 1)} aria-label={t("propertyForm.media.prev")}>
            ←
          </button>
          <button type="button" className="icon-button" disabled={index === items.length - 1} onClick={() => setIndex(index + 1)} aria-label={t("propertyForm.media.next")}>
            →
          </button>
          <button type="button" className="icon-button" onClick={onClose} aria-label={t("propertyForm.media.close")}>
            ✕
          </button>
        </span>
      </div>
    </dialog>
  );
}

/** YouTube/Vimeo → URL для вбудовування (бекенд уже обмежив хости). */
export function embedUrl(url: string): string | null {
  try {
    const u = new URL(url);
    const host = u.hostname.replace(/^(www\.|m\.)/, "");
    if (host === "youtu.be") return `https://www.youtube-nocookie.com/embed/${u.pathname.slice(1)}`;
    if (host === "youtube.com") {
      const id = u.searchParams.get("v") ?? u.pathname.split("/").filter(Boolean).pop();
      return id ? `https://www.youtube-nocookie.com/embed/${id}` : null;
    }
    if (host === "vimeo.com") return `https://player.vimeo.com/video/${u.pathname.split("/").filter(Boolean)[0]}`;
    if (host === "player.vimeo.com") return url;
    return null;
  } catch {
    return null;
  }
}
