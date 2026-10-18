package com.shelter.core.controller;

import com.shelter.core.dto.ConsumptionRatesRequest;
import com.shelter.core.dto.ProcedureMaterialsResponse;
import com.shelter.core.dto.ProcedureTypeRequest;
import com.shelter.core.dto.ProcedureTypeResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.ProcedureTypeService;
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

/** Виды процедур и нормы расхода: читают ADMIN, VET, CURATOR; изменяют ADMIN и VET. */
@RestController
@RequestMapping("/procedure-types")
public class ProcedureTypeController {

    private final ProcedureTypeService procedureTypeService;

    public ProcedureTypeController(ProcedureTypeService procedureTypeService) {
        this.procedureTypeService = procedureTypeService;
    }

    @GetMapping
    @PreAuthorize(Roles.PROCEDURE_TYPES_READ)
    public ResponseEntity<List<ProcedureTypeResponse>> findAll() {
        return ResponseEntity.ok(procedureTypeService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.PROCEDURE_TYPES_READ)
    public ResponseEntity<ProcedureTypeResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(procedureTypeService.findById(id));
    }

    /** Подходящие партии по каждому материалу нормы (FEFO, первая — рекомендуемая) и просроченные. */
    @GetMapping("/{id}/materials")
    @PreAuthorize(Roles.PROCEDURE_TYPES_READ)
    public ResponseEntity<ProcedureMaterialsResponse> materials(@PathVariable Long id) {
        return ResponseEntity.ok(procedureTypeService.materials(id));
    }

    @PostMapping
    @PreAuthorize(Roles.PROCEDURE_TYPES_WRITE)
    public ResponseEntity<ProcedureTypeResponse> create(@Valid @RequestBody ProcedureTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procedureTypeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.PROCEDURE_TYPES_WRITE)
    public ResponseEntity<ProcedureTypeResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody ProcedureTypeRequest request) {
        return ResponseEntity.ok(procedureTypeService.update(id, request));
    }

    /** Замена норм расхода целиком. */
    @PutMapping("/{id}/rates")
    @PreAuthorize(Roles.PROCEDURE_TYPES_WRITE)
    public ResponseEntity<ProcedureTypeResponse> replaceRates(@PathVariable Long id,
                                                              @Valid @RequestBody ConsumptionRatesRequest request) {
        return ResponseEntity.ok(procedureTypeService.replaceRates(id, request.rates()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.PROCEDURE_TYPES_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        procedureTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
