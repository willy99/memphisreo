package com.memphisreo.crm;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Ручне створення/редагування контакту агентом (docs/domain-model.md §5, зміни Фази 1). */
@Service
public class ClientService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ClientRepository clientRepository;
    private final ClientRequirementRepository requirementRepository;

    public ClientService(ClientRepository clientRepository, ClientRequirementRepository requirementRepository) {
        this.clientRepository = clientRepository;
        this.requirementRepository = requirementRepository;
    }

    public java.util.Optional<ClientRequirement> requirement(UUID clientId) {
        return requirementRepository.findByClientId(clientId);
    }

    public List<ClientRequirement> activeRequirements() {
        return requirementRepository.findByActiveTrue();
    }

    /** Запит клієнта: створити або повністю замінити. */
    @Transactional
    public ClientRequirement saveRequirement(UUID tenantId, UUID clientId, RequirementForm form) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("Client не знайдено: " + clientId));
        List<FieldError> errors = new ArrayList<>();
        if (form.roomsMin() != null && form.roomsMax() != null && form.roomsMin() > form.roomsMax()) {
            errors.add(new FieldError("roomsMax", "outOfRange"));
        }
        if (form.priceMin() != null && form.priceMax() != null && form.priceMin().compareTo(form.priceMax()) > 0) {
            errors.add(new FieldError("priceMax", "outOfRange"));
        }
        if (form.currency() != null && !java.util.Set.of("USD", "UAH", "EUR").contains(form.currency())) {
            errors.add(new FieldError("currency", "invalid"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Некоректний запит", errors);
        }
        ClientRequirement r = requirementRepository.findByClientId(clientId).orElseGet(() -> {
            ClientRequirement created = new ClientRequirement();
            created.setTenantId(tenantId);
            created.setClientId(client.getId());
            return created;
        });
        r.setPropertyType(blank(form.propertyType()) ? null : form.propertyType());
        r.setRoomsMin(form.roomsMin());
        r.setRoomsMax(form.roomsMax());
        r.setPriceMin(form.priceMin());
        r.setPriceMax(form.priceMax());
        r.setCurrency(form.currency() == null ? "USD" : form.currency());
        r.setAreaMin(form.areaMin());
        r.setDistricts(form.districts() == null ? new ArrayList<>() : form.districts().stream()
                .filter(d -> !blank(d)).map(String::trim).distinct().toList());
        r.setMarket(blank(form.market()) ? null : form.market());
        r.setMustHave(form.mustHave() == null ? new ArrayList<>() : new ArrayList<>(new java.util.LinkedHashSet<>(form.mustHave())));
        r.setNotes(blank(form.notes()) ? null : form.notes().trim());
        r.setActive(form.active() == null || form.active());
        r.setUpdatedAt(java.time.Instant.now());
        return requirementRepository.save(r);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    @Transactional
    public Client create(UUID tenantId, ClientForm form) {
        validate(form, null);
        Client client = new Client();
        client.setTenantId(tenantId);
        apply(client, form);
        return clientRepository.save(client);
    }

    @Transactional
    public Client update(UUID clientId, ClientForm form) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("Client не знайдено: " + clientId));
        validate(form, client);
        apply(client, form);
        return client;
    }

    private void validate(ClientForm form, Client existing) {
        List<FieldError> errors = new ArrayList<>();
        if (isBlank(form.firstName())) {
            errors.add(new FieldError("firstName", "required"));
        }
        if (isBlank(form.lastName())) {
            errors.add(new FieldError("lastName", "required"));
        }
        if (isBlank(form.email()) && isBlank(form.phone())) {
            errors.add(new FieldError("phone", "contactRequired"));
        }
        if (!isBlank(form.email())) {
            if (!EMAIL.matcher(form.email().trim()).matches()) {
                errors.add(new FieldError("email", "invalid"));
            } else if (clientRepository.findByEmail(form.email().trim())
                    .filter(other -> existing == null || !other.getId().equals(existing.getId())).isPresent()) {
                errors.add(new FieldError("email", "duplicate"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Некоректні дані контакту", errors);
        }
    }

    private static void apply(Client client, ClientForm form) {
        client.setFirstName(form.firstName().trim());
        client.setLastName(form.lastName().trim());
        client.setEmail(isBlank(form.email()) ? null : form.email().trim());
        client.setPhone(isBlank(form.phone()) ? null : form.phone().trim());
        client.setSource(form.source() != null ? form.source() : Client.Source.OTHER);
        client.setNotes(isBlank(form.notes()) ? null : form.notes().trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
