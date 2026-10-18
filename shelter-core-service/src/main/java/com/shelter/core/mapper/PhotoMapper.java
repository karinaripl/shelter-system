package com.shelter.core.mapper;

import com.shelter.core.dto.PhotoResponse;
import com.shelter.model.generated.shelter.tables.records.AnimalPhotoRecord;
import org.springframework.stereotype.Component;

@Component
public class PhotoMapper {

    public PhotoResponse toResponse(AnimalPhotoRecord r) {
        return new PhotoResponse(
                r.getId(),
                PhotoUrls.staff(r.getAnimalId(), r.getId()),
                r.getSortOrder(),
                Boolean.TRUE.equals(r.getIsMain()),
                r.getCreatedAt()
        );
    }
}
