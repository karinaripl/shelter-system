package com.shelter.core.client;

import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.exception.ServiceUnavailableException;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ответы auth-service с ошибкой превращаются в ошибки этого сервиса с тем же сообщением:
 * 400 → 400, 404 → 404, 409 → 409 (например, «Пользователь с таким именем уже существует»), остальное → 503.
 * 401 — auth-service не принял внутренний токен (разные INTERNAL_API_TOKEN у сервисов): это ошибка настройки, тоже 503.
 */
public class AuthErrorDecoder implements ErrorDecoder {

    /** Поле message общего формата ошибок {timestamp, status, error, message, path}. */
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Override
    public Exception decode(String methodKey, Response response) {
        String message = message(response);
        return switch (response.status()) {
            case 400 -> new InvalidRequestException(message != null ? message : "Некорректные данные учётной записи");
            case 404 -> new NotFoundException(message != null ? message : "Учётная запись не найдена");
            case 409 -> new BusinessRuleException(message != null ? message : "Конфликт данных учётной записи");
            case 401 -> new ServiceUnavailableException("Сервис учётных записей не принял внутренний токен. "
                    + "Проверьте, что INTERNAL_API_TOKEN одинаковый у auth-service и shelter-core-service");
            default -> new ServiceUnavailableException(
                    "Сервис учётных записей ответил ошибкой (" + response.status() + "). Повторите позже");
        };
    }

    static String message(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream body = response.body().asInputStream()) {
            Matcher matcher = MESSAGE.matcher(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
        } catch (IOException e) {
            return null;
        }
    }
}
