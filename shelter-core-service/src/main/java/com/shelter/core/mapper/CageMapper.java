package com.shelter.core.mapper;

import com.shelter.core.dto.AnimalBrief;
import com.shelter.core.dto.CageDetailsResponse;
import com.shelter.core.dto.CageResponse;
import com.shelter.core.repository.CageView;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CageMapper {

    public CageResponse toResponse(CageView v) {
        return new CageResponse(v.id(), v.number(), v.capacity(), v.cageType(), v.occupied(), free(v));
    }

    public CageDetailsResponse toDetails(CageView v, List<AnimalBrief> animals) {
        return new CageDetailsResponse(v.id(), v.number(), v.capacity(), v.cageType(), v.occupied(), free(v), animals);
    }

    private static int free(CageView v) {
        return Math.max(0, v.capacity() - v.occupied());
    }
}
