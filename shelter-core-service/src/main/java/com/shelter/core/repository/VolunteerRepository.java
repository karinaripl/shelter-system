package com.shelter.core.repository;

import com.shelter.core.dto.VolunteerResponse;
import com.shelter.model.generated.shelter.tables.records.VolunteerRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.VOLUNTEER;
import static com.shelter.model.generated.shelter.Tables.VOLUNTEER_ASSIGNMENT;

/** Волонтёры: анкеты и признак «активен». */
@Repository
public class VolunteerRepository {

    private final DSLContext dsl;

    public VolunteerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Поиск по части имени и навыков без учёта регистра; неактивные — только по запросу. */
    public List<VolunteerResponse> findPage(String name, String skills, boolean includeInactive, int page, int size) {
        return dsl.selectFrom(VOLUNTEER)
                .where(conditions(name, skills, includeInactive))
                .orderBy(VOLUNTEER.FULL_NAME, VOLUNTEER.ID)
                .limit(size)
                .offset((long) page * size)
                .fetch(VolunteerRepository::toResponse);
    }

    public long count(String name, String skills, boolean includeInactive) {
        return dsl.selectCount().from(VOLUNTEER)
                .where(conditions(name, skills, includeInactive))
                .fetchOne(0, long.class);
    }

    public Optional<VolunteerResponse> findById(Long id) {
        return dsl.selectFrom(VOLUNTEER).where(VOLUNTEER.ID.eq(id)).fetchOptional(VolunteerRepository::toResponse);
    }

    /** Блокирует строку волонтёра до конца транзакции (закрепление, деактивация). */
    public Optional<VolunteerResponse> lockById(Long id) {
        return dsl.selectFrom(VOLUNTEER).where(VOLUNTEER.ID.eq(id)).forUpdate()
                .fetchOptional(VolunteerRepository::toResponse);
    }

    /** Паспорт уже указан у другого волонтёра. */
    public boolean passportTaken(String series, String number, Long excludeId) {
        return dsl.fetchExists(dsl.selectOne().from(VOLUNTEER)
                .where(VOLUNTEER.PASSPORT_SERIES.eq(series))
                .and(VOLUNTEER.PASSPORT_NUMBER.eq(number))
                .and(excludeId == null ? DSL.noCondition() : VOLUNTEER.ID.ne(excludeId)));
    }

    public Long insert(VolunteerRecord volunteer) {
        return dsl.insertInto(VOLUNTEER)
                .set(VOLUNTEER.FULL_NAME, volunteer.getFullName())
                .set(VOLUNTEER.PHONE, volunteer.getPhone())
                .set(VOLUNTEER.EMAIL, volunteer.getEmail())
                .set(VOLUNTEER.ADDRESS, volunteer.getAddress())
                .set(VOLUNTEER.BIRTH_DATE, volunteer.getBirthDate())
                .set(VOLUNTEER.REGISTRATION_DATE, volunteer.getRegistrationDate())
                .set(VOLUNTEER.SKILLS, volunteer.getSkills())
                .set(VOLUNTEER.PASSPORT_SERIES, volunteer.getPassportSeries())
                .set(VOLUNTEER.PASSPORT_NUMBER, volunteer.getPassportNumber())
                .returning(VOLUNTEER.ID)
                .fetchOne(VOLUNTEER.ID);
    }

    public void update(Long id, VolunteerRecord volunteer) {
        dsl.update(VOLUNTEER)
                .set(VOLUNTEER.FULL_NAME, volunteer.getFullName())
                .set(VOLUNTEER.PHONE, volunteer.getPhone())
                .set(VOLUNTEER.EMAIL, volunteer.getEmail())
                .set(VOLUNTEER.ADDRESS, volunteer.getAddress())
                .set(VOLUNTEER.BIRTH_DATE, volunteer.getBirthDate())
                .set(VOLUNTEER.REGISTRATION_DATE, volunteer.getRegistrationDate())
                .set(VOLUNTEER.SKILLS, volunteer.getSkills())
                .set(VOLUNTEER.PASSPORT_SERIES, volunteer.getPassportSeries())
                .set(VOLUNTEER.PASSPORT_NUMBER, volunteer.getPassportNumber())
                .set(VOLUNTEER.UPDATED_AT, OffsetDateTime.now())
                .where(VOLUNTEER.ID.eq(id))
                .execute();
    }

    public void updateActive(Long id, boolean active) {
        dsl.update(VOLUNTEER)
                .set(VOLUNTEER.IS_ACTIVE, active)
                .set(VOLUNTEER.UPDATED_AT, OffsetDateTime.now())
                .where(VOLUNTEER.ID.eq(id))
                .execute();
    }

    public void delete(Long id) {
        dsl.deleteFrom(VOLUNTEER).where(VOLUNTEER.ID.eq(id)).execute();
    }

    /** Число закреплений волонтёра: всех или только действующих. */
    public int countAssignments(Long volunteerId, boolean activeOnly) {
        return dsl.selectCount().from(VOLUNTEER_ASSIGNMENT)
                .where(VOLUNTEER_ASSIGNMENT.VOLUNTEER_ID.eq(volunteerId))
                .and(activeOnly ? VOLUNTEER_ASSIGNMENT.END_DATE.isNull() : DSL.noCondition())
                .fetchOne(0, int.class);
    }

    /** Самая ранняя дата начала закреплений волонтёра — регистрация не может быть позже неё. */
    public Optional<LocalDate> firstAssignmentStart(Long volunteerId) {
        return Optional.ofNullable(dsl.select(DSL.min(VOLUNTEER_ASSIGNMENT.START_DATE)).from(VOLUNTEER_ASSIGNMENT)
                .where(VOLUNTEER_ASSIGNMENT.VOLUNTEER_ID.eq(volunteerId))
                .fetchOne(0, LocalDate.class));
    }

    private static Condition conditions(String name, String skills, boolean includeInactive) {
        Condition condition = includeInactive ? DSL.noCondition() : VOLUNTEER.IS_ACTIVE.isTrue();
        if (name != null) {
            condition = condition.and(VOLUNTEER.FULL_NAME.containsIgnoreCase(name));
        }
        if (skills != null) {
            condition = condition.and(VOLUNTEER.SKILLS.containsIgnoreCase(skills));
        }
        return condition;
    }

    private static VolunteerResponse toResponse(VolunteerRecord r) {
        return new VolunteerResponse(r.getId(), r.getFullName(), r.getPhone(), r.getEmail(), r.getAddress(),
                r.getBirthDate(), r.getRegistrationDate(), r.getSkills(), r.getPassportSeries(),
                r.getPassportNumber(), r.getIsActive());
    }
}
