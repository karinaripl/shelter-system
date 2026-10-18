package com.shelter.core.controller;

import com.shelter.core.dto.CageDetailsResponse;
import com.shelter.core.dto.CageRequest;
import com.shelter.core.dto.CageResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.CageService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Клетки: читают все сотрудники, изменяют ADMIN и CURATOR. */
@RestController
@RequestMapping("/cages")
public class CageController {

    private final CageService cageService;

    public CageController(CageService cageService) {
        this.cageService = cageService;
    }

    @GetMapping
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<List<CageResponse>> findAll() {
        return ResponseEntity.ok(cageService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<CageDetailsResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(cageService.findById(id));
    }

    @PostMapping
    @PreAuthorize(Roles.ANIMALS_WRITE)
    public ResponseEntity<CageResponse> create(@Valid @RequestBody CageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cageService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.ANIMALS_WRITE)
    public ResponseEntity<CageResponse> update(@PathVariable Long id, @Valid @RequestBody CageRequest request) {
        return ResponseEntity.ok(cageService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.ANIMALS_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
