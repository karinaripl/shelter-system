package com.shelter.core.controller;

import com.shelter.core.dto.BenefactorBrief;
import com.shelter.core.dto.GoodsDonationRequest;
import com.shelter.core.dto.GoodsDonationResponse;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.SupplyRequest;
import com.shelter.core.dto.SupplyResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.ReceiptService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Приход: поставки (без оплаты) и натуральные пожертвования — ADMIN и CURATOR.
 * Цены и сумму поставки видит только ADMIN; оплата поставок — в админ-панели (шаг 6).
 */
@RestController
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    // ---------- поставки ----------

    @GetMapping("/supplies")
    @PreAuthorize(Roles.RECEIPTS)
    public ResponseEntity<PageResponse<SupplyResponse>> findSupplies(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(receiptService.findSupplies(supplierId, from, to, page, size));
    }

    @GetMapping("/supplies/{id}")
    @PreAuthorize(Roles.RECEIPTS)
    public ResponseEntity<SupplyResponse> findSupply(@PathVariable Long id) {
        return ResponseEntity.ok(receiptService.findSupply(id));
    }

    @PostMapping("/supplies")
    @PreAuthorize(Roles.RECEIPTS)
    public ResponseEntity<SupplyResponse> createSupply(@Valid @RequestBody SupplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(receiptService.createSupply(request));
    }

    /** Ошибочная неоплаченная поставка без движения по её партиям. */
    @DeleteMapping("/supplies/{id}")
    @PreAuthorize(Roles.RECEIPTS)
    public ResponseEntity<Void> deleteSupply(@PathVariable Long id) {
        receiptService.deleteSupply(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- натуральные пожертвования ----------

    @GetMapping("/goods-donations")
    @PreAuthorize(Roles.RECEIPTS)
    public ResponseEntity<PageResponse<GoodsDonationResponse>> findGoodsDonations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(receiptService.findGoodsDonations(from, to, page, size));
    }

    @GetMapping("/goods-donations/{id}")
    @PreAuthorize(Roles.RECEIPTS)
    public ResponseEntity<GoodsDonationResponse> findGoodsDonation(@PathVariable Long id) {
        return ResponseEntity.ok(receiptService.findGoodsDonation(id));
    }

    @PostMapping("/goods-donations")
    @PreAuthorize(Roles.RECEIPTS)
    public ResponseEntity<GoodsDonationResponse> createGoodsDonation(@Valid @RequestBody GoodsDonationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(receiptService.createGoodsDonation(request));
    }

    @DeleteMapping("/goods-donations/{id}")
    @PreAuthorize(Roles.RECEIPTS)
    public ResponseEntity<Void> deleteGoodsDonation(@PathVariable Long id) {
        receiptService.deleteGoodsDonation(id);
        return ResponseEntity.noContent().build();
    }

    /** Благотворители для формы пожертвования: только id и имя (контакты — в админ-панели). */
    @GetMapping("/benefactors")
    @PreAuthorize(Roles.RECEIPTS)
    public ResponseEntity<List<BenefactorBrief>> findBenefactors(@RequestParam(required = false) String name) {
        return ResponseEntity.ok(receiptService.findBenefactors(name));
    }
}
