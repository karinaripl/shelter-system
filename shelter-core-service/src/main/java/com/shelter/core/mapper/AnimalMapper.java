package com.shelter.core.mapper;

import com.shelter.core.dto.AnimalBrief;
import com.shelter.core.dto.AnimalListItem;
import com.shelter.core.dto.AnimalResponse;
import com.shelter.core.dto.AnimalStatusResponse;
import com.shelter.core.dto.DictionaryItem;
import com.shelter.core.dto.HealthState;
import com.shelter.core.repository.AnimalView;
import org.springframework.stereotype.Component;

@Component
public class AnimalMapper {

    public AnimalResponse toResponse(AnimalView v) {
        return new AnimalResponse(
                v.id(),
                v.name(),
                v.gender(),
                v.birthDate(),
                v.color(),
                v.breed(),
                v.character(),
                v.specialMarks(),
                new AnimalStatusResponse(v.statusCode(), v.statusName(), v.statusFinal()),
                HealthState.valueOf(v.healthState()),
                v.cageId() == null ? null : new AnimalResponse.CageRef(v.cageId(), v.cageNumber()),
                v.arrivalDate(),
                new DictionaryItem(v.arrivalSourceCode(), v.arrivalSourceName()),
                v.departureDate(),
                v.departureNote(),
                PhotoUrls.staff(v.id(), v.mainPhotoId()),
                v.createdAt(),
                v.updatedAt()
        );
    }

    public AnimalListItem toListItem(AnimalView v) {
        return new AnimalListItem(
                v.id(),
                v.name(),
                v.gender(),
                v.birthDate(),
                v.statusCode(),
                v.statusName(),
                HealthState.valueOf(v.healthState()),
                v.cageId(),
                v.cageNumber(),
                v.arrivalDate(),
                v.departureDate(),
                PhotoUrls.staff(v.id(), v.mainPhotoId())
        );
    }

    public AnimalBrief toBrief(AnimalView v) {
        return new AnimalBrief(v.id(), v.name(), v.statusCode(), v.statusName(), HealthState.valueOf(v.healthState()));
    }
}
