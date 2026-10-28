package com.shelter.core.repository;

import com.shelter.core.dto.BenefactorBrief;
import com.shelter.core.dto.DonationType;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.BENEFACTOR;
import static com.shelter.model.generated.shelter.Tables.DONATION;
import static com.shelter.model.generated.shelter.Tables.FINANCE_TRANSACTION;

/** Пожертвования обоих видов для админ-панели. Сумма денежного — в операции прихода (finance_transaction). */
@Repository
public class DonationRepository {

    private final DSLContext dsl;

    public DonationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Фильтр списка; любое поле может быть null. */
    public record Filter(LocalDate from, LocalDate to, DonationType type, Long benefactorId) {
    }

    /** Пожертвование без товаров. amount и transactionId — только у денежного. */
    public record DonationHeader(Long id, DonationType type, BenefactorBrief benefactor, LocalDate donationDate,
                                 BigDecimal amount, Long transactionId, String notes, OffsetDateTime createdAt) {
    }

    /** Итоги по благотворителю за период; benefactor = null — анонимные пожертвования. */
    public record BenefactorTotal(BenefactorBrief benefactor, int moneyCount, BigDecimal moneyTotal, int goodsCount,
                                  LocalDate lastDonation) {
    }

    public Long insertMoneyDonation(Long benefactorId, LocalDate date, String notes) {
        return dsl.insertInto(DONATION)
                .set(DONATION.BENEFACTOR_ID, benefactorId)
                .set(DONATION.DONATION_DATE, date)
                .set(DONATION.DONATION_TYPE, DonationType.MONEY.name())
                .set(DONATION.NOTES, notes)
                .returning(DONATION.ID)
                .fetchOne(DONATION.ID);
    }

    public List<DonationHeader> findPage(Filter filter, int page, int size) {
        return select().where(conditions(filter))
                .orderBy(DONATION.DONATION_DATE.desc(), DONATION.ID.desc())
                .limit(size).offset((long) page * size)
                .fetch(DonationRepository::toHeader);
    }

    public long count(Filter filter) {
        return dsl.fetchCount(DONATION, conditions(filter));
    }

    public Optional<DonationHeader> findById(Long id) {
        return select().where(DONATION.ID.eq(id)).fetchOptional(DonationRepository::toHeader);
    }

    /** Блокирует пожертвование до конца транзакции. */
    public Optional<DonationHeader> lockById(Long id) {
        if (dsl.select(DONATION.ID).from(DONATION).where(DONATION.ID.eq(id)).forUpdate().fetchOptional().isEmpty()) {
            return Optional.empty();
        }
        return findById(id);
    }

    public void delete(Long id) {
        dsl.deleteFrom(DONATION).where(DONATION.ID.eq(id)).execute();
    }

    public List<BenefactorTotal> totalsByBenefactor(LocalDate from, LocalDate to) {
        var moneyCount = DSL.count(FINANCE_TRANSACTION.ID);
        var moneyTotal = DSL.coalesce(DSL.sum(FINANCE_TRANSACTION.AMOUNT), BigDecimal.ZERO);
        var goodsCount = DSL.count().filterWhere(DONATION.DONATION_TYPE.eq(DonationType.GOODS.name()));
        var last = DSL.max(DONATION.DONATION_DATE);
        return dsl.select(BENEFACTOR.ID, BENEFACTOR.FULL_NAME, moneyCount, moneyTotal, goodsCount, last)
                .from(DONATION)
                .leftJoin(BENEFACTOR).on(BENEFACTOR.ID.eq(DONATION.BENEFACTOR_ID))
                .leftJoin(FINANCE_TRANSACTION).on(FINANCE_TRANSACTION.DONATION_ID.eq(DONATION.ID))
                .where(DONATION.DONATION_DATE.between(from, to))
                .groupBy(BENEFACTOR.ID, BENEFACTOR.FULL_NAME)
                .orderBy(moneyTotal.desc(), BENEFACTOR.FULL_NAME.asc().nullsLast())
                .fetch(r -> new BenefactorTotal(
                        r.value1() == null ? null : new BenefactorBrief(r.value1(), r.value2()),
                        r.value3(), r.value4(), r.value5(), r.value6()));
    }

    private SelectOnConditionStep<? extends Record> select() {
        return dsl.select(DONATION.ID, DONATION.DONATION_TYPE, DONATION.DONATION_DATE, DONATION.NOTES,
                        DONATION.CREATED_AT, BENEFACTOR.ID, BENEFACTOR.FULL_NAME, FINANCE_TRANSACTION.ID,
                        FINANCE_TRANSACTION.AMOUNT)
                .from(DONATION)
                .leftJoin(BENEFACTOR).on(BENEFACTOR.ID.eq(DONATION.BENEFACTOR_ID))
                .leftJoin(FINANCE_TRANSACTION).on(FINANCE_TRANSACTION.DONATION_ID.eq(DONATION.ID));
    }

    private static Condition conditions(Filter filter) {
        List<Condition> conditions = new ArrayList<>();
        if (filter.from() != null) {
            conditions.add(DONATION.DONATION_DATE.ge(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add(DONATION.DONATION_DATE.le(filter.to()));
        }
        if (filter.type() != null) {
            conditions.add(DONATION.DONATION_TYPE.eq(filter.type().name()));
        }
        if (filter.benefactorId() != null) {
            conditions.add(DONATION.BENEFACTOR_ID.eq(filter.benefactorId()));
        }
        return DSL.and(conditions);
    }

    private static DonationHeader toHeader(Record r) {
        return new DonationHeader(
                r.get(DONATION.ID),
                DonationType.valueOf(r.get(DONATION.DONATION_TYPE)),
                r.get(BENEFACTOR.ID) == null ? null : new BenefactorBrief(r.get(BENEFACTOR.ID), r.get(BENEFACTOR.FULL_NAME)),
                r.get(DONATION.DONATION_DATE),
                r.get(FINANCE_TRANSACTION.AMOUNT),
                r.get(FINANCE_TRANSACTION.ID),
                r.get(DONATION.NOTES),
                r.get(DONATION.CREATED_AT));
    }
}
