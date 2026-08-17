package com.memphisreo.platform.api;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.property.CreatePropertyRequest;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyRepository;
import com.memphisreo.property.PropertyService;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;
    private final PropertyRepository propertyRepository;

    public PropertyController(PropertyService propertyService, PropertyRepository propertyRepository) {
        this.propertyService = propertyService;
        this.propertyRepository = propertyRepository;
    }

    /** Без where tenant_id = ? — schema-per-tenant вже скопіювала це для нас. */
    @GetMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_VIEW.name())")
    public ResponseEntity<List<Property>> list() {
        return ResponseEntity.ok(propertyRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_CREATE.name())")
    public ResponseEntity<Property> create(@AuthenticationPrincipal AuthenticatedAgent principal,
                                            @RequestBody CreatePropertyRequest request) {
        Property property = propertyService.create(principal.tenantId(), principal.agentId(), request);
        return ResponseEntity.ok(property);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_VIEW.name())")
    public ResponseEntity<Property> get(@PathVariable UUID id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Property не знайдено: " + id));
        return ResponseEntity.ok(property);
    }
}
