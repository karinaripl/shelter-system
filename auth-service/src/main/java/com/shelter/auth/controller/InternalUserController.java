package com.shelter.auth.controller;

import com.shelter.auth.dto.InternalCreateUserRequest;
import com.shelter.auth.dto.InternalUserResult;
import com.shelter.auth.dto.UpdateStatusRequest;
import com.shelter.auth.dto.UserResponse;
import com.shelter.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Внутренний API для shelter-core-service: учётные записи сотрудников. Шлюз пути /internal/** не маршрутизирует,
 * в Docker сервис виден только внутри сети, поэтому заголовков шлюза здесь нет.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    /** 201 — запись создана, 200 — запись с этим ключом запроса уже была (повтор). */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody InternalCreateUserRequest request) {
        InternalUserResult result = userService.createInternal(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.user());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /** Блокировка при увольнении сотрудника и разблокировка при отмене увольнения. */
    @PutMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateStatus(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(userService.updateStatusInternal(id, request.enabled()));
    }

    /** Компенсация неудачного создания сотрудника: удаляется только запись, созданная с этим ключом запроса. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCreated(@PathVariable Long id, @RequestParam UUID requestId) {
        userService.deleteCreatedInternal(id, requestId);
        return ResponseEntity.noContent().build();
    }
}
