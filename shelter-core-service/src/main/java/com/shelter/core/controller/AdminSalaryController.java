package com.shelter.core.controller;

import com.shelter.core.dto.SalaryAccrualRequest;
import com.shelter.core.dto.SalaryAccrualResponse;
import com.shelter.core.dto.SalaryPreviewResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.SalaryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/** Админ-панель: зарплаты — только ADMIN. Месяц — в формате ГГГГ-ММ. */
@RestController
@RequestMapping("/admin/salaries")
@PreAuthorize(Roles.ADMIN_ONLY)
public class AdminSalaryController {

    private final SalaryService salaryService;

    public AdminSalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    /** Предварительный расчёт за месяц: ничего не сохраняет. */
    @GetMapping("/preview")
    public ResponseEntity<SalaryPreviewResponse> preview(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(salaryService.preview(month));
    }

    /** Начисление за месяц — один раз; всё или ничего. */
    @PostMapping
    public ResponseEntity<SalaryAccrualResponse> accrue(@Valid @RequestBody SalaryAccrualRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salaryService.accrue(request.month()));
    }

    @GetMapping("/{month}")
    public ResponseEntity<SalaryAccrualResponse> findAccrued(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(salaryService.findAccrued(month));
    }

    /** Отмена ошибочного начисления — только целиком за месяц. */
    @DeleteMapping("/{month}")
    public ResponseEntity<Void> cancel(@PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        salaryService.cancel(month);
        return ResponseEntity.noContent().build();
    }
}
