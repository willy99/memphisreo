package com.memphisreo.media;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import com.memphisreo.common.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Медіатека об'єкта: фото, планування, відео-файли, відео-посилання.
 *
 * Завантаження йде через бекенд (multipart), а не напряму в S3: так тип
 * файлу перевіряється за сигнатурою, а фото перекодовуються до запису у
 * сховище (без EXIF/GPS). Перехід на presigned upload — коли обсяги відео
 * цього вимагатимуть (docs/property-form.md §4).
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;
    static final int MAX_PHOTOS = 50;
    static final int MAX_FLOORPLANS = 20;
    static final int MAX_VIDEOS = 5;
    private static final Duration URL_TTL = Duration.ofHours(1);
    private static final Set<String> VIDEO_HOSTS = Set.of(
            "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "vimeo.com", "www.vimeo.com", "player.vimeo.com");

    private final PropertyMediaRepository repository;
    private final ObjectStorage storage;
    private final ImageProcessor imageProcessor;
    private final TransactionTemplate transactionTemplate;

    public MediaService(PropertyMediaRepository repository, ObjectStorage storage, ImageProcessor imageProcessor,
                        TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.storage = storage;
        this.imageProcessor = imageProcessor;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Фото або планування: перевірка сигнатури, перекодування, запис варіантів.
     * Без @Transactional навколо: з'єднання з БД не тримається, поки файл
     * обробляється й іде в сховище.
     */
    public MediaView uploadImage(UUID tenantId, UUID propertyId, UUID agentId, PropertyMedia.Kind kind,
                                 String filename, InputStream content, long size) {
        if (kind != PropertyMedia.Kind.PHOTO && kind != PropertyMedia.Kind.FLOORPLAN) {
            throw invalid("kind", "invalid");
        }
        if (size > MAX_IMAGE_BYTES) {
            throw invalid("file", "fileTooLarge");
        }
        enforceLimit(propertyId, kind);
        byte[] bytes = readAll(content);
        FileSignature signature = FileSignature.detect(Arrays.copyOf(bytes, Math.min(bytes.length, FileSignature.HEADER_LENGTH)))
                .orElseThrow(() -> invalid("file", "unsupportedType"));
        if (signature == FileSignature.HEIC) {
            throw invalid("file", "heicNotSupported");
        }
        if (!signature.isProcessableImage()) {
            throw invalid("file", "unsupportedType");
        }
        ImageProcessor.Processed processed = imageProcessor.process(bytes);

        UUID mediaId = UUID.randomUUID();
        String prefix = keyPrefix(tenantId, propertyId, mediaId);
        String largeKey = prefix + "large.jpg";
        String thumbKey = prefix + "thumb.jpg";
        storage.put(largeKey, new ByteArrayInputStream(processed.large().jpeg()), processed.large().jpeg().length, "image/jpeg");
        storage.put(thumbKey, new ByteArrayInputStream(processed.thumb().jpeg()), processed.thumb().jpeg().length, "image/jpeg");

        PropertyMedia media = newMedia(tenantId, propertyId, agentId, kind, filename);
        media.setLargeKey(largeKey);
        media.setThumbKey(thumbKey);
        media.setMimeType("image/jpeg");
        media.setSizeBytes((long) processed.large().jpeg().length);
        media.setWidth(processed.large().width());
        media.setHeight(processed.large().height());
        return register(media, List.of(largeKey, thumbKey));
    }

    /** Відео-файл MP4/MOV — зберігається як є і програється з підписаного посилання. Без транзакції — див. вище. */
    public MediaView uploadVideo(UUID tenantId, UUID propertyId, UUID agentId, String filename,
                                 InputStream content, long size) {
        if (size > MAX_VIDEO_BYTES) {
            throw invalid("file", "fileTooLarge");
        }
        enforceLimit(propertyId, PropertyMedia.Kind.VIDEO);
        BufferedInputStream input = new BufferedInputStream(content);
        FileSignature signature = detectStreaming(input)
                .filter(FileSignature::isVideo)
                .orElseThrow(() -> invalid("file", "unsupportedType"));

        UUID mediaId = UUID.randomUUID();
        String key = keyPrefix(tenantId, propertyId, mediaId)
                + (signature == FileSignature.QUICKTIME ? "original.mov" : "original.mp4");
        storage.put(key, input, size, signature.mimeType());

        PropertyMedia media = newMedia(tenantId, propertyId, agentId, PropertyMedia.Kind.VIDEO, filename);
        media.setOriginalKey(key);
        media.setMimeType(signature.mimeType());
        media.setSizeBytes(size);
        return register(media, List.of(key));
    }

    /** Посилання на YouTube/Vimeo — лише з дозволених хостів, лише https. */
    public MediaView addVideoLink(UUID tenantId, UUID propertyId, UUID agentId, String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException e) {
            throw invalid("url", "invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || !VIDEO_HOSTS.contains(uri.getHost().toLowerCase())) {
            throw invalid("url", "unsupportedVideoHost");
        }
        PropertyMedia media = newMedia(tenantId, propertyId, agentId, PropertyMedia.Kind.VIDEO_LINK, null);
        media.setExternalUrl(uri.toString());
        return register(media, List.of());
    }

    public List<MediaView> list(UUID propertyId) {
        return repository.findByPropertyIdOrderByKindAscPositionAsc(propertyId).stream().map(this::toView).toList();
    }

    /** Новий порядок однієї категорії медіа — повний список id у потрібному порядку. */
    @Transactional
    public List<MediaView> reorder(UUID propertyId, PropertyMedia.Kind kind, List<UUID> orderedIds) {
        List<PropertyMedia> items = repository.findByPropertyIdAndKindOrderByPositionAsc(propertyId, kind);
        Set<UUID> existing = items.stream().map(PropertyMedia::getId).collect(Collectors.toSet());
        if (orderedIds == null || orderedIds.size() != items.size() || !existing.equals(Set.copyOf(orderedIds))) {
            throw invalid("ids", "invalid");
        }
        Map<UUID, PropertyMedia> byId = items.stream().collect(Collectors.toMap(PropertyMedia::getId, m -> m));
        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).setPosition(i);
        }
        return list(propertyId);
    }

    @Transactional
    public List<MediaView> setCover(UUID propertyId, UUID mediaId) {
        repository.lockProperty(propertyId.toString());
        PropertyMedia media = get(propertyId, mediaId);
        if (media.getKind() != PropertyMedia.Kind.PHOTO) {
            throw invalid("kind", "coverMustBePhoto");
        }
        List<PropertyMedia> photos = repository.findByPropertyIdAndKindOrderByPositionAsc(propertyId, PropertyMedia.Kind.PHOTO);
        photos.forEach(p -> p.setCover(false));
        repository.flush(); // унікальний індекс "одна обкладинка" — спершу зняти стару
        media.setCover(true);
        return list(propertyId);
    }

    @Transactional
    public MediaView updateCaption(UUID propertyId, UUID mediaId, String caption) {
        PropertyMedia media = get(propertyId, mediaId);
        String trimmed = caption == null ? null : caption.trim();
        if (trimmed != null && trimmed.length() > 300) {
            throw invalid("caption", "tooLong");
        }
        media.setCaption(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        return toView(media);
    }

    /** Видалення; якщо видалено обкладинку — нею стає перше з решти фото. */
    @Transactional
    public void delete(UUID propertyId, UUID mediaId) {
        repository.lockProperty(propertyId.toString());
        PropertyMedia media = get(propertyId, mediaId);
        boolean wasCover = media.isCover();
        repository.delete(media);
        repository.flush();
        if (wasCover) {
            repository.findByPropertyIdAndKindOrderByPositionAsc(propertyId, PropertyMedia.Kind.PHOTO).stream()
                    .findFirst().ifPresent(next -> next.setCover(true));
        }
        // Запис уже видалено; "сирота" у сховищі не блокує користувача.
        for (String key : new String[]{media.getLargeKey(), media.getThumbKey(), media.getOriginalKey()}) {
            if (key != null) {
                deleteQuietly(key);
            }
        }
    }

    /** Обкладинки для списку об'єктів: propertyId → підписане посилання на мініатюру. */
    public Map<UUID, String> coverThumbs(Collection<UUID> propertyIds) {
        if (propertyIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        for (PropertyMedia media : repository.findByPropertyIdInAndCoverTrue(propertyIds)) {
            result.put(media.getPropertyId(), sign(media.getThumbKey()));
        }
        return result;
    }

    public Map<UUID, Long> photoCounts(Collection<UUID> propertyIds) {
        if (propertyIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByPropertyIdInAndKind(propertyIds, PropertyMedia.Kind.PHOTO).stream()
                .collect(Collectors.groupingBy(PropertyMedia::getPropertyId, Collectors.counting()));
    }

    /**
     * Короткий крок у БД після важкої роботи (обробка, запис у сховище): під
     * блокуванням об'єкта — ліміт, позиція (max+1) і чи це перше фото (обкладинка).
     * Не вдалось зареєструвати — прибираємо вже записані файли.
     */
    private MediaView register(PropertyMedia media, List<String> storedKeys) {
        try {
            return transactionTemplate.execute(status -> {
                UUID propertyId = media.getPropertyId();
                repository.lockProperty(propertyId.toString());
                enforceLimit(propertyId, media.getKind());
                media.setPosition(repository.maxPosition(propertyId, media.getKind()) + 1);
                media.setCover(media.getKind() == PropertyMedia.Kind.PHOTO
                        && !repository.existsByPropertyIdAndCoverTrue(propertyId));
                return toView(repository.save(media));
            });
        } catch (RuntimeException e) {
            storedKeys.forEach(this::deleteQuietly);
            throw e;
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Не вдалося видалити об'єкт сховища {}: {}", key, e.toString());
        }
    }

    private PropertyMedia get(UUID propertyId, UUID mediaId) {
        return repository.findById(mediaId)
                .filter(m -> m.getPropertyId().equals(propertyId))
                .orElseThrow(() -> new NotFoundException("Медіа не знайдено: " + mediaId));
    }

    private PropertyMedia newMedia(UUID tenantId, UUID propertyId, UUID agentId, PropertyMedia.Kind kind, String filename) {
        PropertyMedia media = new PropertyMedia();
        media.setTenantId(tenantId);
        media.setPropertyId(propertyId);
        media.setUploadedByAgentId(agentId);
        media.setKind(kind);
        media.setOriginalFilename(filename != null && filename.length() > 255 ? filename.substring(0, 255) : filename);
        return media;
    }

    private void enforceLimit(UUID propertyId, PropertyMedia.Kind kind) {
        boolean full = switch (kind) {
            case PHOTO -> repository.countByPropertyIdAndKindIn(propertyId, EnumSet.of(kind)) >= MAX_PHOTOS;
            case FLOORPLAN -> repository.countByPropertyIdAndKindIn(propertyId, EnumSet.of(kind)) >= MAX_FLOORPLANS;
            case VIDEO, VIDEO_LINK -> repository.countByPropertyIdAndKindIn(propertyId,
                    EnumSet.of(PropertyMedia.Kind.VIDEO, PropertyMedia.Kind.VIDEO_LINK)) >= MAX_VIDEOS;
        };
        if (full) {
            throw invalid("file", "limitReached");
        }
    }

    private MediaView toView(PropertyMedia m) {
        String thumb = m.getThumbKey() != null ? sign(m.getThumbKey()) : null;
        String full = m.getLargeKey() != null ? sign(m.getLargeKey())
                : m.getOriginalKey() != null ? sign(m.getOriginalKey()) : null;
        return new MediaView(m.getId(), m.getKind(), m.getPosition(), m.isCover(), m.getCaption(), thumb, full,
                m.getExternalUrl(), m.getMimeType(), m.getSizeBytes(), m.getWidth(), m.getHeight(),
                m.getOriginalFilename());
    }

    private String sign(String key) {
        return storage.presignedGetUrl(key, URL_TTL).toString();
    }

    /** Ключі сховища: tenant — перший сегмент (видалення агенції = префікс, ADR-001). */
    private static String keyPrefix(UUID tenantId, UUID propertyId, UUID mediaId) {
        return "tenants/%s/properties/%s/media/%s/".formatted(tenantId, propertyId, mediaId);
    }

    private static Optional<FileSignature> detectStreaming(BufferedInputStream input) {
        try {
            input.mark(FileSignature.HEADER_LENGTH);
            byte[] header = input.readNBytes(FileSignature.HEADER_LENGTH);
            input.reset();
            return FileSignature.detect(header);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] readAll(InputStream content) {
        try {
            return content.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ValidationException invalid(String field, String code) {
        return new ValidationException("Некоректний медіафайл", List.of(new FieldError(field, code)));
    }
}
