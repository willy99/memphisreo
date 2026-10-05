package com.memphisreo.platform.api;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Немає POST — Client заводиться лише через конвертацію Inquiry → Lead. */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientRepository clientRepository;

    public ClientController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_VIEW.name())")
    public ResponseEntity<List<Client>> list() {
        return ResponseEntity.ok(clientRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_VIEW.name())")
    public ResponseEntity<Client> get(@PathVariable UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Client не знайдено: " + id));
        return ResponseEntity.ok(client);
    }
}
