package com.shelter.core.controller;

import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.PublicAnimalDetails;
import com.shelter.core.dto.PublicAnimalItem;
import com.shelter.core.dto.PublicEventItem;
import com.shelter.core.service.AnimalPhotoService;
import com.shelter.core.service.EventService;
import com.shelter.core.service.PublicCatalogService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Публичный каталог и анонсы мероприятий (/public/**) — доступны всем, без входа в систему. */
@RestController
@RequestMapping("/public")
public class PublicCatalogController {

    private final PublicCatalogService catalogService;
    private final AnimalPhotoService photoService;
    private final EventService eventService;

    public PublicCatalogController(PublicCatalogService catalogService, AnimalPhotoService photoService,
                                   EventService eventService) {
        this.catalogService = catalogService;
        this.photoService = photoService;
        this.eventService = eventService;
    }

    /** Животные, готовые к пристройству. Фильтры: пол (M/F), возраст в полных годах от и до. */
    @GetMapping("/animals")
    public ResponseEntity<PageResponse<PublicAnimalItem>> findPage(
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer ageFrom,
            @RequestParam(required = false) Integer ageTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(catalogService.findPage(gender, ageFrom, ageTo, page, size));
    }

    @GetMapping("/animals/{id}")
    public ResponseEntity<PublicAnimalDetails> findById(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.findById(id));
    }

    @GetMapping("/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@PathVariable Long photoId) {
        AnimalPhotoService.PhotoFile file = photoService.publicFile(photoId);
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .cacheControl(CacheControl.noCache())
                .body(file.content());
    }

    /** Анонсы будущих мероприятий: название, дата, место (без организатора). */
    @GetMapping("/events")
    public ResponseEntity<PageResponse<PublicEventItem>> upcomingEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(eventService.findUpcoming(page, size));
    }
}
