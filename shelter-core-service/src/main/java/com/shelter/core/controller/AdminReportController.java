package com.shelter.core.controller;

import com.shelter.core.dto.BenefactorDonationsReport;
import com.shelter.core.dto.CategoryReport;
import com.shelter.core.dto.MonthlyReport;
import com.shelter.core.dto.PeriodBalanceReport;
import com.shelter.core.security.Roles;
import com.shelter.core.service.FinanceReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Админ-панель: финансовые отчёты — только ADMIN. Отчёты склада без денег — в /reports/**. */
@RestController
@RequestMapping("/admin/reports")
@PreAuthorize(Roles.ADMIN_ONLY)
public class AdminReportController {

    private final FinanceReportService reportService;

    public AdminReportController(FinanceReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/finance/categories")
    public ResponseEntity<CategoryReport> byCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.byCategory(from, to));
    }

    /** По месяцам — для графика. */
    @GetMapping("/finance/monthly")
    public ResponseEntity<MonthlyReport> byMonth(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.byMonth(from, to));
    }

    @GetMapping("/finance/balance")
    public ResponseEntity<PeriodBalanceReport> balance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.balance(from, to));
    }

    @GetMapping("/donations/benefactors")
    public ResponseEntity<BenefactorDonationsReport> byBenefactor(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.byBenefactor(from, to));
    }
}
