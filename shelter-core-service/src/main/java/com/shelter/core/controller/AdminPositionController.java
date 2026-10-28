package com.shelter.core.controller;

import com.shelter.core.dto.PositionRequest;
import com.shelter.core.dto.PositionResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.PositionService;
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

/** Админ-панель: справочник должностей и окладов — только ADMIN. */
@RestController
@RequestMapping("/admin/positions")
@PreAuthorize(Roles.ADMIN_ONLY)
public class AdminPositionController {

    private final PositionService positionService;

    public AdminPositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping
    public ResponseEntity<List<PositionResponse>> findAll() {
        return ResponseEntity.ok(positionService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PositionResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(positionService.findById(id));
    }

    @PostMapping
    public ResponseEntity<PositionResponse> create(@Valid @RequestBody PositionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(positionService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PositionResponse> update(@PathVariable Long id, @Valid @RequestBody PositionRequest request) {
        return ResponseEntity.ok(positionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
