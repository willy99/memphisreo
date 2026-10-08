package com.memphisreo.platform.demo;

import com.memphisreo.common.TenantContext;
import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientForm;
import com.memphisreo.crm.ClientService;
import com.memphisreo.crm.RequirementForm;
import com.memphisreo.media.MediaService;
import com.memphisreo.media.PropertyMedia;
import com.memphisreo.platform.agent.AgentInvitationService;
import com.memphisreo.platform.agent.InviteAgentRequest;
import com.memphisreo.platform.agent.InviteAgentResponse;
import com.memphisreo.platform.property.PropertyEditorDtos.Price;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyPayload;
import com.memphisreo.platform.property.PropertyEditorService;
import com.memphisreo.platform.registration.RegisterTenantRequest;
import com.memphisreo.platform.registration.RegisterTenantResponse;
import com.memphisreo.platform.registration.TenantRegistrationService;
import com.memphisreo.platform.sale.SaleDtos.AgentsRequest;
import com.memphisreo.platform.sale.SaleService;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.SaleForm;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.property.Address;
import com.memphisreo.property.Property;
import com.memphisreo.property.Property.Condition;
import com.memphisreo.property.Property.Feature;
import com.memphisreo.property.Property.Heating;
import com.memphisreo.property.Property.Market;
import com.memphisreo.property.Property.Type;
import com.memphisreo.property.Property.WallMaterial;
import com.memphisreo.property.PropertyForm;
import com.memphisreo.property.PropertyForm.AddressForm;
import com.memphisreo.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Демо-агенція "Одеса Рієлт": 3 агенти, 10 об'єктів з реальними одеськими
 * адресами й фото, 6 клієнтів. Лише для розробки/демо
 * ({@code memphisreo.demo-seed.enabled}); пропускається, якщо агенція вже є.
 * Фото — Unsplash (вільна ліцензія); без мережі — згенеровані заглушки.
 */
@Component
@ConditionalOnProperty(name = "memphisreo.demo-seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String SLUG = "odesa-realt";
    static final String ADMIN_EMAIL = "ihor@odesa-realt.test";
    static final String PASSWORD = "Demo1234!";

    private final TenantRepository tenantRepository;
    private final TenantRegistrationService registrationService;
    private final AgentInvitationService invitationService;
    private final PropertyEditorService editorService;
    private final MediaService mediaService;
    private final ClientService clientService;
    private final SaleService saleService;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public DemoDataSeeder(TenantRepository tenantRepository, TenantRegistrationService registrationService,
                          AgentInvitationService invitationService, PropertyEditorService editorService,
                          MediaService mediaService, ClientService clientService, SaleService saleService) {
        this.tenantRepository = tenantRepository;
        this.registrationService = registrationService;
        this.invitationService = invitationService;
        this.editorService = editorService;
        this.mediaService = mediaService;
        this.clientService = clientService;
        this.saleService = saleService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenantRepository.existsBySlug(SLUG)) {
            log.info("Демо-агенція {} вже є — пропускаю", SLUG);
            return;
        }
        log.info("Створюю демо-агенцію \"Одеса Рієлт\"…");
        RegisterTenantResponse agency = registrationService.register(new RegisterTenantRequest(
                "Одеса Рієлт", SLUG, "UA", ADMIN_EMAIL, PASSWORD, "Ігор", "Ткаченко"));
        UUID tenantId = agency.tenantId();

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        tenant.setPublicPhone("+380 48 700 12 34");
        tenant.setPublicEmail("hello@odesa-realt.test");
        tenant.setPublicCity("Одеса");
        tenant.setAbout("Агенція нерухомості в Одесі з 2012 року: квартири в центрі та Аркадії, будинки на Фонтані, "
                + "комерційні приміщення. Супровід угоди від першого показу до реєстрації права власності.");
        tenantRepository.save(tenant);

        TenantContext.runAs(tenantId, () -> {
            UUID olena = invite(tenantId, "olena@odesa-realt.test", "Олена", "Шевчук");
            UUID maksym = invite(tenantId, "maksym@odesa-realt.test", "Максим", "Руденко");
            UUID[] agents = {agency.adminAgentId(), olena, maksym};

            List<Client> clients = new java.util.ArrayList<>();
            for (ClientForm client : clients()) {
                clients.add(clientService.create(tenantId, client));
            }
            Client seller = clients.get(3); // Сергій Литвиненко — власник квартири на Люстдорфській
            clientService.saveRequirement(tenantId, clients.get(0).getId(), new RequirementForm("APARTMENT", 2, 2, null,
                    new BigDecimal("150000"), "USD", null, List.of("Аркадія", "Малий Фонтан"), "NEW_BUILD", List.of(), null, true));
            clientService.saveRequirement(tenantId, clients.get(1).getId(), new RequirementForm("APARTMENT", 1, 1, null,
                    new BigDecimal("80000"), "USD", null, List.of(), "NEW_BUILD", List.of(), "Під оренду, готівка", true));
            clientService.saveRequirement(tenantId, clients.get(2).getId(), new RequirementForm("APARTMENT", 3, 4, null,
                    new BigDecimal("200000"), "USD", null, List.of("Центр", "Відрада"), null, List.of("SHELTER"), null, true));
            clientService.saveRequirement(tenantId, clients.get(4).getId(), new RequirementForm("HOUSE", null, null, null,
                    new BigDecimal("350000"), "USD", null, List.of("Чубаївка", "Фонтанка"), null, List.of("GARAGE"), null, true));
            clientService.saveRequirement(tenantId, clients.get(5).getId(), new RequirementForm("COMMERCIAL", null, null, null,
                    null, "USD", new BigDecimal("60"), List.of("Центр"), null, List.of("SEPARATE_ENTRANCE"), null, true));

            List<Seed> seeds = seeds();
            for (int i = 0; i < seeds.size(); i++) {
                Seed seed = seeds.get(i);
                UUID agentId = agents[i % agents.length];
                PropertyDetails created = editorService.create(tenantId, agentId, seed.payload());
                for (int p = 0; p < seed.photos().size(); p++) {
                    Photo photo = seed.photos().get(p);
                    byte[] bytes = fetch(photo.unsplashId(), photo.caption());
                    var view = mediaService.uploadImage(tenantId, created.id(), agentId, PropertyMedia.Kind.PHOTO,
                            photo.caption() + ".jpg", new ByteArrayInputStream(bytes), bytes.length);
                    mediaService.updateCaption(created.id(), view.id(), photo.caption());
                }
                editorService.complete(tenantId, agentId, created.id());

                // Продаж: мандат, доступ, власник (для одного об'єкта), другий агент на частині об'єктів.
                boolean isLiustdorf = seed.payload().property().title().contains("Люстдорфській");
                boolean hideAddress = seed.payload().property().type() == Type.HOUSE;
                saleService.update(tenantId, agentId, created.id(), new SaleForm(null, null,
                        isLiustdorf ? seller.getId() : null,
                        i % 3 == 0 ? Listing.MandateType.EXCLUSIVE : Listing.MandateType.NON_EXCLUSIVE,
                        java.time.LocalDate.now().plusMonths(i % 2 == 0 ? 6 : 3),
                        new BigDecimal(i % 3 == 0 ? "3" : "4"), null,
                        isLiustdorf ? "Ключі в офісі, власник у Польщі — показ без узгодження" : "Домовлятися з власником за день",
                        hideAddress));
                if (i % 2 == 1) {
                    saleService.setAgents(tenantId, agentId, created.id(),
                            new AgentsRequest(agentId, List.of(agents[(i + 1) % agents.length])));
                }
                // Один об'єкт лишаємо чернеткою продажу — для демонстрації "Виставити на продаж".
                if (i != seeds.size() - 1) {
                    saleService.activate(tenantId, agentId, created.id());
                }
                log.info("  об'єкт {}/{}: {}", i + 1, seeds.size(), seed.payload().property().title());
            }
        });
        log.info("Демо-агенцію створено: {} / {}", ADMIN_EMAIL, PASSWORD);
    }

    private UUID invite(UUID tenantId, String email, String firstName, String lastName) {
        InviteAgentResponse invite = invitationService.invite(tenantId, new InviteAgentRequest(email, firstName, lastName, null));
        invitationService.acceptInvite(invite.inviteToken(), PASSWORD);
        return invite.agentId();
    }

    // ---------- Дані ----------

    private record Photo(String unsplashId, String caption) {
    }

    private record Seed(PropertyPayload payload, List<Photo> photos) {
    }

    private static List<Seed> seeds() {
        return List.of(
                new Seed(apartment("3-кімнатна на Дерибасівській з видом на Міський сад",
                        """
                        Простора трикімнатна квартира у самому серці Одеси — будинок кінця XIX століття \
                        з високими стелями 3,6 м, ліпниною та збереженими дерев'яними дверима. Якісний \
                        ремонт 2021 року: нова електрика, мідна сантехніка, двоконтурний котел. Вікна \
                        виходять у тихий двір і на Міський сад. Поруч Оперний театр, Пасаж, кав'ярні \
                        Дерибасівської. Ідеально як для життя, так і під подобову оренду.""",
                        addr("Дерибасівська вулиця", "10", "Центр", "65026", 46.4843353, 30.7417811),
                        Market.SECONDARY, "98", "64", "14", 3, 2, 3, 4, 1897, "3.6",
                        WallMaterial.BRICK, Condition.EURO, Heating.INDIVIDUAL_GAS, false, 0, "185000",
                        Feature.BALCONY, Feature.FURNISHED, Feature.APPLIANCES, Feature.AIR_CONDITIONING, Feature.SHELTER),
                        List.of(new Photo("1600607687939-ce8a6c25118c", "Вітальня"), new Photo("1556912172-45b7abe8b7e1", "Кухня"),
                                new Photo("1600210492486-724fe5c67fb0", "Спальня"), new Photo("1600573472550-8090b5e0745e", "Санвузол"))),

                new Seed(apartment("2-кімнатна у ЖК на Французькому бульварі",
                        """
                        Двокімнатна квартира в сучасному комплексі бізнес-класу на Французькому бульварі. \
                        Закрита територія, консьєрж, підземний паркінг (місце продається окремо). \
                        Дизайнерський ремонт 2023 року, кухня-вітальня з панорамним вікном, спальня з \
                        гардеробною. У будинку генератор і укриття. До моря — 10 хвилин пішки.""",
                        addr("Французький бульвар", "22", "Малий Фонтан", "65058", 46.456325, 30.7588799),
                        Market.NEW_BUILD, "68", "40", "18", 2, 1, 7, 16, 2021, "3.0",
                        WallMaterial.MONOLITH_FRAME, Condition.DESIGNER, Heating.INDIVIDUAL_GAS, true, 1, "145000",
                        Feature.TERRACE, Feature.FURNISHED, Feature.APPLIANCES, Feature.AIR_CONDITIONING,
                        Feature.UNDERGROUND_PARKING, Feature.CONCIERGE, Feature.CLOSED_AREA, Feature.BACKUP_POWER, Feature.SHELTER),
                        List.of(new Photo("1600566753190-17f0baa2a6c3", "Кухня-вітальня"), new Photo("1540518614846-7eded433c457", "Спальня"),
                                new Photo("1600121848594-d8644e57abab", "Тераса"))),

                new Seed(apartment("4-кімнатна «сталінка» на Пушкінській",
                        """
                        Велика чотирикімнатна квартира у монументальному будинку на Пушкінській. \
                        Два санвузли, окрема кухня 16 м², кабінет, два балкони. Стан хороший, жилий — \
                        потребує косметичного оновлення. Підходить великій родині або під офіс \
                        (можливе переведення в нежитловий фонд). Паркування у дворі.""",
                        addr("Пушкінська вулиця", "32", "Центр", "65011", 46.4778309, 30.7421478),
                        Market.SECONDARY, "140", "96", "16", 4, 2, 2, 3, 1953, "3.2",
                        WallMaterial.BRICK, Condition.GOOD, Heating.CENTRAL, false, 1, "240000",
                        Feature.BALCONY, Feature.STORAGE_ROOM, Feature.CLOSED_AREA),
                        List.of(new Photo("1502672260266-1c1ef2d93688", "Вітальня"), new Photo("1484154218962-a197022b5858", "Кухня"),
                                new Photo("1505693416388-ac5ce068fe85", "Спальня"))),

                new Seed(apartment("1-кімнатна під чистову в Аркадії",
                        """
                        Однокімнатна квартира в новому будинку на Генуезькій, стан «під чистову»: \
                        стяжка, штукатурка, розведена електрика, встановлені вікна й вхідні двері. \
                        12-й поверх, вид на море. Будинок здано у 2024 році, документи готові. \
                        Гарний варіант для інвестиції під оренду в Аркадії.""",
                        addr("Генуезька вулиця", "24", "Аркадія", "65009", 46.4374358, 30.7570391),
                        Market.NEW_BUILD, "45", "20", "12", 1, 1, 12, 24, 2024, "2.8",
                        WallMaterial.MONOLITH, Condition.WHITE_BOX, Heating.INDIVIDUAL_ELECTRIC, true, 0, "72000",
                        Feature.BALCONY, Feature.CLOSED_AREA, Feature.VIDEO_SURVEILLANCE, Feature.PLAYGROUND),
                        List.of(new Photo("1522708323590-d24dbb6b0267", "Фасад будинку"), new Photo("1560448204-e02f11c3d0e2", "Планування-зразок"))),

                new Seed(apartment("3-кімнатна на Маразліївській біля парку Шевченка",
                        """
                        Трикімнатна квартира в цегляному будинку 1970-х на Маразліївській — тиха \
                        вулиця між центром і парком Шевченка. Роздільні кімнати, два балкони, кухня \
                        12 м². Зроблено якісний ремонт 2019 року, залишаються вбудовані меблі та \
                        техніка. Під вікнами — Маразліївський сквер, до моря 15 хвилин.""",
                        addr("Маразліївська вулиця", "1", "Відрада", "65014", 46.4632739, 30.7473852),
                        Market.SECONDARY, "110", "72", "12", 3, 1, 4, 5, 1974, "2.9",
                        WallMaterial.BRICK, Condition.EURO, Heating.CENTRAL, false, 0, "210000",
                        Feature.BALCONY, Feature.LOGGIA, Feature.FURNISHED, Feature.APPLIANCES, Feature.AIR_CONDITIONING),
                        List.of(new Photo("1598928506311-c55ded91a20c", "Вітальня"), new Photo("1600047509807-ba8f99d2cdde", "Кухня"),
                                new Photo("1486406146926-c627a92ad1ab", "Вид з балкона"))),

                new Seed(apartment("2-кімнатна на Люстдорфській дорозі",
                        """
                        Двокімнатна квартира в панельному будинку на Люстдорфській дорозі, 5 поверх \
                        із 9, ліфт працює. Косметичний ремонт, металопластикові вікна, засклений \
                        балкон. Поруч зупинка трамвая, супермаркет, школа. Продаж від власника, \
                        один власник, без обтяжень — швидкий вихід на угоду.""",
                        addr("Люстдорфська дорога", "55", "Київський", "65080", 46.4204551, 30.7260548),
                        Market.SECONDARY, "54", "32", "8", 2, 1, 5, 9, 1982, "2.6",
                        WallMaterial.PANEL, Condition.COSMETIC, Heating.CENTRAL, true, 0, "49000",
                        Feature.BALCONY),
                        List.of(new Photo("1600047509807-ba8f99d2cdde", "Кімната"), new Photo("1568605114967-8130f3a36994", "Будинок"))),

                new Seed(apartment("2-кімнатна на Канатній з ремонтом",
                        """
                        Двокімнатна квартира на Канатній у будинку 2008 року. Кухня-студія з \
                        вітальнею, окрема спальня, гардероб. Автономне газове опалення — комунальні \
                        платежі помірні. Закритий двір, місце для авто. 6 поверх, ліфт.""",
                        addr("Канатна вулиця", "81", "Відрада", "65012", 46.4675946, 30.746625),
                        Market.SECONDARY, "61", "36", "15", 2, 1, 6, 10, 2008, "2.8",
                        WallMaterial.MONOLITH_BRICK, Condition.GOOD, Heating.INDIVIDUAL_GAS, true, 1, "78000",
                        Feature.LOGGIA, Feature.APPLIANCES, Feature.AIR_CONDITIONING, Feature.CLOSED_AREA),
                        List.of(new Photo("1600210492486-724fe5c67fb0", "Спальня"), new Photo("1556912172-45b7abe8b7e1", "Кухня-студія"))),

                new Seed(house("Будинок на Фонтанській дорозі з садом",
                        """
                        Двоповерховий будинок 220 м² на ділянці 8 соток у районі 7-ї станції \
                        Фонтану. Побудований у 2012 році з газоблоку, утеплений. На першому поверсі — \
                        кухня-вітальня, кабінет, гостьовий санвузол; на другому — три спальні та два \
                        санвузли. Гараж на два авто, літня кухня, доглянутий сад, автоматичний полив. \
                        Усі міські комунікації, генератор на 10 кВт. До моря — 7 хвилин пішки.""",
                        addr("Фонтанська дорога", "49", "Чубаївка", "65049", 46.432107, 30.7496412),
                        "220", "160", "22", "800", 5, 3, 3, 2, 2012, "3.0",
                        WallMaterial.AERATED_CONCRETE, Condition.GOOD, Heating.INDIVIDUAL_GAS, 2, "320000",
                        "5110137300:02:013:0045",
                        Feature.GARAGE, Feature.GARDEN, Feature.FENCE, Feature.SUMMER_KITCHEN, Feature.TERRACE,
                        Feature.GAS, Feature.ELECTRICITY, Feature.WATER_CENTRAL, Feature.SEWAGE_CENTRAL, Feature.BACKUP_POWER),
                        List.of(new Photo("1600596542815-ffad4c1539a9", "Фасад"), new Photo("1613490493576-7fde63acd811", "Двір і басейн"),
                                new Photo("1600566753190-17f0baa2a6c3", "Вітальня"), new Photo("1600607687939-ce8a6c25118c", "Кухня"))),

                new Seed(land("Ділянка 10 соток у Фонтанці під житлову забудову",
                        """
                        Рівна ділянка 10 соток у селі Фонтанка (Одеський район), цільове призначення — \
                        будівництво житлового будинку. Сусідні ділянки забудовані, вулиця з твердим \
                        покриттям. Електрика по межі, газ і вода — у 50 метрах. До моря 3 км, до Одеси \
                        20 хвилин. Є кадастровий номер і витяг з ДЗК.""",
                        new AddressForm("UA", "Одеська область", "Фонтанка", null, "Вулиця Степова", null, "67571", null,
                                46.570198, 30.858065, Address.GeocodeSource.MANUAL),
                        "1000", Property.LandPurpose.RESIDENTIAL, "5122783200:01:002:0123", "28000",
                        Feature.ELECTRICITY),
                        List.of(new Photo("1500382017468-9049fed747ef", "Ділянка"), new Photo("1500530855697-b586d89ba3ee", "Вид на вулицю"))),

                new Seed(commercial("Приміщення з вітриною на Рішельєвській",
                        """
                        Нежитлове приміщення 85 м² на першому поверсі будинку на Рішельєвській, за \
                        квартал від Дерибасівської. Окремий вхід з вулиці, вітринні вікна, висота стель \
                        3,4 м. Електрична потужність 15 кВт, виведена вентиляція — підходить під \
                        кав'ярню, шоурум або офіс. Зараз вільне, документи нежитлового фонду готові.""",
                        addr("Рішельєвська вулиця", "12", "Центр", "65012", 46.4820133, 30.740066),
                        Property.CommercialType.RETAIL, "85", 3, 1, 4, 1900, "3.4", Condition.GOOD, Heating.INDIVIDUAL_ELECTRIC,
                        "160000", Feature.SEPARATE_ENTRANCE, Feature.SHOP_WINDOW, Feature.AIR_CONDITIONING),
                        List.of(new Photo("1497366216548-37526070297c", "Основний зал"), new Photo("1497366754035-f200968a6e72", "Друга зона"),
                                new Photo("1524758631624-e2822e304c36", "Вхід")))
        );
    }

    private static List<ClientForm> clients() {
        return List.of(
                new ClientForm("Марина", "Коваленко", "maryna.kovalenko@example.test", "+380 67 211 45 90",
                        Client.Source.ADVERTISEMENT,
                        "Покупець. Шукає 2-кімнатну в Аркадії або на Французькому бульварі, бюджет до 120 000 USD. "
                                + "Планує іпотеку єОселя — потрібні новобудови з готовими документами. Зручно дивитися у вихідні."),
                new ClientForm("Дмитро", "Савченко", "d.savchenko@example.test", "+380 50 332 18 07",
                        Client.Source.REFERRAL,
                        "Інвестор, рекомендація від Олени. Купує 1-кімнатні в новобудовах під оренду, до 80 000 USD, готівка. "
                                + "Хоче 2–3 об'єкти до кінця року."),
                new ClientForm("Ірина", "Бондаренко", "iryna.bondarenko@example.test", "+380 63 480 22 15",
                        Client.Source.WEBSITE_INQUIRY,
                        "Сім'я з двома дітьми, переїжджають з Миколаєва. Шукають 3-кімнатну в центрі або на Відраді до 200 000 USD, "
                                + "обов'язково укриття поруч. Чоловік Олег — дзвонити після 18:00."),
                new ClientForm("Сергій", "Литвиненко", null, "+380 97 605 11 38",
                        Client.Source.WALK_IN,
                        "Продавець. Власник 2-кімнатної на Люстдорфській дорозі, 55. Хоче продати до весни, "
                                + "готовий до торгу в межах 2–3 тис. Ключі в офісі."),
                new ClientForm("Наталія", "Приходько", "n.prykhodko@example.test", "+380 66 219 73 44",
                        Client.Source.REFERRAL,
                        "Шукає будинок або ділянку в районі Фонтанки / Совіньйону до 350 000 USD. "
                                + "Важливі власне подвір'я і гараж. Дивилася будинок на Фонтанській дорозі, думає."),
                new ClientForm("Андрій", "Мельник", "andrii.melnyk@example.test", "+380 93 118 90 26",
                        Client.Source.OTHER,
                        "Підприємець, відкриває кав'ярню. Потрібне приміщення 60–100 м² з окремим входом у центрі, "
                                + "розглядає купівлю або довгострокову оренду.")
        );
    }

    // ---------- Конструктори payload ----------

    private static AddressForm addr(String street, String house, String district, String postcode, double lat, double lon) {
        return new AddressForm("UA", "Одеська область", "Одеса", district, street, house, postcode, null, lat, lon,
                Address.GeocodeSource.AUTOCOMPLETE);
    }

    private static PropertyPayload apartment(String title, String description, AddressForm address, Market market,
                                             String area, String living, String kitchen, int rooms, int bathrooms,
                                             int floor, int totalFloors, int yearBuilt, String ceiling,
                                             WallMaterial wall, Condition condition, Heating heating, boolean elevator,
                                             int parking, String price, Feature... features) {
        PropertyForm form = new PropertyForm(Type.APARTMENT, market, title, description.strip(), dec(area), dec(living),
                dec(kitchen), null, rooms, null, bathrooms, floor, totalFloors, yearBuilt, dec(ceiling), wall, condition,
                heating, null, null, null, elevator, parking, List.of(features), null, address);
        return new PropertyPayload(form, new Price(dec(price), "USD"));
    }

    private static PropertyPayload house(String title, String description, AddressForm address, String area,
                                         String living, String kitchen, String landSqm, int rooms, int bedrooms,
                                         int bathrooms, int totalFloors, int yearBuilt, String ceiling,
                                         WallMaterial wall, Condition condition, Heating heating, int parking,
                                         String price, String cadastral, Feature... features) {
        PropertyForm form = new PropertyForm(Type.HOUSE, Market.SECONDARY, title, description.strip(), dec(area),
                dec(living), dec(kitchen), dec(landSqm), rooms, bedrooms, bathrooms, null, totalFloors, yearBuilt,
                dec(ceiling), wall, condition, heating, null, null, cadastral, null, parking, List.of(features), null,
                address);
        return new PropertyPayload(form, new Price(dec(price), "USD"));
    }

    private static PropertyPayload land(String title, String description, AddressForm address, String landSqm,
                                        Property.LandPurpose purpose, String cadastral, String price, Feature... features) {
        PropertyForm form = new PropertyForm(Type.LAND, null, title, description.strip(), null, null, null,
                dec(landSqm), null, null, null, null, null, null, null, null, null, null, purpose, null, cadastral,
                null, null, List.of(features), null, address);
        return new PropertyPayload(form, new Price(dec(price), "USD"));
    }

    private static PropertyPayload commercial(String title, String description, AddressForm address,
                                              Property.CommercialType type, String area, int rooms, int floor,
                                              int totalFloors, int yearBuilt, String ceiling, Condition condition,
                                              Heating heating, String price, Feature... features) {
        PropertyForm form = new PropertyForm(Type.COMMERCIAL, null, title, description.strip(), dec(area), null, null,
                null, rooms, null, null, floor, totalFloors, yearBuilt, dec(ceiling), null, condition, heating, null,
                type, null, false, 0, List.of(features), null, address);
        return new PropertyPayload(form, new Price(dec(price), "USD"));
    }

    private static BigDecimal dec(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    // ---------- Фото ----------

    private byte[] fetch(String unsplashId, String caption) {
        String url = "https://images.unsplash.com/photo-" + unsplashId + "?auto=format&fit=crop&w=1600&q=80";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Memphis/0.1 (dev demo seed)")
                    .GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200 && response.body().length > 10_000) {
                return response.body();
            }
            log.warn("Unsplash {} → {}; використовую заглушку", unsplashId, response.statusCode());
        } catch (IOException e) {
            log.warn("Фото {} недоступне ({}); використовую заглушку", unsplashId, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return placeholder(caption);
    }

    /** Заглушка без мережі: градієнт з підписом. */
    static byte[] placeholder(String caption) {
        int w = 1600, h = 1067;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        int hue = Math.abs(caption.hashCode()) % 360;
        g.setPaint(new GradientPaint(0, 0, Color.getHSBColor(hue / 360f, 0.35f, 0.85f),
                w, h, Color.getHSBColor(((hue + 40) % 360) / 360f, 0.45f, 0.55f)));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(28, 27, 24));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 96));
        g.drawString(caption, 120, h / 2 + 30);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
