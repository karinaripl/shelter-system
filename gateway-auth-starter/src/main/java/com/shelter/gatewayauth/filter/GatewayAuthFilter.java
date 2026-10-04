package com.shelter.gatewayauth.filter;

import com.shelter.gatewayauth.config.GatewayAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class GatewayAuthFilter extends OncePerRequestFilter {

    private final GatewayAuthProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayAuthFilter(GatewayAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return matchesAny(properties.getInternalPaths(), request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userEmail = request.getHeader("X-User-Email");
        String userRoles = request.getHeader("X-User-Roles");
        String userId = request.getHeader("X-User-Id");
        String signature = request.getHeader("X-Gateway-Signature");

        boolean noUserHeaders = isBlank(userEmail) && isBlank(userRoles)
                && isBlank(userId) && isBlank(signature);
        if (noUserHeaders && matchesAny(properties.getPublicPaths(), request.getRequestURI())) {
            // Анонимный запрос к публичному пути (регистрация, вход)
            filterChain.doFilter(request, response);
            return;
        }

        if (isBlank(userEmail) || isBlank(userRoles) || isBlank(userId) || isBlank(signature)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Access denied: request must go through gateway");
            return;
        }

        String expectedSignature = computeSignature(userId, userRoles, userEmail, properties.getSecret());
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Access denied: invalid gateway signature");
            return;
        }

        // Каждая роль из списка "ADMIN,VET" превращается в ROLE_ADMIN, ROLE_VET
        List<SimpleGrantedAuthority> authorities = Arrays.stream(userRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(userEmail, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        request.setAttribute("userId", Long.valueOf(userId));

        filterChain.doFilter(request, response);
    }

    private boolean matchesAny(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Подпись считается по строке "userId:roles:email" — той же, что и в шлюзе.
     * Email стоит последним: только он может содержать двоеточие.
     */
    static String computeSignature(String userId, String roles, String email, String secret) {
        try {
            String data = userId + ":" + roles + ":" + email;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute signature", e);
        }
    }
}
