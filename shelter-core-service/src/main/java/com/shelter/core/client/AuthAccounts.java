package com.shelter.core.client;

import com.shelter.core.exception.ServiceUnavailableException;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Учётные записи сотрудников в auth-service. Сетевые сбои (сервис не запущен, таймаут) — 503
 * «Сервис учётных записей недоступен»; ответы с ошибкой разбирает {@link AuthErrorDecoder}. Повторов нет.
 */
@Component
public class AuthAccounts {

    private static final String UNAVAILABLE = "Сервис учётных записей недоступен. Повторите позже";

    private final AuthServiceClient client;

    public AuthAccounts(AuthServiceClient client) {
        this.client = client;
    }

    /** Создаёт запись или возвращает созданную ранее с тем же ключом запроса. */
    public AuthUser create(UUID requestId, String username, String email, String password, Set<String> roles) {
        return call(() -> client.create(new AuthUserCreateRequest(requestId, username, email, password, roles)));
    }

    public AuthUser find(Long userId) {
        return call(() -> client.findById(userId));
    }

    public AuthUser setEnabled(Long userId, boolean enabled) {
        return call(() -> client.updateStatus(userId, new AuthStatusRequest(enabled)));
    }

    /** Компенсация: удаляет запись, только если она создана этим ключом запроса. */
    public void deleteCreated(Long userId, UUID requestId) {
        call(() -> {
            client.deleteCreated(userId, requestId);
            return null;
        });
    }

    private static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (FeignException e) {
            // Сюда попадают только сетевые сбои (RetryableException): ответы с ошибкой уже разобраны декодером
            throw new ServiceUnavailableException(UNAVAILABLE, e);
        }
    }
}
