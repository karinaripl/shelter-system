package com.shelter.core.controller;

import com.shelter.core.dto.BalanceResponse;
import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.ManualTransactionRequest;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.TransactionJournalResponse;
import com.shelter.core.dto.TransactionResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.FinanceService;
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

import java.time.LocalDate;

/** Админ-панель: журнал денежных операций и баланс — только ADMIN. */
@RestController
@RequestMapping("/admin/finance")
@PreAuthorize(Roles.ADMIN_ONLY)
public class AdminFinanceController {

    private final FinanceService financeService;

    public AdminFinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/transactions")
    public ResponseEntity<TransactionJournalResponse> findJournal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) MoneyDirection direction,
            @RequestParam(required = false) FinanceCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(financeService.findJournal(from, to, direction, category, page, size));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(financeService.findById(id));
    }

    /** Ручная операция категории OTHER (ремонт клеток, приход от продажи) с комментарием. */
    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody ManualTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(financeService.createManual(request));
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        financeService.deleteManual(id);
        return ResponseEntity.noContent().build();
    }

    /** Баланс на конец дня date (по умолчанию — сегодня). */
    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> balance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(financeService.balance(date));
    }
}
