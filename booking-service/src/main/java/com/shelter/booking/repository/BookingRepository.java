package com.shelter.booking.repository;

import com.shelter.model.generated.booking.tables.records.BookingRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.shelter.model.generated.booking.tables.Booking.BOOKING;

@Repository
public class BookingRepository {

    private final DSLContext dsl;

    public BookingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public BookingRecord insert(Long userId, Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        return dsl.insertInto(BOOKING)
                .set(BOOKING.USER_ID, userId)
                .set(BOOKING.ROOM_ID, roomId)
                .set(BOOKING.START_TIME, startTime)
                .set(BOOKING.END_TIME, endTime)
                .set(BOOKING.STATUS, "PENDING")
                .returning()
                .fetchOne();
    }

    public List<BookingRecord> findByUserId(Long userId) {
        return dsl.selectFrom(BOOKING)
                .where(BOOKING.USER_ID.eq(userId))
                .orderBy(BOOKING.START_TIME.desc())
                .fetch();
    }

    public BookingRecord findById(Long id) {
        return dsl.selectFrom(BOOKING)
                .where(BOOKING.ID.eq(id))
                .fetchOne();
    }

    public void cancel(Long id) {
        dsl.update(BOOKING)
                .set(BOOKING.STATUS, "CANCELLED")
                .where(BOOKING.ID.eq(id))
                .execute();
    }
}