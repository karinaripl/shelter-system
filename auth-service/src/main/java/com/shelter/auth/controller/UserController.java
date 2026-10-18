package com.shelter.auth.controller;

import com.shelter.auth.dto.CreateUserRequest;
import com.shelter.auth.dto.RoleResponse;
import com.shelter.auth.dto.UpdateRolesRequest;
import com.shelter.auth.dto.UpdateStatusRequest;
import com.shelter.auth.dto.UserResponse;
import com.shelter.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Управление пользователями и ролями — часть админ-панели (/admin/**), только ADMIN.
 * Доступ проверяется трижды: шлюзом, правилом /admin/** в gateway-auth-starter и аннотацией ниже.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /** Создание пользователя (например, сотрудника) со списком ролей. */
    @PostMapping("/users")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserResponse> updateRoles(@RequestAttribute("userId") Long currentUserId,
                                                    @PathVariable Long id,
                                                    @Valid @RequestBody UpdateRolesRequest request) {
        return ResponseEntity.ok(userService.updateRoles(currentUserId, id, request.roles()));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<UserResponse> updateStatus(@RequestAttribute("userId") Long currentUserId,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(userService.updateStatus(currentUserId, id, request.enabled()));
    }

    @GetMapping("/roles")
    public ResponseEntity<List<RoleResponse>> roles() {
        return ResponseEntity.ok(userService.findAllRoles());
    }
}
