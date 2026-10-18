package com.shelter.core.service;

import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.PublicAnimalDetails;
import com.shelter.core.dto.PublicAnimalItem;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.PublicAnimalMapper;
import com.shelter.core.repository.AnimalPhotoRepository;
import com.shelter.core.repository.PublicCatalogRepository;
import com.shelter.model.generated.shelter.tables.records.AnimalPhotoRecord;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Публичный каталог: животные, готовые к пристройству (и забронированные), без внутренних полей. */
@Service
public class PublicCatalogService {

    private final PublicCatalogRepository catalogRepository;
    private final AnimalPhotoRepository photoRepository;
    private final PublicAnimalMapper mapper;
    private final Clock clock;

    public PublicCatalogService(PublicCatalogRepository catalogRepository,
                                AnimalPhotoRepository photoRepository,
                                PublicAnimalMapper mapper,
                                Clock clock) {
        this.catalogRepository = catalogRepository;
        this.photoRepository = photoRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public PageResponse<PublicAnimalItem> findPage(String gender, Integer ageFrom, Integer ageTo, int page, int size) {
        Paging.validate(page, size);
        if (gender != null && !gender.equals("M") && !gender.equals("F")) {
            throw new InvalidRequestException("Пол: M или F");
        }
        if ((ageFrom != null && ageFrom < 0) || (ageTo != null && ageTo < 0)) {
            throw new InvalidRequestException("Возраст не может быть отрицательным");
        }
        if (ageFrom != null && ageTo != null && ageFrom > ageTo) {
            throw new InvalidRequestException("Возраст «от» больше возраста «до»");
        }
        LocalDate today = LocalDate.now(clock);
        List<PublicAnimalItem> content = catalogRepository.findPage(gender, ageFrom, ageTo, today, page, size).stream()
                .map(v -> mapper.toItem(v, today))
                .toList();
        return PageResponse.of(content, page, size, catalogRepository.count(gender, ageFrom, ageTo, today));
    }

    public PublicAnimalDetails findById(Long id) {
        var animal = catalogRepository.findVisible(id)
                .orElseThrow(() -> new NotFoundException("Животное с id=" + id + " не найдено в каталоге"));
        List<Long> photoIds = photoRepository.findByAnimal(id).stream().map(AnimalPhotoRecord::getId).toList();
        return mapper.toDetails(animal, photoIds, LocalDate.now(clock));
    }
}
