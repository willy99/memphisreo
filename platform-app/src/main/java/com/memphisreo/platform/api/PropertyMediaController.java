package com.memphisreo.platform.api;

import com.memphisreo.activity.ActivityEvent.SubjectType;
import com.memphisreo.activity.ActivityRecorder;
import com.memphisreo.media.MediaService;
import com.memphisreo.media.MediaView;
import com.memphisreo.media.PropertyMedia;
import com.memphisreo.property.PropertyService;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Медіатека об'єкта. Кожен виклик спершу шукає об'єкт у поточній агенції —
 * чужий об'єкт дає 404 до будь-якої роботи з файлами.
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/media")
public class PropertyMediaController {

    private static final String CAN_VIEW = "hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_VIEW.name())";
    private static final String CAN_EDIT = "hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_EDIT.name())";

    public record LinkRequest(String url) {
    }

    public record OrderRequest(PropertyMedia.Kind kind, List<UUID> ids) {
    }

    public record CaptionRequest(String caption) {
    }

    private final MediaService mediaService;
    private final PropertyService propertyService;
    private final ActivityRecorder activity;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    public PropertyMediaController(MediaService mediaService, PropertyService propertyService,
                                   ActivityRecorder activity, org.springframework.transaction.support.TransactionTemplate tx) {
        this.mediaService = mediaService;
        this.propertyService = propertyService;
        this.activity = activity;
        this.tx = tx;
    }

    private void record(AuthenticatedAgent p, UUID propertyId, String type, Map<String, Object> payload) {
        tx.executeWithoutResult(s -> activity.record(p.tenantId(), SubjectType.PROPERTY, propertyId, type, payload,
                p.agentId(), true));
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<List<MediaView>> list(@PathVariable UUID propertyId) {
        propertyService.get(propertyId);
        return ResponseEntity.ok(mediaService.list(propertyId));
    }

    @PostMapping
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<MediaView> upload(@AuthenticationPrincipal AuthenticatedAgent principal,
                                            @PathVariable UUID propertyId,
                                            @RequestParam PropertyMedia.Kind kind,
                                            @RequestParam MultipartFile file) {
        propertyService.get(propertyId);
        try (InputStream content = file.getInputStream()) {
            MediaView view = kind == PropertyMedia.Kind.VIDEO
                    ? mediaService.uploadVideo(principal.tenantId(), propertyId, principal.agentId(),
                            file.getOriginalFilename(), content, file.getSize())
                    : mediaService.uploadImage(principal.tenantId(), propertyId, principal.agentId(), kind,
                            file.getOriginalFilename(), content, file.getSize());
            record(principal, propertyId, "MEDIA_ADDED", Map.of("kind", kind.name()));
            return ResponseEntity.ok(view);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PostMapping("/links")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<MediaView> addLink(@AuthenticationPrincipal AuthenticatedAgent principal,
                                             @PathVariable UUID propertyId, @RequestBody LinkRequest request) {
        propertyService.get(propertyId);
        MediaView view = mediaService.addVideoLink(principal.tenantId(), propertyId, principal.agentId(), request.url());
        record(principal, propertyId, "MEDIA_ADDED", Map.of("kind", "VIDEO_LINK"));
        return ResponseEntity.ok(view);
    }

    @PutMapping("/order")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<List<MediaView>> reorder(@PathVariable UUID propertyId, @RequestBody OrderRequest request) {
        propertyService.get(propertyId);
        return ResponseEntity.ok(mediaService.reorder(propertyId, request.kind(), request.ids()));
    }

    @PutMapping("/{mediaId}/cover")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<List<MediaView>> setCover(@PathVariable UUID propertyId, @PathVariable UUID mediaId) {
        propertyService.get(propertyId);
        return ResponseEntity.ok(mediaService.setCover(propertyId, mediaId));
    }

    @PatchMapping("/{mediaId}")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<MediaView> updateCaption(@PathVariable UUID propertyId, @PathVariable UUID mediaId,
                                                   @RequestBody CaptionRequest request) {
        propertyService.get(propertyId);
        return ResponseEntity.ok(mediaService.updateCaption(propertyId, mediaId, request.caption()));
    }

    @DeleteMapping("/{mediaId}")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedAgent principal,
                                       @PathVariable UUID propertyId, @PathVariable UUID mediaId) {
        propertyService.get(propertyId);
        mediaService.delete(propertyId, mediaId);
        record(principal, propertyId, "MEDIA_REMOVED", Map.of());
        return ResponseEntity.noContent().build();
    }
}
