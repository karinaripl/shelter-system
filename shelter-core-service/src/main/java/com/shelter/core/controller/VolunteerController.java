package com.shelter.core.controller;

import com.shelter.core.dto.AssignmentCloseRequest;
import com.shelter.core.dto.AssignmentRequest;
import com.shelter.core.dto.AssignmentResponse;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.VolunteerActiveRequest;
import com.shelter.core.dto.VolunteerRequest;
import com.shelter.core.dto.VolunteerResponse;
import com.shelter.core.exception.ForbiddenActionException;
import com.shelter.core.security.CurrentUser;
import com.shelter.core.security.Roles;
import com.shelter.core.service.AssignmentService;
import com.shelter.core.service.VolunteerService;
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

import java.util.List;

/**
 * Волонтёры и закрепления за животными. Анкету с паспортом видят и меняют ADMIN и COORDINATOR;
 * CURATOR видит только имя и контакты (остальных полей в ответе нет) и закрепления.
 */
@RestController
public class VolunteerController {

    private final VolunteerService volunteerService;
    private final AssignmentService assignmentService;
    private final CurrentUser currentUser;

    public VolunteerController(VolunteerService volunteerService, AssignmentService assignmentService,
                               CurrentUser currentUser) {
        this.volunteerService = volunteerService;
        this.assignmentService = assignmentService;
        this.currentUser = currentUser;
    }

    // ---------- волонтёры ----------

    /** Поиск по части имени (name) и навыков (skills); неактивные — с includeInactive=true. */
    @GetMapping("/volunteers")
    @PreAuthorize(Roles.VOLUNTEERS_READ)
    public ResponseEntity<PageResponse<VolunteerResponse>> findPage(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String skills,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        boolean full = seesFullProfile();
        if (!full && skills != null) {
            // навыки — часть анкеты, которую куратор не видит
            throw new ForbiddenActionException("Поиск по навыкам доступен только администратору и координатору");
        }
        PageResponse<VolunteerResponse> result = volunteerService.findPage(name, skills, includeInactive, page, size);
        if (full) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(new PageResponse<>(
                result.content().stream().map(VolunteerResponse::contactsOnly).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @GetMapping("/volunteers/{id}")
    @PreAuthorize(Roles.VOLUNTEERS_READ)
    public ResponseEntity<VolunteerResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(visible(volunteerService.findById(id)));
    }

    @PostMapping("/volunteers")
    @PreAuthorize(Roles.VOLUNTEERS_WRITE)
    public ResponseEntity<VolunteerResponse> create(@Valid @RequestBody VolunteerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(volunteerService.create(request));
    }

    @PutMapping("/volunteers/{id}")
    @PreAuthorize(Roles.VOLUNTEERS_WRITE)
    public ResponseEntity<VolunteerResponse> update(@PathVariable Long id, @Valid @RequestBody VolunteerRequest request) {
        return ResponseEntity.ok(volunteerService.update(id, request));
    }

    @PutMapping("/volunteers/{id}/active")
    @PreAuthorize(Roles.VOLUNTEERS_WRITE)
    public ResponseEntity<VolunteerResponse> setActive(@PathVariable Long id,
                                                       @Valid @RequestBody VolunteerActiveRequest request) {
        return ResponseEntity.ok(volunteerService.setActive(id, request.active()));
    }

    @DeleteMapping("/volunteers/{id}")
    @PreAuthorize(Roles.VOLUNTEERS_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        volunteerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- закрепления ----------

    /** Закрепления волонтёра: active=true — действующие, false — закрытые, без параметра — все. */
    @GetMapping("/volunteers/{id}/assignments")
    @PreAuthorize(Roles.VOLUNTEERS_READ)
    public ResponseEntity<List<AssignmentResponse>> findByVolunteer(@PathVariable Long id,
                                                                    @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(assignmentService.findByVolunteer(id, active));
    }

    /** Волонтёры животного: действующие и история. */
    @GetMapping("/animals/{id}/volunteers")
    @PreAuthorize(Roles.VOLUNTEERS_READ)
    public ResponseEntity<List<AssignmentResponse>> findByAnimal(@PathVariable Long id,
                                                                 @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(assignmentService.findByAnimal(id, active));
    }

    @GetMapping("/volunteer-assignments/{id}")
    @PreAuthorize(Roles.VOLUNTEERS_READ)
    public ResponseEntity<AssignmentResponse> findAssignment(@PathVariable Long id) {
        return ResponseEntity.ok(assignmentService.findById(id));
    }

    @PostMapping("/volunteer-assignments")
    @PreAuthorize(Roles.VOLUNTEERS_WRITE)
    public ResponseEntity<AssignmentResponse> assign(@Valid @RequestBody AssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.create(request));
    }

    /** Закрытие закрепления; тело необязательно (дата окончания по умолчанию — сегодня). */
    @PutMapping("/volunteer-assignments/{id}/close")
    @PreAuthorize(Roles.VOLUNTEERS_WRITE)
    public ResponseEntity<AssignmentResponse> close(@PathVariable Long id,
                                                    @RequestBody(required = false) AssignmentCloseRequest request) {
        return ResponseEntity.ok(assignmentService.close(id, request != null ? request.endDate() : null));
    }

    @DeleteMapping("/volunteer-assignments/{id}")
    @PreAuthorize(Roles.VOLUNTEERS_WRITE)
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        assignmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- вспомогательное ----------

    private boolean seesFullProfile() {
        return currentUser.hasRole(Roles.ADMIN) || currentUser.hasRole(Roles.COORDINATOR);
    }

    private VolunteerResponse visible(VolunteerResponse volunteer) {
        return seesFullProfile() ? volunteer : volunteer.contactsOnly();
    }
}
