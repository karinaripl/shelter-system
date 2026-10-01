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
        String path = request.getRequestURI();
        return properties.getInternalPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userEmail = request.getHeader("X-User-Email");
        String userRole = request.getHeader("X-User-Role");
        String userId = request.getHeader("X-User-Id");
        String signature = request.getHeader("X-Gateway-Signature");

        if (userEmail == null || userEmail.isBlank()
                || userRole == null || userRole.isBlank()
                || userId == null || userId.isBlank()
                || signature == null || signature.isBlank()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Access denied: request must go through gateway");
            return;
        }

        String expectedSignature = computeSignature(userEmail, userRole, userId, properties.getSecret());
        if (!expectedSignature.equals(signature)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Access denied: invalid gateway signature");
            return;
        }

        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + userRole));
        var authentication = new UsernamePasswordAuthenticationToken(userEmail, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        request.setAttribute("userId", Long.valueOf(userId));

        filterChain.doFilter(request, response);
    }

    private String computeSignature(String email, String role, String userId, String secret) {
        try {
            String data = email + ":" + role + ":" + userId;
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