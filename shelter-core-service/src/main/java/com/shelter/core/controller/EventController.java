package com.shelter.core.controller;

import com.shelter.core.dto.AnimalEventItem;
import com.shelter.core.dto.ConditionRequest;
import com.shelter.core.dto.EventDetailsResponse;
import com.shelter.core.dto.EventEffectivenessReport;
import com.shelter.core.dto.EventRequest;
import com.shelter.core.dto.EventResponse;
import com.shelter.core.dto.EventUpdateResult;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.ParticipantRequest;
import com.shelter.core.security.Roles;
import com.shelter.core.service.EventReportService;
import com.shelter.core.service.EventService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Мероприятия и участие животных: читают все сотрудники, меняют ADMIN, ADOPTION_MANAGER, COORDINATOR.
 * Отчёт об эффективности — ADMIN, ADOPTION_MANAGER, COORDINATOR.
 */
@RestController
public class EventController {

    private final EventService eventService;
    private final EventReportService reportService;

    public EventController(EventService eventService, EventReportService reportService) {
        this.eventService = eventService;
        this.reportService = reportService;
    }

    // ---------- мероприятия ----------

    @GetMapping("/events")
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<PageResponse<EventResponse>> findPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(eventService.findPage(from, to, page, size));
    }

    @GetMapping("/events/{id}")
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<EventDetailsResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.findById(id));
    }

    @PostMapping("/events")
    @PreAuthorize(Roles.EVENTS_WRITE)
    public ResponseEntity<EventDetailsResponse> create(@Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(request));
    }

    /** Изменение; при переносе даты в ответе — животные, убранные из мероприятия (выбыли раньше новой даты). */
    @PutMapping("/events/{id}")
    @PreAuthorize(Roles.EVENTS_WRITE)
    public ResponseEntity<EventUpdateResult> update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.update(id, request));
    }

    @DeleteMapping("/events/{id}")
    @PreAuthorize(Roles.EVENTS_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- участие ----------

    @PostMapping("/events/{id}/animals")
    @PreAuthorize(Roles.EVENTS_WRITE)
    public ResponseEntity<EventDetailsResponse> addParticipant(@PathVariable Long id,
                                                               @Valid @RequestBody ParticipantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.addParticipant(id, request.animalId()));
    }

    /** Состояние животного после мероприятия. */
    @PutMapping("/events/{id}/animals/{animalId}")
    @PreAuthorize(Roles.EVENTS_WRITE)
    public ResponseEntity<EventDetailsResponse> updateCondition(@PathVariable Long id, @PathVariable Long animalId,
                                                                @Valid @RequestBody ConditionRequest request) {
        return ResponseEntity.ok(eventService.updateCondition(id, animalId, request.conditionAfter()));
    }

    @DeleteMapping("/events/{id}/animals/{animalId}")
    @PreAuthorize(Roles.EVENTS_WRITE)
    public ResponseEntity<Void> removeParticipant(@PathVariable Long id, @PathVariable Long animalId) {
        eventService.removeParticipant(id, animalId);
        return ResponseEntity.noContent().build();
    }

    /** Мероприятия животного. */
    @GetMapping("/animals/{id}/events")
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<List<AnimalEventItem>> findByAnimal(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.findByAnimal(id));
    }

    // ---------- отчёт ----------

    @GetMapping("/reports/event-effectiveness")
    @PreAuthorize(Roles.EVENT_REPORT)
    public ResponseEntity<EventEffectivenessReport> effectiveness(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.effectiveness(from, to));
    }
}
