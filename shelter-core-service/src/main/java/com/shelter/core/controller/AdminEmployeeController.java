package com.shelter.core.controller;

import com.shelter.core.dto.AccountLinkRequest;
import com.shelter.core.dto.EmployeeAdminResponse;
import com.shelter.core.dto.EmployeeCreateRequest;
import com.shelter.core.dto.EmployeeDetailsResponse;
import com.shelter.core.dto.EmployeeRequest;
import com.shelter.core.dto.TerminationRequest;
import com.shelter.core.repository.EmployeeRepository.Status;
import com.shelter.core.security.Roles;
import com.shelter.core.service.EmployeeAdminService;
import com.shelter.core.service.EmployeeAdminService.CreateResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Админ-панель: сотрудники (персональные данные, должности, оклады, учётные записи, увольнение) — только ADMIN.
 * Доступ проверяется трижды: шлюзом, правилом /admin/** в gateway-auth-starter и аннотацией ниже.
 */
@RestController
@RequestMapping("/admin/employees")
@PreAuthorize(Roles.ADMIN_ONLY)
public class AdminEmployeeController {

    private final EmployeeAdminService employeeService;

    public AdminEmployeeController(EmployeeAdminService employeeService) {
        this.employeeService = employeeService;
    }

    /** search — часть ФИО; status — WORKING, TERMINATED или ALL (по умолчанию). */
    @GetMapping
    public ResponseEntity<List<EmployeeAdminResponse>> findAll(@RequestParam(required = false) String search,
                                                               @RequestParam(required = false) Long positionId,
                                                               @RequestParam(required = false) Status status) {
        return ResponseEntity.ok(employeeService.findAll(search, positionId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeDetailsResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.findById(id));
    }

    /**
     * Сотрудник и учётная запись одним действием. Idempotency-Key (UUID) — ключ запроса: повтор с ним
     * возвращает ту же карточку (200), а не создаёт вторую; без ключа повтор упрётся в занятое имя (409).
     */
    @PostMapping
    public ResponseEntity<EmployeeDetailsResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody EmployeeCreateRequest request) {
        CreateResult result = employeeService.create(request, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.employee());
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeDetailsResponse> update(@PathVariable Long id,
                                                          @Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(employeeService.update(id, request));
    }

    /** Привязка существующей учётной записи (например, демо-пользователя). */
    @PutMapping("/{id}/account")
    public ResponseEntity<EmployeeDetailsResponse> linkAccount(@PathVariable Long id,
                                                               @Valid @RequestBody AccountLinkRequest request) {
        return ResponseEntity.ok(employeeService.linkAccount(id, request.userId()));
    }

    @DeleteMapping("/{id}/account")
    public ResponseEntity<EmployeeDetailsResponse> unlinkAccount(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.unlinkAccount(id));
    }

    /** Увольнение: вход блокируется. */
    @PostMapping("/{id}/termination")
    public ResponseEntity<EmployeeDetailsResponse> terminate(@PathVariable Long id,
                                                             @Valid @RequestBody TerminationRequest request) {
        return ResponseEntity.ok(employeeService.terminate(id, request.terminationDate()));
    }

    /** Отмена ошибочного увольнения: вход разблокируется. */
    @DeleteMapping("/{id}/termination")
    public ResponseEntity<EmployeeDetailsResponse> cancelTermination(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.cancelTermination(id));
    }
}
