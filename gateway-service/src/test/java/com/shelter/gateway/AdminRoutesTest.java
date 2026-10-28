package com.shelter.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Админ-панель через шлюз: новые пути /admin/** ведут в shelter-core-service и пропускаются только с ролью ADMIN;
 * /internal/** шлюз не маршрутизирует (вызовы только между сервисами).
 */
@SpringBootTest
class AdminRoutesTest {

    private static final List<String> NOT_ADMIN = List.of("VET", "CURATOR", "ADOPTION_MANAGER", "COORDINATOR", "VISITOR");

    @Autowired
    private RouteLocator routes;

    @Autowired
    private ApplicationContext context;

    @Value("${jwt.secret}")
    private String secret;

    private Optional<String> routeOf(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        return Flux.from(routes.getRoutes())
                .filterWhen(route -> Mono.from(route.getPredicate().apply(exchange)))
                .next()
                .map(Route::getId)
                .blockOptional();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/employees/1/termination", "/admin/positions", "/admin/benefactors/3",
            "/admin/donations", "/admin/finance/transactions", "/admin/finance/balance", "/admin/supplies/unpaid",
            "/admin/salaries/preview", "/admin/reports/finance/categories", "/admin/reports/donations/benefactors"})
    void newAdminPathsLeadToShelterCore(String path) {
        assertThat(routeOf(path)).contains("shelter-core-service");
    }

    @Test
    void accountsStayInAuthServiceAndInternalPathsHaveNoRoute() {
        assertThat(routeOf("/admin/users/5/status")).contains("auth-service");
        assertThat(routeOf("/internal/users")).isEmpty();
        assertThat(routeOf("/internal/users/42/status")).isEmpty();
    }

    @Test
    void financeIsForbiddenForEveryRoleButAdminAndUnauthorizedWithoutToken() {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
        for (String role : NOT_ADMIN) {
            client.get().uri("/admin/finance/balance").header("Authorization", "Bearer " + token(role))
                    .exchange().expectStatus().isForbidden();
            client.post().uri("/admin/salaries").header("Authorization", "Bearer " + token(role))
                    .exchange().expectStatus().isForbidden();
        }
        client.get().uri("/admin/finance/balance").exchange().expectStatus().isUnauthorized();
        // С ролью ADMIN шлюз пропускает запрос дальше (сервис в тесте не запущен — ошибка уже не 401/403)
        int status = client.get().uri("/admin/finance/balance").header("Authorization", "Bearer " + token("ADMIN"))
                .exchange().returnResult(String.class).getStatus().value();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void internalUsersAreNotReachableThroughGateway() {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
        client.post().uri("/internal/users").header("Authorization", "Bearer " + token("ADMIN"))
                .exchange().expectStatus().isNotFound();
    }

    private String token(String role) {
        return Jwts.builder()
                .subject(role.toLowerCase() + "@test.local")
                .claim("roles", List.of(role))
                .claim("userId", 1)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
