package com.shelter.core.controller;

import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.VolunteerResponse;
import com.shelter.core.security.CurrentUser;
import com.shelter.core.service.AnimalPhotoService;
import com.shelter.core.service.AssignmentService;
import com.shelter.core.service.EventReportService;
import com.shelter.core.service.EventService;
import com.shelter.core.service.VolunteerService;
import com.shelter.core.service.AnimalService;
import com.shelter.core.service.CageService;
import com.shelter.core.service.DictionaryService;
import com.shelter.core.service.EmployeeService;
import com.shelter.core.service.MedicalCardService;
import com.shelter.core.service.MedicalRecordService;
import com.shelter.core.service.ProcedureService;
import com.shelter.core.service.ProcedureTypeService;
import com.shelter.core.service.ProductService;
import com.shelter.core.service.ReceiptService;
import com.shelter.core.service.StockOperationService;
import com.shelter.core.service.StockReportService;
import com.shelter.core.service.WarehouseReferenceService;
import com.shelter.core.service.PublicCatalogService;
import com.shelter.gatewayauth.config.GatewayAuthAutoConfiguration;
import com.shelter.gatewayauth.filter.GatewayAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Матрица доступа (docs/access.md) на уровне сервиса: каждый эндпоинт × каждая роль.
 * Разрешённая роль получает 2xx, остальные — 403, без заголовков шлюза — 401.
 * Работает настоящий gateway-auth-starter с подписанными заголовками, как при вызове через шлюз.
 */
@WebMvcTest
@ImportAutoConfiguration(GatewayAuthAutoConfiguration.class)
@Import(CurrentUser.class)
class AccessMatrixWebMvcTest {

    private static final List<String> ROLES =
            List.of("ADMIN", "VET", "CURATOR", "ADOPTION_MANAGER", "COORDINATOR", "VISITOR");
    private static final Set<String> STAFF = Set.of("ADMIN", "VET", "CURATOR", "ADOPTION_MANAGER", "COORDINATOR");
    private static final Set<String> ANIMALS_WRITE = Set.of("ADMIN", "CURATOR");
    private static final Set<String> PHOTOS_WRITE = Set.of("ADMIN", "CURATOR", "ADOPTION_MANAGER");
    private static final Set<String> MEDICAL = Set.of("ADMIN", "VET");
    private static final Set<String> PROCEDURE_TYPES_READ = Set.of("ADMIN", "VET", "CURATOR");
    private static final Set<String> WAREHOUSE_READ = Set.of("ADMIN", "VET", "CURATOR");
    private static final Set<String> WAREHOUSE_WRITE = Set.of("ADMIN", "CURATOR");
    private static final Set<String> RECEIPTS = Set.of("ADMIN", "CURATOR");
    private static final Set<String> VOLUNTEERS_WRITE = Set.of("ADMIN", "COORDINATOR");
    private static final Set<String> VOLUNTEERS_READ = Set.of("ADMIN", "COORDINATOR", "CURATOR");
    private static final Set<String> EVENTS_WRITE = Set.of("ADMIN", "ADOPTION_MANAGER", "COORDINATOR");
    private static final String VOLUNTEER_BODY = "{\"fullName\":\"Гусева Полина\"}";
    private static final String EVENT_BODY = "{\"name\":\"Выставка\",\"eventDate\":\"2026-10-19\"}";
    private static final String PRODUCT_BODY = "{\"name\":\"Корм\",\"categoryId\":1,\"unitOfMeasure\":\"кг\"}";

    /** Эндпоинт и роли, которым он разрешён. Тела запросов валидны: иначе запрещённая роль получила бы 400. */
    record Endpoint(HttpMethod method, String path, String body, boolean multipart, Set<String> allowed) {

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static List<Endpoint> protectedEndpoints() {
        return List.of(
                new Endpoint(HttpMethod.GET, "/animal-statuses", null, false, STAFF),
                new Endpoint(HttpMethod.GET, "/arrival-sources", null, false, STAFF),

                new Endpoint(HttpMethod.GET, "/cages", null, false, STAFF),
                new Endpoint(HttpMethod.GET, "/cages/1", null, false, STAFF),
                new Endpoint(HttpMethod.POST, "/cages", "{\"number\":\"Z-1\",\"capacity\":2}", false, ANIMALS_WRITE),
                new Endpoint(HttpMethod.PUT, "/cages/1", "{\"number\":\"Z-1\",\"capacity\":3}", false, ANIMALS_WRITE),
                new Endpoint(HttpMethod.DELETE, "/cages/1", null, false, ANIMALS_WRITE),

                new Endpoint(HttpMethod.GET, "/animals", null, false, STAFF),
                new Endpoint(HttpMethod.GET, "/animals/1", null, false, STAFF),
                new Endpoint(HttpMethod.POST, "/animals", "{\"arrivalSourceCode\":\"STREET\",\"cageId\":1}", false, ANIMALS_WRITE),
                new Endpoint(HttpMethod.PUT, "/animals/1", "{\"name\":\"Кот\"}", false, ANIMALS_WRITE),
                new Endpoint(HttpMethod.PUT, "/animals/1/cage", "{\"cageId\":2}", false, ANIMALS_WRITE),
                new Endpoint(HttpMethod.PUT, "/animals/1/status", "{\"status\":\"AVAILABLE\"}", false, ANIMALS_WRITE),
                new Endpoint(HttpMethod.PUT, "/animals/1/health-state", "{\"healthState\":\"TREATMENT\"}", false,
                        Set.of("ADMIN", "VET")),
                new Endpoint(HttpMethod.POST, "/animals/1/departure",
                        "{\"departureDate\":\"2026-01-01\",\"status\":\"DIED\"}", false, Set.of("ADMIN", "CURATOR", "VET")),

                new Endpoint(HttpMethod.GET, "/animals/1/photos", null, false, STAFF),
                new Endpoint(HttpMethod.GET, "/animals/1/photos/2/file", null, false, STAFF),
                new Endpoint(HttpMethod.POST, "/animals/1/photos", null, true, PHOTOS_WRITE),
                new Endpoint(HttpMethod.DELETE, "/animals/1/photos/2", null, false, PHOTOS_WRITE),
                new Endpoint(HttpMethod.PUT, "/animals/1/photos/2/main", null, false, PHOTOS_WRITE),

                // Медицина: полная медкарта — ADMIN и VET; сводка здоровья — все сотрудники
                new Endpoint(HttpMethod.GET, "/animals/1/medical-card", null, false, MEDICAL),
                new Endpoint(HttpMethod.PUT, "/animals/1/medical-card",
                        "{\"sterilized\":true,\"vaccinated\":true,\"parasiteTreated\":false}", false, MEDICAL),
                new Endpoint(HttpMethod.GET, "/animals/1/health-summary", null, false, STAFF),
                new Endpoint(HttpMethod.GET, "/animals/1/medical-records", null, false, MEDICAL),
                new Endpoint(HttpMethod.POST, "/animals/1/medical-records", "{\"weightKg\":3.4}", false, MEDICAL),
                new Endpoint(HttpMethod.GET, "/medical-records/5", null, false, MEDICAL),
                new Endpoint(HttpMethod.DELETE, "/medical-records/5", null, false, MEDICAL),
                new Endpoint(HttpMethod.GET, "/animals/1/procedures", null, false, MEDICAL),
                new Endpoint(HttpMethod.POST, "/animals/1/procedures", "{\"procedureTypeId\":2}", false, MEDICAL),
                new Endpoint(HttpMethod.GET, "/procedures/5", null, false, MEDICAL),
                new Endpoint(HttpMethod.DELETE, "/procedures/5", null, false, MEDICAL),

                // Виды процедур и нормы расхода: читают ADMIN, VET, CURATOR; изменяют ADMIN и VET
                new Endpoint(HttpMethod.GET, "/procedure-types", null, false, PROCEDURE_TYPES_READ),
                new Endpoint(HttpMethod.GET, "/procedure-types/2", null, false, PROCEDURE_TYPES_READ),
                new Endpoint(HttpMethod.GET, "/procedure-types/2/materials", null, false, PROCEDURE_TYPES_READ),
                new Endpoint(HttpMethod.POST, "/procedure-types", "{\"name\":\"Ревакцинация\"}", false, MEDICAL),
                new Endpoint(HttpMethod.PUT, "/procedure-types/2", "{\"name\":\"Вакцинация\"}", false, MEDICAL),
                new Endpoint(HttpMethod.PUT, "/procedure-types/2/rates",
                        "{\"rates\":[{\"productId\":9,\"quantity\":1}]}", false, MEDICAL),
                new Endpoint(HttpMethod.DELETE, "/procedure-types/2", null, false, MEDICAL),

                // Справочники склада и товары: читают ADMIN, CURATOR, VET; меняют ADMIN, CURATOR
                new Endpoint(HttpMethod.GET, "/product-categories", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/product-categories/1", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.POST, "/product-categories", "{\"name\":\"Игрушки\"}", false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.PUT, "/product-categories/1", "{\"name\":\"Игрушки\"}", false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.DELETE, "/product-categories/1", null, false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.GET, "/warehouses", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/warehouses/1", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.POST, "/warehouses", "{\"name\":\"Склад 4\"}", false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.PUT, "/warehouses/1", "{\"name\":\"Склад 4\"}", false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.DELETE, "/warehouses/1", null, false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.GET, "/suppliers", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/suppliers/1", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.POST, "/suppliers", "{\"name\":\"Поставщик\"}", false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.PUT, "/suppliers/1", "{\"name\":\"Поставщик\"}", false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.DELETE, "/suppliers/1", null, false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.GET, "/products", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/products/1", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/products/1/stock", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.POST, "/products", PRODUCT_BODY, false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.PUT, "/products/1", PRODUCT_BODY, false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.PUT, "/products/1/active", "{\"active\":false}", false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.DELETE, "/products/1", null, false, WAREHOUSE_WRITE),

                // Приход: поставки (без оплаты), натуральные пожертвования, благотворители — ADMIN, CURATOR
                new Endpoint(HttpMethod.GET, "/supplies", null, false, RECEIPTS),
                new Endpoint(HttpMethod.GET, "/supplies/1", null, false, RECEIPTS),
                new Endpoint(HttpMethod.POST, "/supplies",
                        "{\"supplierId\":1,\"warehouseId\":1,\"items\":[{\"productId\":1,\"quantity\":10}]}", false, RECEIPTS),
                new Endpoint(HttpMethod.DELETE, "/supplies/1", null, false, RECEIPTS),
                new Endpoint(HttpMethod.GET, "/goods-donations", null, false, RECEIPTS),
                new Endpoint(HttpMethod.GET, "/goods-donations/1", null, false, RECEIPTS),
                new Endpoint(HttpMethod.POST, "/goods-donations",
                        "{\"warehouseId\":1,\"items\":[{\"productId\":1,\"quantity\":3}]}", false, RECEIPTS),
                new Endpoint(HttpMethod.DELETE, "/goods-donations/1", null, false, RECEIPTS),
                new Endpoint(HttpMethod.GET, "/benefactors", null, false, RECEIPTS),

                // Движение по складу: читают ADMIN, CURATOR, VET; оформляют ADMIN, CURATOR
                new Endpoint(HttpMethod.GET, "/expenses", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/expenses/1", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.POST, "/expenses", "{\"productId\":1,\"warehouseId\":1,\"quantity\":2}", false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.DELETE, "/expenses/1", null, false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.GET, "/stock-transfers", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.POST, "/stock-transfers",
                        "{\"batchId\":1,\"fromWarehouseId\":1,\"toWarehouseId\":2,\"quantity\":1}", false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.GET, "/write-offs", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/write-offs/1", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.POST, "/write-offs",
                        "{\"batchId\":1,\"warehouseId\":1,\"quantity\":1,\"reason\":\"DAMAGED\"}", false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.GET, "/write-offs/expired/preview", null, false, WAREHOUSE_WRITE),
                new Endpoint(HttpMethod.POST, "/write-offs/expired", "{\"token\":\"abc\"}", false, WAREHOUSE_WRITE),

                // Отчёты склада: ADMIN, CURATOR, VET
                new Endpoint(HttpMethod.GET, "/reports/stock", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/reports/expiring", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/reports/expired", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/reports/below-minimum", null, false, WAREHOUSE_READ),
                new Endpoint(HttpMethod.GET, "/reports/movements?productId=1", null, false, WAREHOUSE_READ),

                // Волонтёры: анкета — ADMIN, COORDINATOR; имя и контакты — ещё CURATOR
                new Endpoint(HttpMethod.GET, "/volunteers", null, false, VOLUNTEERS_READ),
                new Endpoint(HttpMethod.GET, "/volunteers/5", null, false, VOLUNTEERS_READ),
                new Endpoint(HttpMethod.POST, "/volunteers", VOLUNTEER_BODY, false, VOLUNTEERS_WRITE),
                new Endpoint(HttpMethod.PUT, "/volunteers/5", VOLUNTEER_BODY, false, VOLUNTEERS_WRITE),
                new Endpoint(HttpMethod.PUT, "/volunteers/5/active", "{\"active\":false}", false, VOLUNTEERS_WRITE),
                new Endpoint(HttpMethod.DELETE, "/volunteers/5", null, false, VOLUNTEERS_WRITE),

                // Закрепления: ADMIN, COORDINATOR (RW), CURATOR (R)
                new Endpoint(HttpMethod.GET, "/volunteers/5/assignments", null, false, VOLUNTEERS_READ),
                new Endpoint(HttpMethod.GET, "/animals/1/volunteers", null, false, VOLUNTEERS_READ),
                new Endpoint(HttpMethod.GET, "/volunteer-assignments/50", null, false, VOLUNTEERS_READ),
                new Endpoint(HttpMethod.POST, "/volunteer-assignments", "{\"volunteerId\":5,\"animalId\":1}", false,
                        VOLUNTEERS_WRITE),
                new Endpoint(HttpMethod.PUT, "/volunteer-assignments/50/close", "{}", false, VOLUNTEERS_WRITE),
                new Endpoint(HttpMethod.DELETE, "/volunteer-assignments/50", null, false, VOLUNTEERS_WRITE),

                // Мероприятия и участие: читают все сотрудники; меняют ADMIN, ADOPTION_MANAGER, COORDINATOR
                new Endpoint(HttpMethod.GET, "/events", null, false, STAFF),
                new Endpoint(HttpMethod.GET, "/events/70", null, false, STAFF),
                new Endpoint(HttpMethod.GET, "/animals/1/events", null, false, STAFF),
                new Endpoint(HttpMethod.POST, "/events", EVENT_BODY, false, EVENTS_WRITE),
                new Endpoint(HttpMethod.PUT, "/events/70", EVENT_BODY, false, EVENTS_WRITE),
                new Endpoint(HttpMethod.DELETE, "/events/70", null, false, EVENTS_WRITE),
                new Endpoint(HttpMethod.POST, "/events/70/animals", "{\"animalId\":1}", false, EVENTS_WRITE),
                new Endpoint(HttpMethod.PUT, "/events/70/animals/1", "{\"conditionAfter\":\"Спокоен\"}", false,
                        EVENTS_WRITE),
                new Endpoint(HttpMethod.DELETE, "/events/70/animals/1", null, false, EVENTS_WRITE),

                // Отчёт об эффективности мероприятий: ADMIN, ADOPTION_MANAGER, COORDINATOR
                new Endpoint(HttpMethod.GET, "/reports/event-effectiveness", null, false, EVENTS_WRITE),

                new Endpoint(HttpMethod.GET, "/admin/employees", null, false, Set.of("ADMIN"))
        );
    }

    static List<Endpoint> publicEndpoints() {
        return List.of(
                new Endpoint(HttpMethod.GET, "/public/animals", null, false, Set.of()),
                new Endpoint(HttpMethod.GET, "/public/animals/1", null, false, Set.of()),
                new Endpoint(HttpMethod.GET, "/public/photos/2", null, false, Set.of()),
                new Endpoint(HttpMethod.GET, "/public/events", null, false, Set.of())
        );
    }

    static Stream<Arguments> endpointAndRole() {
        List<Arguments> cases = new ArrayList<>();
        for (Endpoint endpoint : protectedEndpoints()) {
            for (String role : ROLES) {
                cases.add(Arguments.of(endpoint, role));
            }
        }
        return cases.stream();
    }

    @Autowired
    private MockMvc mvc;

    @Value("${gateway.auth.secret}")
    private String secret;

    @MockitoBean
    private AnimalService animalService;
    @MockitoBean
    private CageService cageService;
    @MockitoBean
    private DictionaryService dictionaryService;
    @MockitoBean
    private AnimalPhotoService photoService;
    @MockitoBean
    private PublicCatalogService catalogService;
    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private MedicalCardService medicalCardService;
    @MockitoBean
    private MedicalRecordService medicalRecordService;
    @MockitoBean
    private ProcedureService procedureService;
    @MockitoBean
    private ProcedureTypeService procedureTypeService;
    @MockitoBean
    private WarehouseReferenceService warehouseReferenceService;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private ReceiptService receiptService;
    @MockitoBean
    private StockOperationService stockOperationService;
    @MockitoBean
    private StockReportService stockReportService;
    @MockitoBean
    private VolunteerService volunteerService;
    @MockitoBean
    private AssignmentService assignmentService;
    @MockitoBean
    private EventService eventService;
    @MockitoBean
    private EventReportService eventReportService;

    /** Полная анкета волонтёра — так её возвращает сервис; что показать роли, решает контроллер. */
    private static final VolunteerResponse FULL_VOLUNTEER = new VolunteerResponse(5L, "Гусева Полина", "89100000001",
            "guseva@test.local", "Москва", java.time.LocalDate.of(1990, 1, 1), java.time.LocalDate.of(2025, 1, 1),
            "Фотосъёмка", "1234", "567890", true);

    @BeforeEach
    void stubFiles() {
        var file = new AnimalPhotoService.PhotoFile(new byte[]{1}, MediaType.IMAGE_PNG);
        when(photoService.file(any(), any())).thenReturn(file);
        when(photoService.publicFile(any())).thenReturn(file);
        when(volunteerService.findById(5L)).thenReturn(FULL_VOLUNTEER);
        when(volunteerService.findPage(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(PageResponse.of(List.of(FULL_VOLUNTEER), 0, 20, 1));
    }

    // ---------- волонтёры: куратор видит только имя и контакты ----------

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/volunteers/5", "/volunteers"})
    void curatorSeesOnlyNameAndContacts(String path) throws Exception {
        String prefix = path.equals("/volunteers") ? "$.content[0]" : "$";
        mvc.perform(signed(MockMvcRequestBuilders.get(path), "CURATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(prefix + ".fullName").value("Гусева Полина"))
                .andExpect(jsonPath(prefix + ".phone").value("89100000001"))
                .andExpect(jsonPath(prefix + ".email").value("guseva@test.local"))
                .andExpect(jsonPath(prefix + ".passportSeries").doesNotExist())
                .andExpect(jsonPath(prefix + ".passportNumber").doesNotExist())
                .andExpect(jsonPath(prefix + ".skills").doesNotExist())
                .andExpect(jsonPath(prefix + ".address").doesNotExist())
                .andExpect(jsonPath(prefix + ".birthDate").doesNotExist())
                .andExpect(jsonPath(prefix + ".registrationDate").doesNotExist())
                .andExpect(jsonPath(prefix + ".active").doesNotExist());
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "COORDINATOR", "CURATOR,COORDINATOR"})
    void coordinatorAndAdminSeeFullProfileWithPassport(String roles) throws Exception {
        mvc.perform(signed(MockMvcRequestBuilders.get("/volunteers/5"), roles))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passportSeries").value("1234"))
                .andExpect(jsonPath("$.passportNumber").value("567890"))
                .andExpect(jsonPath("$.skills").value("Фотосъёмка"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void curatorCannotSearchBySkills() throws Exception {
        mvc.perform(signed(MockMvcRequestBuilders.get("/volunteers").param("skills", "фото"), "CURATOR"))
                .andExpect(status().isForbidden());
        mvc.perform(signed(MockMvcRequestBuilders.get("/volunteers").param("skills", "фото"), "COORDINATOR"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidVolunteerBodies")
    void invalidVolunteerDataIsBadRequest(String body) throws Exception {
        mvc.perform(signed(MockMvcRequestBuilders.post("/volunteers")
                        .contentType(MediaType.APPLICATION_JSON).content(body), "COORDINATOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    static Stream<String> invalidVolunteerBodies() {
        return Stream.of(
                "{\"fullName\":\" \"}",
                "{\"fullName\":\"Гусева\",\"phone\":\"12345\"}",
                "{\"fullName\":\"Гусева\",\"phone\":\"+79991234567\",\"email\":\"не-почта\"}",
                "{\"fullName\":\"Гусева\",\"passportSeries\":\"123\",\"passportNumber\":\"567890\"}",
                "{\"fullName\":\"Гусева\",\"passportSeries\":\"1234\",\"passportNumber\":\"56789A\"}"
        );
    }

    @Test
    void eventWithoutDateIsBadRequest() throws Exception {
        mvc.perform(signed(MockMvcRequestBuilders.post("/events")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Выставка\"}"), "ADOPTION_MANAGER"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "{0} — {1}")
    @MethodSource("endpointAndRole")
    void roleGetsAccessOnlyIfAllowed(Endpoint endpoint, String role) throws Exception {
        int status = mvc.perform(signed(request(endpoint), role)).andReturn().getResponse().getStatus();

        if (endpoint.allowed().contains(role)) {
            assertThat(status).as("%s для %s", endpoint, role).isBetween(200, 299);
        } else {
            assertThat(status).as("%s для %s", endpoint, role).isEqualTo(403);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("protectedEndpoints")
    void withoutGatewayHeadersIsUnauthorized(Endpoint endpoint) throws Exception {
        mvc.perform(request(endpoint))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Требуется авторизация"))
                .andExpect(jsonPath("$.path").value(endpoint.path().split("\\?")[0]));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publicEndpoints")
    void publicCatalogIsOpenForEveryone(Endpoint endpoint) throws Exception {
        mvc.perform(request(endpoint)).andExpect(status().isOk());
        for (String role : ROLES) {
            mvc.perform(signed(request(endpoint), role)).andExpect(status().isOk());
        }
    }

    @Test
    void adminPrefixIsClosedForEveryoneButAdminEvenWithoutHandler() throws Exception {
        for (String role : ROLES) {
            int status = mvc.perform(signed(MockMvcRequestBuilders.get("/admin/finance/balance"), role))
                    .andReturn().getResponse().getStatus();
            // ADMIN проходит правило /admin/** и получает 404 (эндпоинта пока нет), остальные — 403
            assertThat(status).as(role).isEqualTo(role.equals("ADMIN") ? 404 : 403);
        }
    }

    @Test
    void forbiddenResponseUsesCommonErrorFormat() throws Exception {
        mvc.perform(signed(MockMvcRequestBuilders.get("/admin/employees"), "CURATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Недостаточно прав для этого действия"))
                .andExpect(jsonPath("$.path").value("/admin/employees"));
    }

    @Test
    void anyOfSeveralRolesIsEnough() throws Exception {
        mvc.perform(signed(MockMvcRequestBuilders.post("/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivalSourceCode\":\"STREET\",\"cageId\":1}"), "VET,CURATOR"))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("invalidMedicalBodies")
    void invalidMedicalDataIsBadRequest(String path, String body) throws Exception {
        mvc.perform(signed(MockMvcRequestBuilders.request(path.endsWith("rates") ? HttpMethod.PUT : HttpMethod.POST, path)
                        .contentType(MediaType.APPLICATION_JSON).content(body), "VET"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    static Stream<Arguments> invalidMedicalBodies() {
        return Stream.of(
                Arguments.of("/animals/1/medical-records", "{\"weightKg\":0}"),
                Arguments.of("/animals/1/medical-records", "{\"weightKg\":-1.5}"),
                Arguments.of("/animals/1/medical-records", "{\"weightKg\":1000}"),
                Arguments.of("/animals/1/procedures", "{}"),
                Arguments.of("/animals/1/procedures",
                        "{\"procedureTypeId\":2,\"materials\":[{\"productId\":9,\"batchId\":1,\"warehouseId\":2,\"quantity\":0}]}"),
                Arguments.of("/animals/1/procedures",
                        "{\"procedureTypeId\":2,\"materials\":[{\"productId\":9,\"warehouseId\":2}]}"),
                Arguments.of("/procedure-types", "{\"name\":\" \"}"),
                Arguments.of("/procedure-types", "{\"name\":\"Тест\",\"cardFlag\":\"NO_SUCH_FLAG\"}"),
                Arguments.of("/procedure-types/2/rates", "{\"rates\":[{\"productId\":9,\"quantity\":0}]}"),
                Arguments.of("/procedure-types/2/rates", "{}")
        );
    }

    /** Цены и сумма поставки, которые сервис не заполнил (не ADMIN), отсутствуют в JSON, а не приходят как null. */
    @Test
    void supplyWithoutMoneyHasNoPriceFieldsInJson() throws Exception {
        var item = new com.shelter.core.dto.ReceiptItemResponse(5L, 1L, "Корм", "кг", java.math.BigDecimal.TEN, null, null);
        when(receiptService.findSupply(1L)).thenReturn(new com.shelter.core.dto.SupplyResponse(1L, null, null, null,
                false, null, null, List.of(item), null, null));

        mvc.perform(signed(MockMvcRequestBuilders.get("/supplies/1"), "CURATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").doesNotExist())
                .andExpect(jsonPath("$.items[0].unitPrice").doesNotExist())
                .andExpect(jsonPath("$.items[0].quantity").value(10));
    }

    @Test
    void supplyWithMoneyHasPriceFieldsInJson() throws Exception {
        var item = new com.shelter.core.dto.ReceiptItemResponse(5L, 1L, "Корм", "кг", java.math.BigDecimal.TEN, null,
                new java.math.BigDecimal("12.50"));
        when(receiptService.findSupply(1L)).thenReturn(new com.shelter.core.dto.SupplyResponse(1L, null, null, null,
                false, null, null, List.of(item), new java.math.BigDecimal("125.00"), null));

        mvc.perform(signed(MockMvcRequestBuilders.get("/supplies/1"), "ADMIN"))
                .andExpect(jsonPath("$.totalAmount").value(125.00))
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.50));
    }

    @Test
    void forgedRolesAreRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/admin/employees")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "vet@test")
                        .header("X-User-Roles", "ADMIN")
                        .header("X-Gateway-Signature", GatewayAuthFilter.computeSignature("1", "VET", "vet@test", secret)))
                .andExpect(status().isForbidden());
    }

    private AbstractMockHttpServletRequestBuilder<?> request(Endpoint endpoint) {
        if (endpoint.multipart()) {
            return MockMvcRequestBuilders.multipart(endpoint.path())
                    .file(new MockMultipartFile("file", "cat.png", "image/png", new byte[]{1, 2, 3}));
        }
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(endpoint.method(), endpoint.path());
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        return builder;
    }

    /** Заголовки, которые выставляет шлюз после проверки JWT. */
    private AbstractMockHttpServletRequestBuilder<?> signed(AbstractMockHttpServletRequestBuilder<?> builder, String roles) {
        String email = roles.toLowerCase().replace(',', '-') + "@test.local";
        return builder
                .header("X-User-Id", "1")
                .header("X-User-Email", email)
                .header("X-User-Roles", roles)
                .header("X-Gateway-Signature", GatewayAuthFilter.computeSignature("1", roles, email, secret));
    }
}
