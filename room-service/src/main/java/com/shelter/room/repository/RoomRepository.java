package com.shelter.room.repository;

import com.shelter.model.generated.room.tables.records.RoomsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.shelter.model.generated.room.tables.Rooms.ROOMS;

@Repository
public class RoomRepository {

    private final DSLContext dsl;

    public RoomRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public RoomsRecord insert(String name, Integer capacity, String description, String location) {
        return dsl.insertInto(ROOMS)
                .set(ROOMS.NAME, name)
                .set(ROOMS.CAPACITY, capacity)
                .set(ROOMS.DESCRIPTION, description)
                .set(ROOMS.LOCATION, location)
                .set(ROOMS.IS_ACTIVE, true)
                .returning()
                .fetchOne();
    }

    public List<RoomsRecord> findAllActive() {
        return dsl.selectFrom(ROOMS)
                .where(ROOMS.IS_ACTIVE.isTrue())
                .fetch();
    }

    public RoomsRecord findById(Long id) {
        return dsl.selectFrom(ROOMS)
                .where(ROOMS.ID.eq(id))
                .fetchOne();
    }

    public RoomsRecord findActiveById(Long id) {
        return dsl.selectFrom(ROOMS)
                .where(ROOMS.ID.eq(id))
                .and(ROOMS.IS_ACTIVE.isTrue())
                .fetchOne();
    }

    public int update(Long id, String name, Integer capacity, String description, String location) {
        return dsl.update(ROOMS)
                .set(ROOMS.NAME, name)
                .set(ROOMS.CAPACITY, capacity)
                .set(ROOMS.DESCRIPTION, description)
                .set(ROOMS.LOCATION, location)
                .where(ROOMS.ID.eq(id))
                .execute();
    }

    public int deactivate(Long id) {
        return dsl.update(ROOMS)
                .set(ROOMS.IS_ACTIVE, false)
                .where(ROOMS.ID.eq(id))
                .execute();
    }
}