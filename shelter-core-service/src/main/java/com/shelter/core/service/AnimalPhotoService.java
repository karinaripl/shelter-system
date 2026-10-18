package com.shelter.core.service;

import com.shelter.core.dto.PhotoResponse;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.PhotoMapper;
import com.shelter.core.repository.AnimalPhotoRepository;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.PublicCatalogRepository;
import com.shelter.model.generated.shelter.tables.records.AnimalPhotoRecord;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Фото животных. Первое загруженное фото становится главным; при удалении главного
 * главным становится следующее по порядку.
 */
@Service
public class AnimalPhotoService {

    /** Файл фото и его тип для ответа. */
    public record PhotoFile(byte[] content, MediaType mediaType) {
    }

    private final AnimalPhotoRepository photoRepository;
    private final AnimalRepository animalRepository;
    private final PublicCatalogRepository publicCatalogRepository;
    private final PhotoStorage storage;
    private final ImageValidator imageValidator;
    private final PhotoMapper photoMapper;

    public AnimalPhotoService(AnimalPhotoRepository photoRepository,
                              AnimalRepository animalRepository,
                              PublicCatalogRepository publicCatalogRepository,
                              PhotoStorage storage,
                              ImageValidator imageValidator,
                              PhotoMapper photoMapper) {
        this.photoRepository = photoRepository;
        this.animalRepository = animalRepository;
        this.publicCatalogRepository = publicCatalogRepository;
        this.storage = storage;
        this.imageValidator = imageValidator;
        this.photoMapper = photoMapper;
    }

    public List<PhotoResponse> findByAnimal(Long animalId) {
        ensureAnimalExists(animalId);
        return photoRepository.findByAnimal(animalId).stream().map(photoMapper::toResponse).toList();
    }

    @Transactional
    public PhotoResponse upload(Long animalId, MultipartFile file) {
        animalRepository.lockById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Файл не передан или пустой");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestException("Не удалось прочитать загруженный файл");
        }
        ImageValidator.ImageType type = imageValidator.validate(content);

        String fileName = storage.save(content, type.extension());
        try {
            boolean first = photoRepository.findByAnimal(animalId).isEmpty();
            AnimalPhotoRecord photo = photoRepository.insert(
                    animalId, fileName, photoRepository.nextSortOrder(animalId), first);
            return photoMapper.toResponse(photo);
        } catch (RuntimeException e) {
            // Запись в базу не удалась — файл не должен остаться без записи
            storage.deleteQuietly(fileName);
            throw e;
        }
    }

    @Transactional
    public void delete(Long animalId, Long photoId) {
        animalRepository.lockById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        AnimalPhotoRecord photo = findOwnPhoto(animalId, photoId);
        photoRepository.delete(photoId);
        if (Boolean.TRUE.equals(photo.getIsMain())) {
            photoRepository.findByAnimal(animalId).stream().findFirst()
                    .ifPresent(next -> photoRepository.makeMain(animalId, next.getId()));
        }
        storage.deleteQuietly(photo.getFilePath());
    }

    @Transactional
    public PhotoResponse makeMain(Long animalId, Long photoId) {
        animalRepository.lockById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        findOwnPhoto(animalId, photoId);
        photoRepository.makeMain(animalId, photoId);
        return photoMapper.toResponse(photoRepository.findById(photoId).orElseThrow());
    }

    /** Файл фото для сотрудников. */
    public PhotoFile file(Long animalId, Long photoId) {
        return read(findOwnPhoto(animalId, photoId));
    }

    /** Файл фото для публичного каталога: только пока животное есть в каталоге. */
    public PhotoFile publicFile(Long photoId) {
        AnimalPhotoRecord photo = photoRepository.findById(photoId)
                .filter(p -> publicCatalogRepository.isVisible(p.getAnimalId()))
                .orElseThrow(() -> NotFoundException.photo(photoId));
        return read(photo);
    }

    private PhotoFile read(AnimalPhotoRecord photo) {
        return new PhotoFile(storage.read(photo.getFilePath()), ImageValidator.ImageType.mediaTypeOf(photo.getFilePath()));
    }

    private AnimalPhotoRecord findOwnPhoto(Long animalId, Long photoId) {
        return photoRepository.findById(photoId)
                .filter(p -> p.getAnimalId().equals(animalId))
                .orElseThrow(() -> NotFoundException.photo(photoId));
    }

    private void ensureAnimalExists(Long animalId) {
        animalRepository.findById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
    }
}
