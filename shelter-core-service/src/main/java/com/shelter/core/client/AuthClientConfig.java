package com.shelter.core.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/** Настройки только клиента auth-service (не @Configuration: не попадает в общий контекст). */
public class AuthClientConfig {

    /** Заголовок, без которого auth-service отвечает на /internal/** 401. */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Bean
    public ErrorDecoder authErrorDecoder() {
        return new AuthErrorDecoder();
    }

    @Bean
    public RequestInterceptor internalTokenInterceptor(@Value("${auth-service.internal-token}") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Не задан INTERNAL_API_TOKEN — auth-service не примет вызовы /internal/**");
        }
        return template -> template.header(INTERNAL_TOKEN_HEADER, token);
    }
}
