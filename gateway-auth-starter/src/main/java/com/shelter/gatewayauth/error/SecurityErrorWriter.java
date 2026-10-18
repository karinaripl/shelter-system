package com.shelter.gatewayauth.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Ошибки безопасности (401, 403) в общем формате сервисов:
 * {"timestamp", "status", "error", "message", "path"}.
 */
public final class SecurityErrorWriter {

    public static final String UNAUTHORIZED_MESSAGE = "Требуется авторизация";
    public static final String FORBIDDEN_MESSAGE = "Недостаточно прав для этого действия";
    public static final String NOT_FROM_GATEWAY_MESSAGE = "Доступ запрещён: запрос должен приходить через шлюз";

    private SecurityErrorWriter() {
    }

    public static void write(HttpServletRequest request, HttpServletResponse response,
                             HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{"
                + "\"timestamp\":\"" + LocalDateTime.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + status.getReasonPhrase() + "\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"path\":\"" + escape(request.getRequestURI()) + "\""
                + "}");
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
