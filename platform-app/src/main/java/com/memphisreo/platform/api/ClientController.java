package com.memphisreo.platform.api;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientForm;
import com.memphisreo.crm.ClientRepository;
import com.memphisreo.crm.ClientService;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Контакти агенції: покупці й продавці (роль — у контексті угоди, не на записі). */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientRepository clientRepository;
    private final ClientService clientService;

    public ClientController(ClientRepository clientRepository, ClientService clientService) {
        this.clientRepository = clientRepository;
        this.clientService = clientService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_VIEW.name())")
    public ResponseEntity<List<Client>> list() {
        return ResponseEntity.ok(clientRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_VIEW.name())")
    public ResponseEntity<Client> get(@PathVariable UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Client не знайдено: " + id));
        return ResponseEntity.ok(client);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_MANAGE.name())")
    public ResponseEntity<Client> create(@AuthenticationPrincipal AuthenticatedAgent principal,
                                         @RequestBody ClientForm form) {
        return ResponseEntity.ok(clientService.create(principal.tenantId(), form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_MANAGE.name())")
    public ResponseEntity<Client> update(@PathVariable UUID id, @RequestBody ClientForm form) {
        return ResponseEntity.ok(clientService.update(id, form));
    }
}
