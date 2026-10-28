package com.shelter.core.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/** Внутренний API auth-service (/internal/users): учётные записи сотрудников. Ошибки разбирает {@link AuthErrorDecoder}. */
@FeignClient(name = "auth-service", url = "${auth-service.url}", configuration = AuthClientConfig.class)
public interface AuthServiceClient {

    @PostMapping("/internal/users")
    AuthUser create(@RequestBody AuthUserCreateRequest request);

    @GetMapping("/internal/users/{id}")
    AuthUser findById(@PathVariable("id") Long id);

    @PutMapping("/internal/users/{id}/status")
    AuthUser updateStatus(@PathVariable("id") Long id, @RequestBody AuthStatusRequest request);

    @DeleteMapping("/internal/users/{id}")
    void deleteCreated(@PathVariable("id") Long id, @RequestParam("requestId") UUID requestId);
}
