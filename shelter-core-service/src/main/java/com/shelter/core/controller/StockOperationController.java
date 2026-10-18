package com.shelter.core.controller;

import com.shelter.core.dto.ExpenseRequest;
import com.shelter.core.dto.ExpenseResponse;
import com.shelter.core.dto.ExpenseResult;
import com.shelter.core.dto.ExpiredWriteOffPreview;
import com.shelter.core.dto.ExpiredWriteOffRequest;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.TransferRequest;
import com.shelter.core.dto.TransferResponse;
import com.shelter.core.dto.WriteOffReason;
import com.shelter.core.dto.WriteOffRequest;
import com.shelter.core.dto.WriteOffResponse;
import com.shelter.core.repository.StockJournalRepository.Filter;
import com.shelter.core.security.Roles;
import com.shelter.core.service.StockOperationService;
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
 * Движение по складу: расход, перемещения, списания. Читают ADMIN, CURATOR, VET; оформляют ADMIN и CURATOR
 * (ветеринар расходует материалы только через процедуры).
 */
@RestController
public class StockOperationController {

    private final StockOperationService service;

    public StockOperationController(StockOperationService service) {
        this.service = service;
    }

    // ---------- расход ----------

    @GetMapping("/expenses")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<PageResponse<ExpenseResponse>> findExpenses(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long animalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findExpenses(new Filter(productId, warehouseId, animalId, from, to), page, size));
    }

    @GetMapping("/expenses/{id}")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<ExpenseResponse> findExpense(@PathVariable Long id) {
        return ResponseEntity.ok(service.findExpense(id));
    }

    /** Ручной расход: выбранная партия или FEFO на складе; в ответе — какие партии списаны. */
    @PostMapping("/expenses")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<ExpenseResult> createExpense(@Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createExpense(request));
    }

    /** Удаление ошибочного ручного расхода (количество возвращается в ту же партию). */
    @DeleteMapping("/expenses/{id}")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        service.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- перемещения ----------

    @GetMapping("/stock-transfers")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<PageResponse<TransferResponse>> findTransfers(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findTransfers(new Filter(productId, warehouseId, null, from, to), page, size));
    }

    @PostMapping("/stock-transfers")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.transfer(request));
    }

    // ---------- списания ----------

    @GetMapping("/write-offs")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<PageResponse<WriteOffResponse>> findWriteOffs(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) WriteOffReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findWriteOffs(new Filter(productId, warehouseId, null, from, to), reason, page, size));
    }

    @GetMapping("/write-offs/{id}")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<WriteOffResponse> findWriteOff(@PathVariable Long id) {
        return ResponseEntity.ok(service.findWriteOff(id));
    }

    @PostMapping("/write-offs")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<WriteOffResponse> writeOff(@Valid @RequestBody WriteOffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.writeOff(request));
    }

    /** Предпросмотр «списать все просроченные»: что и сколько будет списано и token для подтверждения. */
    @GetMapping("/write-offs/expired/preview")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<ExpiredWriteOffPreview> previewExpired() {
        return ResponseEntity.ok(service.previewExpired());
    }

    /** Подтверждение: списывается ровно то, что было в предпросмотре (иначе 409). */
    @PostMapping("/write-offs/expired")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<List<WriteOffResponse>> writeOffExpired(@Valid @RequestBody ExpiredWriteOffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.writeOffExpired(request));
    }
}
