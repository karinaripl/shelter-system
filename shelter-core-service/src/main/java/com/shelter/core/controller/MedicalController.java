package com.shelter.core.controller;

import com.shelter.core.dto.HealthSummaryResponse;
import com.shelter.core.dto.MedicalCardResponse;
import com.shelter.core.dto.MedicalCardUpdateRequest;
import com.shelter.core.dto.MedicalRecordRequest;
import com.shelter.core.dto.MedicalRecordResponse;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.ProcedureDeletionResponse;
import com.shelter.core.dto.ProcedureRequest;
import com.shelter.core.dto.ProcedureResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.MedicalCardService;
import com.shelter.core.service.MedicalRecordService;
import com.shelter.core.service.ProcedureService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Медицина. Полная медкарта (осмотры, диагнозы, процедуры, флаги) — только ADMIN и VET;
 * сводка здоровья — все сотрудники. Медицинское состояние (health-state) — в AnimalController.
 */
@RestController
public class MedicalController {

    private final MedicalCardService medicalCardService;
    private final MedicalRecordService medicalRecordService;
    private final ProcedureService procedureService;

    public MedicalController(MedicalCardService medicalCardService,
                             MedicalRecordService medicalRecordService,
                             ProcedureService procedureService) {
        this.medicalCardService = medicalCardService;
        this.medicalRecordService = medicalRecordService;
        this.procedureService = procedureService;
    }

    // ---------- медкарта и сводка ----------

    @GetMapping("/animals/{id}/medical-card")
    @PreAuthorize(Roles.MEDICAL)
    public ResponseEntity<MedicalCardResponse> getCard(@PathVariable Long id) {
        return ResponseEntity.ok(medicalCardService.getCard(id));
    }

    /** Флаги (стерилизован, привит, обработан от паразитов), хронические болезни и заметки. */
    @PutMapping("/animals/{id}/medical-card")
    @PreAuthorize(Roles.MEDICAL)
    public ResponseEntity<MedicalCardResponse> updateCard(@PathVariable Long id,
                                                          @Valid @RequestBody MedicalCardUpdateRequest request) {
        return ResponseEntity.ok(medicalCardService.updateCard(id, request));
    }

    @GetMapping("/animals/{id}/health-summary")
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<HealthSummaryResponse> healthSummary(@PathVariable Long id) {
        return ResponseEntity.ok(medicalCardService.healthSummary(id));
    }

    // ---------- осмотры ----------

    /** Осмотры животного, новые первыми (page с нуля). */
    @GetMapping("/animals/{id}/medical-records")
    @PreAuthorize(Roles.MEDICAL)
    public ResponseEntity<PageResponse<MedicalRecordResponse>> findRecords(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(medicalRecordService.findPage(id, page, size));
    }

    @PostMapping("/animals/{id}/medical-records")
    @PreAuthorize(Roles.MEDICAL)
    public ResponseEntity<MedicalRecordResponse> createRecord(@PathVariable Long id,
                                                              @Valid @RequestBody MedicalRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicalRecordService.create(id, request));
    }

    @GetMapping("/medical-records/{id}")
    @PreAuthorize(Roles.MEDICAL)
    public ResponseEntity<MedicalRecordResponse> findRecord(@PathVariable Long id) {
        return ResponseEntity.ok(medicalRecordService.findById(id));
    }

    @DeleteMapping("/medical-records/{id}")
    @PreAuthorize(Roles.MEDICAL)
    public ResponseEntity<Void> deleteRecord(@PathVariable Long id) {
        medicalRecordService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- процедуры ----------

    /** Процедуры животного, новые первыми, со списанными материалами. */
    @GetMapping("/animals/{id}/procedures")
    @PreAuthorize(Roles.MEDICAL)
    public ResponseEntity<PageResponse<ProcedureResponse>> findProcedures(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(procedureService.findPage(id, page, size));
    }

    /** Внесение процедуры: материалы норм списываются со склада (выбранные партии или FEFO). */
    @PostMapping("/animals/{id}/procedures")
    @PreAuthorize(Roles.MEDICAL)
    public ResponseEntity<ProcedureResponse> createProcedure(@PathVariable Long id,
                                                             @Valid @RequestBody ProcedureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procedureService.create(id, request));
    }

    @GetMapping("/procedures/{id}")
    @PreAuthorize(Roles.MEDICAL)
    public ResponseEntity<ProcedureResponse> findProcedure(@PathVariable Long id) {
        return ResponseEntity.ok(procedureService.findById(id));
    }

    /** Удаление ошибочной процедуры: материалы возвращаются на склад, флаг снимается, если его поставила она. */
    @DeleteMapping("/procedures/{id}")
    @PreAuthorize(Roles.MEDICAL)
    public ResponseEntity<ProcedureDeletionResponse> deleteProcedure(@PathVariable Long id) {
        return ResponseEntity.ok(procedureService.delete(id));
    }
}
