package com.shelter.core.controller;

import com.shelter.core.dto.EmployeeAdminResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.EmployeeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Админ-панель: сотрудники (персональные данные, должности, оклады) — только ADMIN.
 * Доступ проверяется трижды: шлюзом, правилом /admin/** в gateway-auth-starter и аннотацией ниже.
 * Остальные эндпоинты админ-панели — на шаге 6.
 */
@RestController
@RequestMapping("/admin/employees")
@PreAuthorize(Roles.ADMIN_ONLY)
public class AdminEmployeeController {

    private final EmployeeService employeeService;

    public AdminEmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<List<EmployeeAdminResponse>> findAll() {
        return ResponseEntity.ok(employeeService.findAllForAdmin());
    }
}
