package com.shelter.core.controller;

import com.shelter.core.service.AnimalPhotoService;
import com.shelter.core.service.AnimalService;
import com.shelter.core.service.CageService;
import com.shelter.core.service.DictionaryService;
import com.shelter.core.service.EmployeeService;
import com.shelter.core.service.MedicalCardService;
import com.shelter.core.service.MedicalRecordService;
import com.shelter.core.service.ProcedureService;
import com.shelter.core.service.ProcedureTypeService;
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
class AccessMatrixWebMvcTest {

    private static final List<String> ROLES =
            List.of("ADMIN", "VET", "CURATOR", "ADOPTION_MANAGER", "COORDINATOR", "VISITOR");
    private static final Set<String> STAFF = Set.of("ADMIN", "VET", "CURATOR", "ADOPTION_MANAGER", "COORDINATOR");
    private static final Set<String> ANIMALS_WRITE = Set.of("ADMIN", "CURATOR");
    private static final Set<String> PHOTOS_WRITE = Set.of("ADMIN", "CURATOR", "ADOPTION_MANAGER");
    private static final Set<String> MEDICAL = Set.of("ADMIN", "VET");
    private static final Set<String> PROCEDURE_TYPES_READ = Set.of("ADMIN", "VET", "CURATOR");

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


                new Endpoint(HttpMethod.GET, "/admin/employees", null, false, Set.of("ADMIN"))
        );
    }

    static List<Endpoint> publicEndpoints() {
        return List.of(
                new Endpoint(HttpMethod.GET, "/public/animals", null, false, Set.of()),
                new Endpoint(HttpMethod.GET, "/public/animals/1", null, false, Set.of()),
                new Endpoint(HttpMethod.GET, "/public/photos/2", null, false, Set.of())
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

    @BeforeEach
    void stubFiles() {
        var file = new AnimalPhotoService.PhotoFile(new byte[]{1}, MediaType.IMAGE_PNG);
        when(photoService.file(any(), any())).thenReturn(file);
        when(photoService.publicFile(any())).thenReturn(file);
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
                .andExpect(jsonPath("$.path").value(endpoint.path()));
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
