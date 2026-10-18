package com.shelter.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        ServerAuthenticationEntryPoint unauthorized = (exchange, e) ->
                writeError(exchange, HttpStatus.UNAUTHORIZED, "Требуется авторизация");
        ServerAccessDeniedHandler forbidden = (exchange, e) ->
                writeError(exchange, HttpStatus.FORBIDDEN, "Недостаточно прав для этого действия");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(auth -> auth
                        // Без токена: регистрация, вход и публичный каталог
                        .pathMatchers("/auth/register", "/auth/login", "/public/**").permitAll()
                        // Админ-панель (сотрудники, деньги) — только ADMIN; сервисы проверяют это ещё раз
                        .pathMatchers("/admin/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder())
                                .jwtAuthenticationConverter(rolesConverter()))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                )
                .build();
    }

    /** Роли из claim "roles" (["ADMIN", "VET"]) становятся ROLE_ADMIN, ROLE_VET. */
    private ReactiveJwtAuthenticationConverterAdapter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    /** Ошибка в общем формате сервисов: {timestamp, status, error, message, path}. */
    private static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"timestamp\":\"" + LocalDateTime.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + status.getReasonPhrase() + "\","
                + "\"message\":\"" + message + "\","
                + "\"path\":\"" + exchange.getRequest().getPath().value().replace("\"", "\\\"") + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
