package com.memphisreo.platform;

import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;

import com.memphisreo.crm.Client;
import com.memphisreo.crm.Lead;
import com.memphisreo.crm.LeadActivity;
import com.memphisreo.inquiry.Inquiry;
import com.memphisreo.platform.publicsite.PublicDtos.InquiryRequest;
import com.memphisreo.platform.sale.SaleDtos.SaleView;
import com.memphisreo.platform.api.LeadController;
import com.memphisreo.property.Property;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Inquiry (публічна форма) → Lead (конвертація) → активність — Фаза 2, docs/domain-model.md §5. */
class InquiryLeadFlowIT extends AbstractIntegrationTest {

    @Test
    void publicInquiry_convertsToLead_andSupportsActivityAndStatusUpdates() {
        String slug = "crm-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "admin@" + slug + ".ua";
        String password = "SuperSecret123!";

        register(slug, email, password, "UA");
        String token = login(email, password);
        PropertyDetails property = createProperty(token);
        SaleView sale = listProperty(token, property.id());

        InquiryRequest inquiryRequest = new InquiryRequest(
                "Ірина Петренко", "+380501234567", "iryna@example.com", "Цікавить перегляд у вихідні");
        ResponseEntity<Inquiry> inquiryResponse = restTemplate.postForEntity(
                "/api/public/agencies/" + slug + "/properties/" + property.id() + "/inquiries",
                inquiryRequest, Inquiry.class);
        assertThat(inquiryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Inquiry inquiry = inquiryResponse.getBody();
        assertThat(inquiry.getStatus()).isEqualTo(Inquiry.Status.NEW);
        assertThat(inquiry.getAgentId()).isNotNull();

        ResponseEntity<Lead> convertResponse = restTemplate.exchange(
                "/api/inquiries/" + inquiry.getId() + "/convert", HttpMethod.POST, authed(token), Lead.class);
        assertThat(convertResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Lead lead = convertResponse.getBody();
        assertThat(lead.getStatus()).isEqualTo(Lead.Status.NEW);
        assertThat(lead.getListingId()).isEqualTo(sale.listingId());
        assertThat(lead.getSourceInquiryId()).isEqualTo(inquiry.getId());

        // Повторна конвертація того самого inquiry — заборонена.
        ResponseEntity<String> secondConvert = restTemplate.exchange(
                "/api/inquiries/" + inquiry.getId() + "/convert", HttpMethod.POST, authed(token), String.class);
        assertThat(secondConvert.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Client[]> clients = restTemplate.exchange(
                "/api/clients", HttpMethod.GET, authed(token), Client[].class);
        assertThat(clients.getBody()).extracting(Client::getEmail).contains("iryna@example.com");

        ResponseEntity<Lead> statusUpdate = restTemplate.exchange(
                "/api/leads/" + lead.getId() + "/status", HttpMethod.PATCH,
                authed(token, new LeadController.UpdateStatusRequest(Lead.Status.QUALIFIED)), Lead.class);
        assertThat(statusUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusUpdate.getBody().getStatus()).isEqualTo(Lead.Status.QUALIFIED);

        ResponseEntity<LeadActivity> activityResponse = restTemplate.exchange(
                "/api/leads/" + lead.getId() + "/activities", HttpMethod.POST,
                authed(token, new LeadController.AddActivityRequest("Домовились на перегляд у суботу")),
                LeadActivity.class);
        assertThat(activityResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<LeadActivity[]> activities = restTemplate.exchange(
                "/api/leads/" + lead.getId() + "/activities", HttpMethod.GET, authed(token), LeadActivity[].class);
        assertThat(List.of(activities.getBody())).hasSize(1);
        assertThat(activities.getBody()[0].getNote()).isEqualTo("Домовились на перегляд у суботу");
    }
}
