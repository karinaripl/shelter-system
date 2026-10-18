package com.shelter.core.controller;

import com.shelter.core.dto.BelowMinimumItem;
import com.shelter.core.dto.ExpiringBatchItem;
import com.shelter.core.dto.MovementReport;
import com.shelter.core.dto.StockReportItem;
import com.shelter.core.security.Roles;
import com.shelter.core.service.StockReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Отчёты склада: ADMIN, CURATOR, VET. Только количества, без сумм денег. */
@RestController
@RequestMapping("/reports")
public class StockReportController {

    private final StockReportService service;

    public StockReportController(StockReportService service) {
        this.service = service;
    }

    /** Остатки по товарам и складам. */
    @GetMapping("/stock")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<List<StockReportItem>> stock(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(service.stock(includeArchived));
    }

    /** Партии с истекающим сроком годности (по умолчанию — в ближайшие 30 дней). */
    @GetMapping("/expiring")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<List<ExpiringBatchItem>> expiring(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(service.expiring(days));
    }

    /** Просроченные партии с остатком — «к списанию». */
    @GetMapping("/expired")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<List<ExpiringBatchItem>> expired() {
        return ResponseEntity.ok(service.expired());
    }

    /** Товары, годный остаток которых ниже минимального. */
    @GetMapping("/below-minimum")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<List<BelowMinimumItem>> belowMinimum() {
        return ResponseEntity.ok(service.belowMinimum());
    }

    /** Движение товара за период (по умолчанию — последние 30 дней). */
    @GetMapping("/movements")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<MovementReport> movements(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.movements(productId, from, to));
    }
}
