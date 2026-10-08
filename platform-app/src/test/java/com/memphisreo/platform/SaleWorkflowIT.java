package com.memphisreo.platform;

import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientForm;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.SaleForm;
import com.memphisreo.platform.api.AuthController;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyPayload;
import com.memphisreo.platform.sale.SaleDtos.AgentView;
import com.memphisreo.platform.sale.SaleDtos.AgentsRequest;
import com.memphisreo.platform.sale.SaleDtos.SaleView;
import com.memphisreo.platform.sale.SaleDtos.SoldRequest;
import com.memphisreo.platform.sale.SaleDtos.TimelineEntry;
import com.memphisreo.platform.sale.SaleDtos.WithdrawRequest;
import com.memphisreo.platform.agent.InviteAgentRequest;
import com.memphisreo.platform.agent.InviteAgentResponse;
import com.memphisreo.platform.agent.AcceptInviteRequest;
import com.memphisreo.property.PropertyAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Вкладка "Продаж": стейт-машина, власник/мандат, кілька агентів, таймлайн. docs/sales-workflow.md §2. */
class SaleWorkflowIT extends AbstractIntegrationTest {

    private String token;
    private UUID agentId;
    private PropertyDetails property;

    @BeforeEach
    void agencyWithDraft() {
        String slug = "sale-" + UUID.randomUUID().toString().substring(0, 8);
        agentId = register(slug, "admin@" + slug + ".ua", "Password123!", "UA").adminAgentId();
        token = login("admin@" + slug + ".ua", "Password123!");
        property = createProperty(token);
    }

    @Test
    void draftSale_existsRightAfterPropertyCreation_withAuthorAsLeadAgent() {
        SaleView sale = sale();
        assertThat(sale.status()).isEqualTo(Listing.Status.DRAFT);
        assertThat(sale.price()).isEqualByComparingTo("95000");
        assertThat(sale.agents()).extracting(AgentView::id, AgentView::role).containsExactly(tuple(agentId, PropertyAgent.Role.LEAD));
        assertThat(sale.blockers()).containsExactly("propertyDraft");
        assertThat(sale.publicUrl()).isNull();
    }

    @Test
    void activate_requiresCompletedPropertyAndPrice_thenExposesPublicUrl() {
        ResponseEntity<String> tooEarly = restTemplate.exchange(path("/sale/activate"), HttpMethod.POST, authed(token), String.class);
        assertThat(tooEarly.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooEarly.getBody()).contains("propertyDraft");

        SaleView active = listProperty(token, property.id());
        assertThat(active.status()).isEqualTo(Listing.Status.ACTIVE);
        assertThat(active.publishedAt()).isNotNull();
        assertThat(active.publicUrl()).contains("/p/").contains(property.id().toString());
    }

    @Test
    void priceWithoutValue_blocksActivation() {
        PropertyDetails noPrice = createProperty(token, new PropertyPayload(apartmentPayload().property(), null));
        restTemplate.exchange("/api/properties/" + noPrice.id() + "/complete", HttpMethod.POST, authed(token), String.class);
        // complete потребує ціни — тож ставимо ціну, а потім перевіряємо блокер окремо через sale view
        SaleView sale = restTemplate.exchange("/api/properties/" + noPrice.id() + "/sale", HttpMethod.GET, authed(token), SaleView.class).getBody();
        assertThat(sale.blockers()).contains("price");
    }

    @Test
    void sellerMandateAccess_areSaved_andSellerSetIsOnTimeline() {
        Client seller = restTemplate.exchange("/api/clients", HttpMethod.POST,
                authed(token, new ClientForm("Сергій", "Литвиненко", null, "+380970000000", Client.Source.WALK_IN, null)),
                Client.class).getBody();
        SaleForm form = new SaleForm(new BigDecimal("99000"), "USD", seller.getId(), Listing.MandateType.EXCLUSIVE,
                LocalDate.now().plusMonths(6), new BigDecimal("3"), null, "Ключі в офісі", true);

        SaleView saved = restTemplate.exchange(path("/sale"), HttpMethod.PUT, authed(token, form), SaleView.class).getBody();

        assertThat(saved.seller().id()).isEqualTo(seller.getId());
        assertThat(saved.mandateType()).isEqualTo(Listing.MandateType.EXCLUSIVE);
        assertThat(saved.commissionPercent()).isEqualByComparingTo("3");
        assertThat(saved.hideExactAddress()).isTrue();
        assertThat(saved.price()).isEqualByComparingTo("99000");
        assertThat(saved.priceHistory()).hasSize(2);
        assertThat(timeline()).extracting(TimelineEntry::type).contains("PROPERTY_CREATED", "PRICE_SET", "PRICE_CHANGED", "SELLER_SET");
    }

    @Test
    void expiredMandate_blocksActivation() {
        SaleForm expired = new SaleForm(null, null, null, Listing.MandateType.EXCLUSIVE, LocalDate.now().minusDays(1),
                null, null, null, false);
        restTemplate.exchange(path("/sale"), HttpMethod.PUT, authed(token, expired), SaleView.class);
        restTemplate.exchange(path("/complete"), HttpMethod.POST, authed(token), String.class);

        ResponseEntity<String> response = restTemplate.exchange(path("/sale/activate"), HttpMethod.POST, authed(token), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("mandateExpired");
    }

    @Test
    void withdrawAndRelist_thenSold_followStateMachine() {
        listProperty(token, property.id());

        SaleView withdrawn = restTemplate.exchange(path("/sale/withdraw"), HttpMethod.POST,
                authed(token, new WithdrawRequest("Власник передумав")), SaleView.class).getBody();
        assertThat(withdrawn.status()).isEqualTo(Listing.Status.WITHDRAWN);
        assertThat(withdrawn.withdrawnReason()).isEqualTo("Власник передумав");
        assertThat(withdrawn.publicUrl()).isNull();

        // Повторно зняти вже зняте — неможливий перехід.
        assertThat(restTemplate.exchange(path("/sale/withdraw"), HttpMethod.POST, authed(token, new WithdrawRequest(null)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        SaleView relisted = restTemplate.exchange(path("/sale/activate"), HttpMethod.POST, authed(token), SaleView.class).getBody();
        assertThat(relisted.status()).isEqualTo(Listing.Status.ACTIVE);

        SaleView sold = restTemplate.exchange(path("/sale/sold"), HttpMethod.POST,
                authed(token, new SoldRequest(new BigDecimal("93000"))), SaleView.class).getBody();
        assertThat(sold.status()).isEqualTo(Listing.Status.SOLD);
        assertThat(sold.price()).isEqualByComparingTo("93000");
        assertThat(sold.closedAt()).isNotNull();

        assertThat(timeline()).extracting(TimelineEntry::type)
                .containsSubsequence("SOLD", "RELISTED", "WITHDRAWN", "LISTED", "PROPERTY_COMPLETED", "PROPERTY_CREATED");

        // Після продажу вкладка показує закритий продаж; змінити його вже не можна.
        assertThat(sale().status()).isEqualTo(Listing.Status.SOLD);
        assertThat(restTemplate.exchange(path("/sale/withdraw"), HttpMethod.POST, authed(token, new WithdrawRequest(null)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void severalAgents_oneLead_changesAreInternalTimelineOnly() {
        InviteAgentResponse invite = restTemplate.exchange("/api/agents/invite", HttpMethod.POST,
                authed(token, new InviteAgentRequest("second@" + UUID.randomUUID() + ".ua", "Олена", "Шевчук", null)),
                InviteAgentResponse.class).getBody();
        restTemplate.postForEntity("/api/auth/accept-invite", new AcceptInviteRequest(invite.inviteToken(), "Password123!"), Void.class);

        List<AgentView> agents = List.of(restTemplate.exchange(path("/agents"), HttpMethod.PUT,
                authed(token, new AgentsRequest(invite.agentId(), List.of(agentId))), AgentView[].class).getBody());
        assertThat(agents).extracting(AgentView::id, AgentView::role)
                .containsExactly(tuple(invite.agentId(), PropertyAgent.Role.LEAD), tuple(agentId, PropertyAgent.Role.CO_AGENT));

        TimelineEntry change = timeline().stream().filter(e -> e.type().equals("AGENTS_CHANGED")).findFirst().orElseThrow();
        assertThat(change.ownerVisible()).isFalse();

        ResponseEntity<String> unknown = restTemplate.exchange(path("/agents"), HttpMethod.PUT,
                authed(token, new AgentsRequest(UUID.randomUUID(), List.of())), String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String path(String suffix) {
        return "/api/properties/" + property.id() + suffix;
    }

    private SaleView sale() {
        return restTemplate.exchange(path("/sale"), HttpMethod.GET, authed(token), SaleView.class).getBody();
    }

    private List<TimelineEntry> timeline() {
        return List.of(restTemplate.exchange(path("/timeline"), HttpMethod.GET, authed(token), TimelineEntry[].class).getBody());
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
