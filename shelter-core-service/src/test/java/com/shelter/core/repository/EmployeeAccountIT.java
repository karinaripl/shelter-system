package com.shelter.core.repository;

import com.github.tomakehurst.wiremock.http.Fault;
import com.shelter.core.dto.AccountRequest;
import com.shelter.core.dto.EmployeeCreateRequest;
import com.shelter.core.dto.EmployeeRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.ServiceUnavailableException;
import com.shelter.core.service.EmployeeAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * Создание сотрудника с учётной записью при настоящем Feign-клиенте и замоканном (WireMock) auth-service:
 * при ошибке на любой стороне не остаётся ни карточки без учётной записи, ни учётной записи без карточки.
 */
class EmployeeAccountIT extends CoreApplicationIT {

    private static final UUID KEY = UUID.fromString("0b5c1d2e-3f40-4a5b-8c6d-7e8f90a1b2c3");
    private static final String USER_JSON = """
            {"id":42,"username":"novikova","email":"novikova@test.local","roles":["VET"],"enabled":true}""";

    @Autowired
    private EmployeeAdminService service;

    @MockitoSpyBean
    private EmployeeRepository employeeRepository;

    private Long position;

    @BeforeEach
    void setUp() {
        reset(employeeRepository);
        position = position("Ветеринар IT", "60000.00");
    }

    private EmployeeCreateRequest request() {
        return new EmployeeCreateRequest(
                new EmployeeRequest("Новикова Анна", position, LocalDate.now().minusDays(1), 40, null, null, null, null,
                        "4510", "123456"),
                new AccountRequest("novikova", "novikova@test.local", "password1", Set.of("VET")), null);
    }

    private static void authCreates(int status) {
        AUTH.stubFor(post(urlEqualTo("/internal/users")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(USER_JSON)));
    }

    @Test
    void employeeAndAccountAreCreatedTogether() {
        authCreates(201);

        var result = service.create(request(), KEY);

        assertThat(result.created()).isTrue();
        assertThat(dsl.fetchOne(EMPLOYEE).getUserId()).isEqualTo(42L);
        AUTH.verify(postRequestedFor(urlEqualTo("/internal/users"))
                .withHeader("X-Internal-Token", equalTo("test-only-internal-token"))
                .withRequestBody(matchingJsonPath("$.requestId", equalTo(KEY.toString())))
                .withRequestBody(matchingJsonPath("$.username", equalTo("novikova"))));
        AUTH.verify(0, deleteRequestedFor(urlPathEqualTo("/internal/users/42")));
    }

    @Test
    void repeatedRequestWithSameKeyCreatesNoDuplicate() {
        authCreates(201);
        service.create(request(), KEY);
        // auth-service на повтор с тем же ключом возвращает ту же запись (200)
        authCreates(200);

        var repeat = service.create(request(), KEY);

        assertThat(repeat.created()).isFalse();
        assertThat(dsl.fetchCount(EMPLOYEE)).isEqualTo(1);
        // Повтор распознан по ключу в карточке — до проверки паспорта и без второго вызова auth-service
        AUTH.verify(1, postRequestedFor(urlEqualTo("/internal/users")));
    }

    @Test
    void cardFailureRemovesCreatedAccount() {
        authCreates(201);
        AUTH.stubFor(delete(urlPathEqualTo("/internal/users/42")).willReturn(aResponse().withStatus(204)));
        doThrow(new DataIntegrityViolationException("сбой записи карточки")).when(employeeRepository).insert(any());

        assertThatThrownBy(() -> service.create(request(), KEY)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(dsl.fetchCount(EMPLOYEE)).isZero();
        AUTH.verify(deleteRequestedFor(urlPathEqualTo("/internal/users/42"))
                .withQueryParam("requestId", equalTo(KEY.toString())));
    }

    @Test
    void retryAfterFailedCompensationPicksUpLeftoverAccount() {
        authCreates(201);
        AUTH.stubFor(delete(urlPathEqualTo("/internal/users/42")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        doThrow(new DataIntegrityViolationException("сбой записи карточки")).when(employeeRepository).insert(any());
        assertThatThrownBy(() -> service.create(request(), KEY)).isInstanceOf(DataIntegrityViolationException.class);

        // Учётная запись осталась в auth-service; повтор с тем же ключом получает её и создаёт карточку
        reset(employeeRepository);
        authCreates(200);
        var retry = service.create(request(), KEY);

        assertThat(retry.created()).isTrue();
        assertThat(dsl.fetchOne(EMPLOYEE).getUserId()).isEqualTo(42L);
    }

    @Test
    void takenUsernameIsConflictAndNoCardIsCreated() {
        AUTH.stubFor(post(urlEqualTo("/internal/users")).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":409,\"message\":\"Пользователь с таким именем уже существует\"}")));

        assertThatThrownBy(() -> service.create(request(), KEY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Пользователь с таким именем уже существует");
        assertThat(dsl.fetchCount(EMPLOYEE)).isZero();
    }

    @Test
    void authServiceDownIsServiceUnavailableAndNoCardIsCreated() {
        AUTH.stubFor(post(urlEqualTo("/internal/users")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> service.create(request(), KEY))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageContaining("недоступен");

        AUTH.stubFor(post(urlEqualTo("/internal/users")).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> service.create(request(), KEY)).isInstanceOf(ServiceUnavailableException.class);
        // Разные INTERNAL_API_TOKEN у сервисов: auth-service отвечает 401 — это ошибка настройки, а не данных
        AUTH.stubFor(post(urlEqualTo("/internal/users")).willReturn(aResponse().withStatus(401)));
        assertThatThrownBy(() -> service.create(request(), KEY))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageContaining("INTERNAL_API_TOKEN");
        assertThat(dsl.fetchCount(EMPLOYEE)).isZero();
    }

    @Test
    void terminationBlocksAccountAndCancellationUnblocks() {
        authCreates(201);
        Long id = service.create(request(), KEY).employee().id();
        AUTH.stubFor(put(urlEqualTo("/internal/users/42/status")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(USER_JSON)));

        service.terminate(id, LocalDate.now());
        AUTH.verify(putRequestedFor(urlEqualTo("/internal/users/42/status")).withRequestBody(equalToJson("{\"enabled\":false}")));
        assertThat(dsl.fetchOne(EMPLOYEE).getTerminationDate()).isEqualTo(LocalDate.now());

        service.cancelTermination(id);
        AUTH.verify(putRequestedFor(urlEqualTo("/internal/users/42/status")).withRequestBody(equalToJson("{\"enabled\":true}")));
        assertThat(dsl.fetchOne(EMPLOYEE).getTerminationDate()).isNull();
    }

    @Test
    void terminationIsNotSavedWhenAccountCannotBeBlocked() {
        authCreates(201);
        Long id = service.create(request(), KEY).employee().id();
        AUTH.stubFor(put(urlEqualTo("/internal/users/42/status")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> service.terminate(id, LocalDate.now())).isInstanceOf(ServiceUnavailableException.class);
        assertThat(dsl.fetchOne(EMPLOYEE).getTerminationDate()).isNull();
    }
}
