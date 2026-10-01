package com.shelter.booking.mapper;

import com.shelter.booking.dto.BookingResponse;
import com.shelter.model.generated.booking.tables.records.BookingRecord;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public BookingResponse toResponse(BookingRecord record) {
        return new BookingResponse(
                record.getId(),
                record.getUserId(),
                record.getRoomId(),
                record.getStartTime(),
                record.getEndTime(),
                record.getStatus(),
                record.getCreatedAt()
        );
    }
}