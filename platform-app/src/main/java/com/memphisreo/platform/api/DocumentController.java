package com.memphisreo.platform.api;

import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.document.Document;
import com.memphisreo.document.DocumentService;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyRepository;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import com.memphisreo.security.rbac.Permission;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Видимість LISTING_AGENT_AND_LAWYER — не новий RBAC-механізм, той самий
 * патерн "RBAC для типу дії, ownership для конкретного екземпляра"
 * (security.md §3): лістер об'єкта АБО власник DOCUMENT_VIEW_CONFIDENTIAL.
 * docs/domain-model.md §6.
 */
@RestController
public class DocumentController {

    private final DocumentService documentService;
    private final PropertyRepository propertyRepository;

    public DocumentController(DocumentService documentService, PropertyRepository propertyRepository) {
        this.documentService = documentService;
        this.propertyRepository = propertyRepository;
    }

    @PostMapping("/api/properties/{propertyId}/documents")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).DOCUMENT_MANAGE.name())")
    public ResponseEntity<Document> upload(@AuthenticationPrincipal AuthenticatedAgent principal,
                                            @PathVariable UUID propertyId,
                                            @RequestParam Document.Type type,
                                            @RequestParam(defaultValue = "AGENCY_INTERNAL") Document.Visibility visibility,
                                            @RequestParam MultipartFile file) {
        propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Property не знайдено: " + propertyId));
        try {
            Document document = documentService.upload(principal.tenantId(), propertyId, principal.agentId(),
                    type, visibility, file.getOriginalFilename(), file.getContentType(),
                    file.getInputStream(), file.getSize());
            return ResponseEntity.ok(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/api/properties/{propertyId}/documents")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).DOCUMENT_VIEW.name())")
    public ResponseEntity<List<Document>> list(@AuthenticationPrincipal AuthenticatedAgent principal,
                                                @PathVariable UUID propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Property не знайдено: " + propertyId));
        List<Document> visible = documentService.listByProperty(propertyId).stream()
                .filter(doc -> canView(principal, doc, property))
                .collect(Collectors.toList());
        return ResponseEntity.ok(visible);
    }

    @GetMapping("/api/documents/{id}/download")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).DOCUMENT_VIEW.name())")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal AuthenticatedAgent principal,
                                                          @PathVariable UUID id) {
        Document document = documentService.get(id);
        Property property = propertyRepository.findById(document.getPropertyId())
                .orElseThrow(() -> new NotFoundException("Property не знайдено: " + document.getPropertyId()));
        if (!canView(principal, document, property)) {
            throw new ForbiddenException("Немає доступу до цього документа");
        }
        InputStreamResource resource = new InputStreamResource(documentService.download(document));
        return ResponseEntity.ok()
                .contentType(document.getContentType() != null
                        ? MediaType.parseMediaType(document.getContentType())
                        : MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + document.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/api/documents/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).DOCUMENT_MANAGE.name())")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(documentService.get(id));
        return ResponseEntity.noContent().build();
    }

    private boolean canView(AuthenticatedAgent principal, Document document, Property property) {
        return switch (document.getVisibility()) {
            case PUBLIC, AGENCY_INTERNAL -> true;
            case LISTING_AGENT_AND_LAWYER ->
                    property.getCreatedByAgentId().equals(principal.agentId()) || hasConfidentialAccess();
        };
    }

    private boolean hasConfidentialAccess() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String required = Permission.DOCUMENT_VIEW_CONFIDENTIAL.name();
        return auth.getAuthorities().stream().anyMatch(a -> required.equals(a.getAuthority()));
    }
}
