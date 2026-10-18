package com.shelter.core.mapper;

import com.shelter.core.dto.PublicAnimalDetails;
import com.shelter.core.dto.PublicAnimalItem;
import com.shelter.core.repository.PublicAnimalView;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Component
public class PublicAnimalMapper {

    public PublicAnimalItem toItem(PublicAnimalView v, LocalDate today) {
        Period age = age(v.birthDate(), today);
        return new PublicAnimalItem(
                v.id(), v.name(), v.gender(), v.birthDate(),
                age == null ? null : age.getYears(),
                age == null ? null : age.getMonths(),
                v.color(), v.breed(),
                "RESERVED".equals(v.statusCode()),
                PhotoUrls.publicUrl(v.mainPhotoId())
        );
    }

    public PublicAnimalDetails toDetails(PublicAnimalView v, List<Long> photoIds, LocalDate today) {
        Period age = age(v.birthDate(), today);
        return new PublicAnimalDetails(
                v.id(), v.name(), v.gender(), v.birthDate(),
                age == null ? null : age.getYears(),
                age == null ? null : age.getMonths(),
                v.color(), v.breed(), v.character(), v.specialMarks(),
                v.sterilized(), v.vaccinated(),
                "RESERVED".equals(v.statusCode()),
                PhotoUrls.publicUrl(v.mainPhotoId()),
                photoIds.stream().map(PhotoUrls::publicUrl).toList()
        );
    }

    private static Period age(LocalDate birthDate, LocalDate today) {
        return birthDate == null ? null : Period.between(birthDate, today);
    }
}
