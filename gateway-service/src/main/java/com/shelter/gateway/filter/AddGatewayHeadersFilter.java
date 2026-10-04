package com.shelter.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
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
import java.util.List;

@Component
public class AddGatewayHeadersFilter implements GlobalFilter, Ordered {

    /** Заголовки, которые выставляет только шлюз. Пришедшие от клиента удаляются. */
    private static final List<String> GATEWAY_HEADERS =
            List.of("X-User-Email", "X-User-Roles", "X-User-Id", "X-Gateway-Signature");

    @Value("${jwt.secret}")
    private String secret;

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest cleanRequest = exchange.getRequest().mutate()
                .headers(this::removeGatewayHeaders)
                .build();
        ServerWebExchange cleanExchange = exchange.mutate().request(cleanRequest).build();

        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> {
                    var principal = ctx.getAuthentication().getPrincipal();

                    if (principal instanceof Jwt jwt) {
                        String email = jwt.getSubject();
                        List<String> roleList = jwt.getClaimAsStringList("roles");
                        String roles = roleList != null ? String.join(",", roleList) : "";
                        Long userId = jwt.getClaim("userId") != null
                                ? ((Number) jwt.getClaim("userId")).longValue()
                                : null;
                        String userIdStr = userId != null ? String.valueOf(userId) : "";

                        String signature = computeSignature(userIdStr, roles, email, secret);

                        ServerHttpRequest mutatedRequest = cleanRequest.mutate()
                                .header("X-User-Email", email)
                                .header("X-User-Roles", roles)
                                .header("X-User-Id", userIdStr)
                                .header("X-Gateway-Signature", signature)
                                .build();

                        return chain.filter(cleanExchange.mutate().request(mutatedRequest).build());
                    }

                    return chain.filter(cleanExchange);
                })
                .switchIfEmpty(Mono.defer(() -> chain.filter(cleanExchange)));
    }

    private void removeGatewayHeaders(HttpHeaders headers) {
        GATEWAY_HEADERS.forEach(headers::remove);
    }

    /** Подпись по строке "userId:roles:email" — так же считает gateway-auth-starter. */
    private String computeSignature(String userId, String roles, String email, String secret) {
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
