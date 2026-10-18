package com.shelter.gatewayauth.filter;

import com.shelter.gatewayauth.config.GatewayAuthProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthFilterTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789";

    private GatewayAuthFilter filter;

    @BeforeEach
    void setUp() {
        GatewayAuthProperties properties = new GatewayAuthProperties();
        properties.setSecret(SECRET);
        properties.setPublicPaths(List.of("/auth/login"));
        filter = new GatewayAuthFilter(properties);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyRoleFromHeaderBecomesAuthority() throws Exception {
        MockHttpServletRequest request = signedRequest("/rooms", "42", "VET,ADMIN", "doc@shelter.local");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("запрос пропущен дальше").isNotNull();
        assertThat(authorities()).containsExactlyInAnyOrder("ROLE_VET", "ROLE_ADMIN");
        assertThat(request.getAttribute("userId")).isEqualTo(42L);
    }

    @Test
    void forgedRolesAreRejected() throws Exception {
        MockHttpServletRequest request = signedRequest("/rooms", "42", "VISITOR", "guest@shelter.local");
        // Клиент дописал себе роль, но подпись считалась по исходной строке
        request.removeHeader("X-User-Roles");
        request.addHeader("X-User-Roles", "VISITOR,ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void requestWithoutGatewayHeadersIsUnauthorized() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/rooms"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":401", "\"message\":\"Требуется авторизация\"", "\"path\":\"/rooms\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void partialGatewayHeadersAreForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rooms");
        request.addHeader("X-User-Email", "guest@shelter.local");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void publicPathIsAvailableWithoutHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("POST", "/auth/login"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void internalPathIsNotFiltered() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/internal/rooms/1/exists"),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private static MockHttpServletRequest signedRequest(String path, String userId, String roles, String email) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("X-User-Id", userId);
        request.addHeader("X-User-Roles", roles);
        request.addHeader("X-User-Email", email);
        request.addHeader("X-Gateway-Signature",
                GatewayAuthFilter.computeSignature(userId, roles, email, SECRET));
        return request;
    }

    private static List<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
