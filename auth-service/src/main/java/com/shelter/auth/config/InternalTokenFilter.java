package com.shelter.auth.config;

import com.shelter.gatewayauth.error.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /internal/** — вызовы других сервисов (шлюз эти пути не маршрутизирует). gateway-auth-starter пропускает их
 * без заголовков шлюза, поэтому здесь требуется общий секрет: заголовок X-Internal-Token со значением
 * INTERNAL_API_TOKEN. Нет заголовка или значение не совпадает — 401. Без INTERNAL_API_TOKEN сервис не запускается.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    static final String MESSAGE = "Требуется внутренний токен сервиса (X-Internal-Token)";

    private final byte[] token;

    public InternalTokenFilter(@Value("${internal-api.token}") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Не задан INTERNAL_API_TOKEN — внутренний API /internal/** не защищён");
        }
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals("/internal") || path.startsWith("/internal/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        // Сравнение за постоянное время: по времени ответа нельзя подобрать токен
        if (presented == null || !MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8))) {
            SecurityErrorWriter.write(request, response, HttpStatus.UNAUTHORIZED, MESSAGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
