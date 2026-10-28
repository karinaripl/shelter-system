package com.shelter.core.controller;

import com.shelter.core.dto.SupplyPaymentRequest;
import com.shelter.core.dto.SupplyPaymentResponse;
import com.shelter.core.dto.UnpaidSupplyResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.SupplyPaymentService;
import jakarta.validation.Valid;
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

import java.util.List;

/** Админ-панель: оплата поставок — только ADMIN. Поставки оформляет куратор (/supplies) без оплаты. */
@RestController
@RequestMapping("/admin/supplies")
@PreAuthorize(Roles.ADMIN_ONLY)
public class AdminSupplyController {

    private final SupplyPaymentService paymentService;

    public AdminSupplyController(SupplyPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/unpaid")
    public ResponseEntity<List<UnpaidSupplyResponse>> findUnpaid(@RequestParam(required = false) Long supplierId) {
        return ResponseEntity.ok(paymentService.findUnpaid(supplierId));
    }

    /** Оплата на сумму поставки: создаётся операция EXPENSE/PURCHASE. Тело можно не передавать. */
    @PostMapping("/{id}/payment")
    public ResponseEntity<SupplyPaymentResponse> pay(@PathVariable Long id,
                                                     @Valid @RequestBody(required = false) SupplyPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.pay(id, request));
    }

    /** Отмена ошибочной оплаты: поставка снова неоплачена. */
    @DeleteMapping("/{id}/payment")
    public ResponseEntity<Void> cancelPayment(@PathVariable Long id) {
        paymentService.cancelPayment(id);
        return ResponseEntity.noContent().build();
    }

    /** Удаление поставки вместе с оплатой — если по её партиям не было расхода, списания и перемещений. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentService.deleteSupply(id);
        return ResponseEntity.noContent().build();
    }
}
