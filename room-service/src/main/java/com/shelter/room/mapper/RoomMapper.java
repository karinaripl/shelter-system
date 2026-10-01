package com.shelter.room.mapper;

import com.shelter.model.generated.room.tables.records.RoomsRecord;
import com.shelter.room.dto.RoomResponse;
import org.springframework.stereotype.Component;

@Component
public class RoomMapper {

    public RoomResponse toResponse(RoomsRecord record) {
        return new RoomResponse(
                record.getId(),
                record.getName(),
                record.getCapacity(),
                record.getDescription(),
                record.getLocation(),
                record.getIsActive(),
                record.getCreatedAt()
        );
    }
}