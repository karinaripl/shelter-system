package com.shelter.core.controller;

import com.shelter.core.dto.AnimalFilter;
import com.shelter.core.dto.AnimalIntakeRequest;
import com.shelter.core.dto.AnimalListItem;
import com.shelter.core.dto.AnimalResponse;
import com.shelter.core.dto.AnimalStatusCode;
import com.shelter.core.dto.AnimalUpdateRequest;
import com.shelter.core.dto.DepartureRequest;
import com.shelter.core.dto.HealthStateRequest;
import com.shelter.core.dto.MoveRequest;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.StatusChangeRequest;
import com.shelter.core.security.Roles;
import com.shelter.core.service.AnimalService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Животные. Читают все сотрудники; изменяют ADMIN и CURATOR;
 * медицинское состояние — ADMIN и VET; выбытие «умер» может оформить и VET.
 */
@RestController
@RequestMapping("/animals")
public class AnimalController {

    private final AnimalService animalService;

    public AnimalController(AnimalService animalService) {
        this.animalService = animalService;
    }

    /**
     * Список с фильтрами, пагинацией (page с нуля) и сортировкой (например, sort=arrivalDate,desc).
     * presentOnly=true (по умолчанию) — только не выбывшие.
     */
    @GetMapping
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<PageResponse<AnimalListItem>> findPage(
            @RequestParam(required = false) AnimalStatusCode status,
            @RequestParam(required = false) Long cageId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrivedTo,
            @RequestParam(defaultValue = "true") boolean presentOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        AnimalFilter filter = new AnimalFilter(status, cageId, name, arrivedFrom, arrivedTo, presentOnly);
        return ResponseEntity.ok(animalService.findPage(filter, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<AnimalResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(animalService.findById(id));
    }

    /** Регистрация поступления. */
    @PostMapping
    @PreAuthorize(Roles.ANIMALS_WRITE)
    public ResponseEntity<AnimalResponse> registerIntake(@Valid @RequestBody AnimalIntakeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(animalService.registerIntake(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.ANIMALS_WRITE)
    public ResponseEntity<AnimalResponse> update(@PathVariable Long id, @Valid @RequestBody AnimalUpdateRequest request) {
        return ResponseEntity.ok(animalService.updateDescription(id, request));
    }

    /** Перевод в другую клетку. */
    @PutMapping("/{id}/cage")
    @PreAuthorize(Roles.ANIMALS_WRITE)
    public ResponseEntity<AnimalResponse> move(@PathVariable Long id, @Valid @RequestBody MoveRequest request) {
        return ResponseEntity.ok(animalService.moveToCage(id, request.cageId()));
    }

    /** Смена этапа жизненного цикла (кроме итоговых). RESERVED — только ADMIN (проверяется в сервисе). */
    @PutMapping("/{id}/status")
    @PreAuthorize(Roles.ANIMALS_WRITE)
    public ResponseEntity<AnimalResponse> changeStatus(@PathVariable Long id,
                                                       @Valid @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(animalService.changeStatus(id, request));
    }

    /** Медицинское состояние: здоров / карантин / лечение. */
    @PutMapping("/{id}/health-state")
    @PreAuthorize(Roles.HEALTH_WRITE)
    public ResponseEntity<AnimalResponse> changeHealthState(@PathVariable Long id,
                                                            @Valid @RequestBody HealthStateRequest request) {
        return ResponseEntity.ok(animalService.changeHealthState(id, request.healthState()));
    }

    /** Оформление выбытия. */
    @PostMapping("/{id}/departure")
    @PreAuthorize(Roles.DEPARTURE_WRITE)
    public ResponseEntity<AnimalResponse> registerDeparture(@PathVariable Long id,
                                                            @Valid @RequestBody DepartureRequest request) {
        return ResponseEntity.ok(animalService.registerDeparture(id, request));
    }
}
