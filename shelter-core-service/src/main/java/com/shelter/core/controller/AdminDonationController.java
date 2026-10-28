package com.shelter.core.controller;

import com.shelter.core.dto.DonationResponse;
import com.shelter.core.dto.DonationType;
import com.shelter.core.dto.MoneyDonationRequest;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.DonationService;
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

/**
 * Админ-панель: пожертвования обоих видов — только ADMIN. Денежные создаются здесь,
 * натуральные — при приёме на склад (/goods-donations); удалить можно и те и другие.
 */
@RestController
@RequestMapping("/admin/donations")
@PreAuthorize(Roles.ADMIN_ONLY)
public class AdminDonationController {

    private final DonationService donationService;

    public AdminDonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<DonationResponse>> findPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) DonationType type,
            @RequestParam(required = false) Long benefactorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(donationService.findPage(from, to, type, benefactorId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DonationResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(donationService.findById(id));
    }

    /** Денежное пожертвование: создаётся операция INCOME/DONATION. */
    @PostMapping
    public ResponseEntity<DonationResponse> createMoney(@Valid @RequestBody MoneyDonationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(donationService.createMoney(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        donationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
