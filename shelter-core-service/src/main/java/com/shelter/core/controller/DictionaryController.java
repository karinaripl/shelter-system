package com.shelter.core.controller;

import com.shelter.core.dto.AnimalStatusResponse;
import com.shelter.core.dto.DictionaryItem;
import com.shelter.core.security.Roles;
import com.shelter.core.service.DictionaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Справочники для форм. Читают все сотрудники; меняются только миграциями. */
@RestController
@PreAuthorize(Roles.STAFF)
public class DictionaryController {

    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @GetMapping("/animal-statuses")
    public ResponseEntity<List<AnimalStatusResponse>> statuses() {
        return ResponseEntity.ok(dictionaryService.findAllStatuses());
    }

    @GetMapping("/arrival-sources")
    public ResponseEntity<List<DictionaryItem>> arrivalSources() {
        return ResponseEntity.ok(dictionaryService.findAllArrivalSources());
    }
}
