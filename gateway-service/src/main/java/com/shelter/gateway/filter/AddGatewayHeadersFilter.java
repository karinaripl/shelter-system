package com.shelter.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class AddGatewayHeadersFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String secret;

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> {
                    var principal = ctx.getAuthentication().getPrincipal();

                    if (principal instanceof Jwt jwt) {
                        String email = jwt.getSubject();
                        String role = jwt.getClaimAsString("role");
                        Long userId = jwt.getClaim("userId") != null
                                ? ((Number) jwt.getClaim("userId")).longValue()
                                : null;
                        String userIdStr = userId != null ? String.valueOf(userId) : "";

                        String signature = computeSignature(email, role, userIdStr, secret);

                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("X-User-Email", email)
                                .header("X-User-Role", role != null ? role : "")
                                .header("X-User-Id", userIdStr)
                                .header("X-Gateway-Signature", signature)
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    }

                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
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