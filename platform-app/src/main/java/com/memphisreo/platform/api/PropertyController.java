package com.memphisreo.platform.api;

import com.memphisreo.platform.property.PropertyEditorDtos.FormSchema;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyCard;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyPayload;
import com.memphisreo.platform.property.PropertyEditorService;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Об'єкти нерухомості агенції. Без where tenant_id = ? — це робить Hibernate @TenantId і RLS (ADR-001). */
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyEditorService editorService;

    public PropertyController(PropertyEditorService editorService) {
        this.editorService = editorService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_VIEW.name())")
    public ResponseEntity<List<PropertyCard>> list() {
        return ResponseEntity.ok(editorService.cards());
    }

    @GetMapping("/form-schema")
    public ResponseEntity<FormSchema> formSchema() {
        return ResponseEntity.ok(editorService.schema());
    }

    /** Створює чернетку — форма далі автозберігається через PUT. */
    @PostMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_CREATE.name())")
    public ResponseEntity<PropertyDetails> create(@AuthenticationPrincipal AuthenticatedAgent principal,
                                                  @RequestBody PropertyPayload payload) {
        return ResponseEntity.ok(editorService.create(principal.tenantId(), principal.agentId(), payload));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_VIEW.name())")
    public ResponseEntity<PropertyDetails> get(@PathVariable UUID id) {
        return ResponseEntity.ok(editorService.details(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_EDIT.name())")
    public ResponseEntity<PropertyDetails> update(@AuthenticationPrincipal AuthenticatedAgent principal,
                                                  @PathVariable UUID id, @RequestBody PropertyPayload payload) {
        return ResponseEntity.ok(editorService.update(principal.tenantId(), principal.agentId(), id, payload));
    }

    /** Чернетка → активний об'єкт; 400 зі списком незаповнених обов'язкових полів. */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_EDIT.name())")
    public ResponseEntity<PropertyDetails> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(editorService.complete(id));
    }
}
