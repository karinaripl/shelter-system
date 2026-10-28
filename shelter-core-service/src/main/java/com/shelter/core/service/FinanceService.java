package com.shelter.core.service;

import com.shelter.core.dto.BalanceResponse;
import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.ManualTransactionRequest;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.TransactionJournalResponse;
import com.shelter.core.dto.TransactionResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.FinanceRepository;
import com.shelter.core.repository.FinanceRepository.Filter;
import com.shelter.core.repository.FinanceRepository.Totals;
import com.shelter.core.repository.FinanceRepository.TransactionRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Журнал денежных операций и баланс (админ-панель, только ADMIN). Вручную создаются и удаляются только операции
 * категории OTHER; пожертвования, зарплаты и закупки — через свои документы. Нарушение правила — 409, ошибка данных — 400.
 */
@Service
public class FinanceService {

    private final FinanceRepository repository;
    private final BalanceGuard balanceGuard;
    private final Clock clock;

    public FinanceService(FinanceRepository repository, BalanceGuard balanceGuard, Clock clock) {
        this.repository = repository;
        this.balanceGuard = balanceGuard;
        this.clock = clock;
    }

    public TransactionJournalResponse findJournal(LocalDate from, LocalDate to, MoneyDirection direction,
                                                  FinanceCategory category, int page, int size) {
        Paging.validate(page, size);
        ReceiptService.checkPeriod(from, to);
        Filter filter = new Filter(from, to, direction, category);
        List<TransactionResponse> content = repository.findPage(filter, page, size);
        PageResponse<TransactionResponse> pageResponse = PageResponse.of(content, page, size, repository.count(filter));
        Totals totals = repository.totals(filter);
        return new TransactionJournalResponse(pageResponse.content(), pageResponse.page(), pageResponse.size(),
                pageResponse.totalElements(), pageResponse.totalPages(), Money.of(totals.income()),
                Money.of(totals.expense()), Money.of(totals.balance()));
    }

    public TransactionResponse findById(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.transaction(id));
    }

    /** Баланс на конец дня (по умолчанию — сегодня). */
    public BalanceResponse balance(LocalDate date) {
        LocalDate day = date != null ? date : today();
        Totals totals = repository.totalsUpTo(day);
        return new BalanceResponse(day, Money.of(totals.income()), Money.of(totals.expense()), Money.of(totals.balance()));
    }

    /** Ручная операция OTHER: дата не в будущем; расход не уводит баланс в минус ни на свою дату, ни позже. */
    @Transactional
    public TransactionResponse createManual(ManualTransactionRequest request) {
        if (request.category() != null && request.category() != FinanceCategory.OTHER) {
            throw new BusinessRuleException(documentOnly(request.category()));
        }
        LocalDate date = request.transactionDate() != null ? request.transactionDate() : today();
        if (date.isAfter(today())) {
            throw new InvalidRequestException("Дата операции не может быть в будущем");
        }
        if (request.direction() == MoneyDirection.EXPENSE) {
            balanceGuard.ensureCanSpend(date, request.amount(), "расхода");
        }
        Long id = repository.insert(date, request.direction(), FinanceCategory.OTHER, request.amount(),
                request.description().trim(), null);
        return findById(id);
    }

    /** Удаляется только ручная операция (OTHER); удаление прихода не должно увести баланс в минус. */
    @Transactional
    public void deleteManual(Long id) {
        TransactionRow row = repository.lockById(id).orElseThrow(() -> NotFoundException.transaction(id));
        if (row.category() != FinanceCategory.OTHER) {
            throw new BusinessRuleException(documentOnly(row.category()));
        }
        if (row.direction() == MoneyDirection.INCOME) {
            balanceGuard.ensureCanSpend(row.date(), row.amount(), "удаления прихода");
        }
        repository.delete(id);
    }

    static String documentOnly(FinanceCategory category) {
        return switch (category) {
            case DONATION -> "Операции категории DONATION создаются и удаляются только вместе с денежным "
                    + "пожертвованием (/admin/donations)";
            case SALARY -> "Операции категории SALARY создаются и отменяются только начислением зарплаты "
                    + "за месяц (/admin/salaries)";
            case PURCHASE -> "Операции категории PURCHASE создаются и отменяются только оплатой поставки "
                    + "(/admin/supplies/{id}/payment)";
            case OTHER -> "Операция категории OTHER";
        };
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
