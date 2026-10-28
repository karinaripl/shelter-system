package com.shelter.core.controller;

import com.shelter.core.dto.BenefactorRequest;
import com.shelter.core.dto.BenefactorResponse;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.BenefactorService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Админ-панель: благотворители с контактами — только ADMIN (куратору — только имена, GET /benefactors). */
@RestController
@RequestMapping("/admin/benefactors")
@PreAuthorize(Roles.ADMIN_ONLY)
public class AdminBenefactorController {

    private final BenefactorService benefactorService;

    public AdminBenefactorController(BenefactorService benefactorService) {
        this.benefactorService = benefactorService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<BenefactorResponse>> findPage(@RequestParam(required = false) String name,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(benefactorService.findPage(name, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BenefactorResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(benefactorService.findById(id));
    }

    @PostMapping
    public ResponseEntity<BenefactorResponse> create(@Valid @RequestBody BenefactorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(benefactorService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BenefactorResponse> update(@PathVariable Long id, @Valid @RequestBody BenefactorRequest request) {
        return ResponseEntity.ok(benefactorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        benefactorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
