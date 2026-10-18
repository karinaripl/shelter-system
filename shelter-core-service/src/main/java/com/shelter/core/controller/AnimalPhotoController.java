package com.shelter.core.controller;

import com.shelter.core.dto.PhotoResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.AnimalPhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Фото животных: видят все сотрудники, изменяют ADMIN, CURATOR и ADOPTION_MANAGER. */
@RestController
@RequestMapping("/animals/{animalId}/photos")
public class AnimalPhotoController {

    private final AnimalPhotoService photoService;

    public AnimalPhotoController(AnimalPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<List<PhotoResponse>> findAll(@PathVariable Long animalId) {
        return ResponseEntity.ok(photoService.findByAnimal(animalId));
    }

    /** Загрузка: multipart/form-data, поле file, JPG или PNG до 5 МБ. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.PHOTOS_WRITE)
    public ResponseEntity<PhotoResponse> upload(@PathVariable Long animalId,
                                                @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(photoService.upload(animalId, file));
    }

    @DeleteMapping("/{photoId}")
    @PreAuthorize(Roles.PHOTOS_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long animalId, @PathVariable Long photoId) {
        photoService.delete(animalId, photoId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{photoId}/main")
    @PreAuthorize(Roles.PHOTOS_WRITE)
    public ResponseEntity<PhotoResponse> makeMain(@PathVariable Long animalId, @PathVariable Long photoId) {
        return ResponseEntity.ok(photoService.makeMain(animalId, photoId));
    }

    @GetMapping("/{photoId}/file")
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<byte[]> file(@PathVariable Long animalId, @PathVariable Long photoId) {
        AnimalPhotoService.PhotoFile file = photoService.file(animalId, photoId);
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .cacheControl(CacheControl.noCache())
                .body(file.content());
    }
}
