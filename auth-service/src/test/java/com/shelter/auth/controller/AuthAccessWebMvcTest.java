package com.shelter.auth.controller;

import com.shelter.auth.service.AuthService;
import com.shelter.auth.service.UserService;
import com.shelter.gatewayauth.config.GatewayAuthAutoConfiguration;
import com.shelter.gatewayauth.filter.GatewayAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Учётные записи живут под /admin/** — только ADMIN; вход и регистрация — без заголовков шлюза. */
@WebMvcTest
@ImportAutoConfiguration(GatewayAuthAutoConfiguration.class)
class AuthAccessWebMvcTest {

    private static final List<String> NOT_ADMIN = List.of("VET", "CURATOR", "ADOPTION_MANAGER", "COORDINATOR", "VISITOR");
    /** Значение INTERNAL_API_TOKEN из тестового application.yaml. */
    private static final String INTERNAL_TOKEN = "test-only-internal-token";

    @Autowired
    private MockMvc mvc;

    @Value("${gateway.auth.secret}")
    private String secret;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private UserService userService;

    @Test
    void usersAndRolesAreAdminOnly() throws Exception {
        for (String path : List.of("/admin/users", "/admin/users/1", "/admin/roles")) {
            mvc.perform(signed(get(path), "ADMIN")).andExpect(status().isOk());
            for (String role : NOT_ADMIN) {
                assertThat(mvc.perform(signed(get(path), role)).andReturn().getResponse().getStatus())
                        .as(path + " для " + role).isEqualTo(403);
            }
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    /** /internal/** — вызовы shelter-core-service: без заголовков шлюза, но с X-Internal-Token. */
    @Test
    void internalUsersApiNeedsNoGatewayHeadersButNeedsInternalToken() throws Exception {
        org.mockito.Mockito.when(userService.createInternal(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.shelter.auth.dto.InternalUserResult(null, true));
        mvc.perform(internal(post("/internal/users")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"requestId":"11111111-2222-3333-4444-555555555555","username":"vet","email":"vet@test.local",
                         "password":"password1","roles":["VET"]}"""))
                .andExpect(status().isCreated());
        mvc.perform(internal(get("/internal/users/5"))).andExpect(status().isOk());
        mvc.perform(internal(put("/internal/users/5/status"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(internal(delete("/internal/users/5")).param("requestId", "11111111-2222-3333-4444-555555555555"))
                .andExpect(status().isNoContent());
        // Без ключа запроса создать нельзя
        mvc.perform(internal(post("/internal/users")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"vet","email":"vet@test.local","password":"password1","roles":["VET"]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalApiWithoutOrWithWrongTokenIsUnauthorized() throws Exception {
        String body = """
                {"requestId":"11111111-2222-3333-4444-555555555555","username":"vet","email":"vet@test.local",
                 "password":"password1","roles":["VET"]}""";
        List<MockHttpServletRequestBuilder> requests = List.of(
                post("/internal/users").contentType(MediaType.APPLICATION_JSON).content(body),
                get("/internal/users/5"),
                put("/internal/users/5/status").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"),
                delete("/internal/users/5").param("requestId", "11111111-2222-3333-4444-555555555555"));
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Требуется внутренний токен сервиса (X-Internal-Token)"));
        }
        mvc.perform(get("/internal/users/5").header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/5").header("X-Internal-Token", ""))
                .andExpect(status().isUnauthorized());
        // Подписанные заголовки шлюза, даже администратора, внутренний токен не заменяют
        mvc.perform(signed(get("/internal/users/5"), "ADMIN")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(userService);
    }

    @Test
    void coordinatorCannotCreateUser() throws Exception {
        mvc.perform(signed(post("/admin/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"x","email":"x@test.local","password":"password1","roles":["VET"]}"""), "COORDINATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Недостаточно прав для этого действия"))
                .andExpect(jsonPath("$.path").value("/admin/users"));
    }

    @Test
    void oldUsersPathNoLongerExists() throws Exception {
        mvc.perform(signed(get("/auth/users"), "ADMIN")).andExpect(status().isNotFound());
    }

    @Test
    void loginAndRegistrationArePublic() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@test.local\",\"password\":\"password1\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"email\":\"a@test.local\",\"password\":\"password1\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void profileNeedsAuthenticationAndErrorsHavePath() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/auth/me"));
        mvc.perform(signed(get("/auth/me"), "VISITOR")).andExpect(status().isOk());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/auth/login"));
    }

    private static MockHttpServletRequestBuilder internal(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Internal-Token", INTERNAL_TOKEN);
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder builder, String roles) {
        String email = roles.toLowerCase() + "@test.local";
        return builder
                .header("X-User-Id", "1")
                .header("X-User-Email", email)
                .header("X-User-Roles", roles)
                .header("X-Gateway-Signature", GatewayAuthFilter.computeSignature("1", roles, email, secret));
    }
}
