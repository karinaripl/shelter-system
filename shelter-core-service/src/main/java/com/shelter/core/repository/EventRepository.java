package com.shelter.core.repository;

import com.shelter.core.dto.AnimalEventItem;
import com.shelter.core.dto.EventEffectivenessItem;
import com.shelter.core.dto.ParticipantResponse;
import com.shelter.core.dto.PublicEventItem;
import com.shelter.core.dto.RemovedParticipant;
import com.shelter.core.dto.AnimalStatusCode;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;
import static com.shelter.model.generated.shelter.Tables.ANIMAL_STATUS;
import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.EVENT;
import static com.shelter.model.generated.shelter.Tables.EVENT_PARTICIPATION;
import static com.shelter.model.generated.shelter.Tables.POSITION;

/** Мероприятия, участие животных, анонсы и отчёт об эффективности. */
@Repository
public class EventRepository {

    private static final Field<Integer> PARTICIPANT_COUNT = DSL.field(
            DSL.selectCount().from(EVENT_PARTICIPATION).where(EVENT_PARTICIPATION.EVENT_ID.eq(EVENT.ID)))
            .as("participant_count");

    private final DSLContext dsl;

    public EventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- мероприятия ----------

    /** Мероприятия за период (границы включительно, любая может быть не задана), поздние первыми. */
    public List<EventView> findPage(LocalDate from, LocalDate to, int page, int size) {
        return selectView()
                .where(period(from, to))
                .orderBy(EVENT.EVENT_DATE.desc(), EVENT.ID.desc())
                .limit(size)
                .offset((long) page * size)
                .fetch(EventRepository::toView);
    }

    public long count(LocalDate from, LocalDate to) {
        return dsl.selectCount().from(EVENT).where(period(from, to)).fetchOne(0, long.class);
    }

    public Optional<EventView> findById(Long id) {
        return selectView().where(EVENT.ID.eq(id)).fetchOptional(EventRepository::toView);
    }

    /** Блокирует строку мероприятия до конца транзакции (перенос, участие). */
    public Optional<EventView> lockById(Long id) {
        boolean exists = dsl.select(EVENT.ID).from(EVENT).where(EVENT.ID.eq(id)).forUpdate()
                .fetchOptional().isPresent();
        return exists ? findById(id) : Optional.empty();
    }

    public Long insert(String name, LocalDate eventDate, String location, Long organizerId, String notes) {
        return dsl.insertInto(EVENT)
                .set(EVENT.NAME, name)
                .set(EVENT.EVENT_DATE, eventDate)
                .set(EVENT.LOCATION, location)
                .set(EVENT.ORGANIZER_ID, organizerId)
                .set(EVENT.NOTES, notes)
                .returning(EVENT.ID)
                .fetchOne(EVENT.ID);
    }

    public void update(Long id, String name, LocalDate eventDate, String location, Long organizerId, String notes) {
        dsl.update(EVENT)
                .set(EVENT.NAME, name)
                .set(EVENT.EVENT_DATE, eventDate)
                .set(EVENT.LOCATION, location)
                .set(EVENT.ORGANIZER_ID, organizerId)
                .set(EVENT.NOTES, notes)
                .set(EVENT.UPDATED_AT, OffsetDateTime.now())
                .where(EVENT.ID.eq(id))
                .execute();
    }

    /** Удаляет мероприятие; участие удаляется каскадом. */
    public void delete(Long id) {
        dsl.deleteFrom(EVENT).where(EVENT.ID.eq(id)).execute();
    }

    /** Будущие мероприятия (с сегодняшнего дня) для публичной страницы, ближайшие первыми. */
    public List<PublicEventItem> findUpcoming(LocalDate today, int page, int size) {
        return dsl.select(EVENT.ID, EVENT.NAME, EVENT.EVENT_DATE, EVENT.LOCATION)
                .from(EVENT)
                .where(EVENT.EVENT_DATE.ge(today))
                .orderBy(EVENT.EVENT_DATE, EVENT.ID)
                .limit(size)
                .offset((long) page * size)
                .fetch(r -> new PublicEventItem(r.get(EVENT.ID), r.get(EVENT.NAME), r.get(EVENT.EVENT_DATE),
                        r.get(EVENT.LOCATION)));
    }

    public long countUpcoming(LocalDate today) {
        return dsl.selectCount().from(EVENT).where(EVENT.EVENT_DATE.ge(today)).fetchOne(0, long.class);
    }

    // ---------- участие ----------

    public List<ParticipantResponse> findParticipants(Long eventId) {
        return dsl.select(ANIMAL.ID, ANIMAL.NAME, ANIMAL_STATUS.CODE, ANIMAL_STATUS.NAME, ANIMAL.DEPARTURE_DATE,
                        EVENT_PARTICIPATION.CONDITION_AFTER)
                .from(EVENT_PARTICIPATION)
                .join(ANIMAL).on(ANIMAL.ID.eq(EVENT_PARTICIPATION.ANIMAL_ID))
                .join(ANIMAL_STATUS).on(ANIMAL_STATUS.ID.eq(ANIMAL.STATUS_ID))
                .where(EVENT_PARTICIPATION.EVENT_ID.eq(eventId))
                .orderBy(ANIMAL.NAME.asc().nullsLast(), ANIMAL.ID)
                .fetch(r -> new ParticipantResponse(r.get(ANIMAL.ID), r.get(ANIMAL.NAME), r.get(ANIMAL_STATUS.CODE),
                        r.get(ANIMAL_STATUS.NAME), r.get(ANIMAL.DEPARTURE_DATE),
                        r.get(EVENT_PARTICIPATION.CONDITION_AFTER)));
    }

    public boolean participates(Long eventId, Long animalId) {
        return dsl.fetchExists(dsl.selectOne().from(EVENT_PARTICIPATION)
                .where(EVENT_PARTICIPATION.EVENT_ID.eq(eventId))
                .and(EVENT_PARTICIPATION.ANIMAL_ID.eq(animalId)));
    }

    public void addParticipant(Long eventId, Long animalId) {
        dsl.insertInto(EVENT_PARTICIPATION)
                .set(EVENT_PARTICIPATION.EVENT_ID, eventId)
                .set(EVENT_PARTICIPATION.ANIMAL_ID, animalId)
                .execute();
    }

    public void updateCondition(Long eventId, Long animalId, String conditionAfter) {
        dsl.update(EVENT_PARTICIPATION)
                .set(EVENT_PARTICIPATION.CONDITION_AFTER, conditionAfter)
                .where(EVENT_PARTICIPATION.EVENT_ID.eq(eventId))
                .and(EVENT_PARTICIPATION.ANIMAL_ID.eq(animalId))
                .execute();
    }

    public void removeParticipant(Long eventId, Long animalId) {
        dsl.deleteFrom(EVENT_PARTICIPATION)
                .where(EVENT_PARTICIPATION.EVENT_ID.eq(eventId))
                .and(EVENT_PARTICIPATION.ANIMAL_ID.eq(animalId))
                .execute();
    }

    /** Участники, выбывшие раньше даты (правило прототипа trg_event_date_change). */
    public List<RemovedParticipant> findDepartedBefore(Long eventId, LocalDate date) {
        return dsl.select(ANIMAL.ID, ANIMAL.NAME, ANIMAL.DEPARTURE_DATE)
                .from(EVENT_PARTICIPATION)
                .join(ANIMAL).on(ANIMAL.ID.eq(EVENT_PARTICIPATION.ANIMAL_ID))
                .where(EVENT_PARTICIPATION.EVENT_ID.eq(eventId))
                .and(ANIMAL.DEPARTURE_DATE.lt(date))
                .orderBy(ANIMAL.NAME.asc().nullsLast(), ANIMAL.ID)
                .fetch(r -> new RemovedParticipant(r.get(ANIMAL.ID), r.get(ANIMAL.NAME), r.get(ANIMAL.DEPARTURE_DATE)));
    }

    /** Клички участников, поступивших в приют позже даты: мероприятие нельзя перенести раньше их поступления. */
    public List<String> findArrivedAfter(Long eventId, LocalDate date) {
        return dsl.select(DSL.coalesce(ANIMAL.NAME, DSL.inline("id=").concat(ANIMAL.ID)))
                .from(EVENT_PARTICIPATION)
                .join(ANIMAL).on(ANIMAL.ID.eq(EVENT_PARTICIPATION.ANIMAL_ID))
                .where(EVENT_PARTICIPATION.EVENT_ID.eq(eventId))
                .and(ANIMAL.ARRIVAL_DATE.gt(date))
                .orderBy(ANIMAL.ID)
                .fetch(0, String.class);
    }

    /** Мероприятия животного, последние первыми. */
    public List<AnimalEventItem> findByAnimal(Long animalId) {
        return dsl.select(EVENT.ID, EVENT.NAME, EVENT.EVENT_DATE, EVENT.LOCATION, EVENT_PARTICIPATION.CONDITION_AFTER)
                .from(EVENT_PARTICIPATION)
                .join(EVENT).on(EVENT.ID.eq(EVENT_PARTICIPATION.EVENT_ID))
                .where(EVENT_PARTICIPATION.ANIMAL_ID.eq(animalId))
                .orderBy(EVENT.EVENT_DATE.desc(), EVENT.ID.desc())
                .fetch(r -> new AnimalEventItem(r.get(EVENT.ID), r.get(EVENT.NAME), r.get(EVENT.EVENT_DATE),
                        r.get(EVENT.LOCATION), r.get(EVENT_PARTICIPATION.CONDITION_AFTER)));
    }

    // ---------- отчёт ----------

    /**
     * Эффективность мероприятий за период: участники и пристроенные впоследствии — статус с кодом ADOPTED
     * и дата выбытия не раньше даты мероприятия (представление прототипа v_event_adoption_rate).
     */
    public List<EventEffectivenessItem> effectiveness(LocalDate from, LocalDate to) {
        Field<Integer> participants = DSL.count(EVENT_PARTICIPATION.ANIMAL_ID).as("participants");
        Field<Integer> adopted = DSL.count().filterWhere(ANIMAL_STATUS.CODE.eq(AnimalStatusCode.ADOPTED.name())
                .and(ANIMAL.DEPARTURE_DATE.ge(EVENT.EVENT_DATE))).as("adopted");
        return dsl.select(EVENT.ID, EVENT.NAME, EVENT.EVENT_DATE, EVENT.LOCATION, participants, adopted)
                .from(EVENT)
                .leftJoin(EVENT_PARTICIPATION).on(EVENT_PARTICIPATION.EVENT_ID.eq(EVENT.ID))
                .leftJoin(ANIMAL).on(ANIMAL.ID.eq(EVENT_PARTICIPATION.ANIMAL_ID))
                .leftJoin(ANIMAL_STATUS).on(ANIMAL_STATUS.ID.eq(ANIMAL.STATUS_ID))
                .where(EVENT.EVENT_DATE.between(from, to))
                .groupBy(EVENT.ID, EVENT.NAME, EVENT.EVENT_DATE, EVENT.LOCATION)
                .orderBy(EVENT.EVENT_DATE, EVENT.ID)
                .fetch(r -> {
                    int count = r.get(participants);
                    int adoptedCount = r.get(adopted);
                    return new EventEffectivenessItem(r.get(EVENT.ID), r.get(EVENT.NAME), r.get(EVENT.EVENT_DATE),
                            r.get(EVENT.LOCATION), count, adoptedCount, percent(adoptedCount, count));
                });
    }

    /** Доля в процентах с одним знаком; null, если делить не на что. */
    public static BigDecimal percent(int part, int whole) {
        if (whole == 0) {
            return null;
        }
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    // ---------- вспомогательное ----------

    private static Condition period(LocalDate from, LocalDate to) {
        Condition condition = DSL.noCondition();
        if (from != null) {
            condition = condition.and(EVENT.EVENT_DATE.ge(from));
        }
        if (to != null) {
            condition = condition.and(EVENT.EVENT_DATE.le(to));
        }
        return condition;
    }

    private SelectOnConditionStep<? extends Record> selectView() {
        return dsl.select(EVENT.ID, EVENT.NAME, EVENT.EVENT_DATE, EVENT.LOCATION, EVENT.ORGANIZER_ID,
                        EMPLOYEE.FULL_NAME, POSITION.NAME, EVENT.NOTES, PARTICIPANT_COUNT)
                .from(EVENT)
                .join(EMPLOYEE).on(EMPLOYEE.ID.eq(EVENT.ORGANIZER_ID))
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID));
    }

    private static EventView toView(Record r) {
        return new EventView(r.get(EVENT.ID), r.get(EVENT.NAME), r.get(EVENT.EVENT_DATE), r.get(EVENT.LOCATION),
                r.get(EVENT.ORGANIZER_ID), r.get(EMPLOYEE.FULL_NAME), r.get(POSITION.NAME), r.get(EVENT.NOTES),
                r.get(PARTICIPANT_COUNT));
    }
}
