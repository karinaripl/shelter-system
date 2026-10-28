package com.shelter.core.service;

import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.FinanceRepository;
import com.shelter.core.repository.FinanceRepository.Totals;
import com.shelter.core.repository.SalaryRepository;
import com.shelter.core.service.SalaryCalculator.Employment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Начисление за месяц: один раз, всё или ничего, дата операции — день начисления, не за будущий месяц. */
class SalaryServiceTest {

    private static final YearMonth MONTH = YearMonth.from(TODAY);
    private static final LocalDate PERIOD = MONTH.atDay(1);
    private static final LocalDate MONTH_END = MONTH.atEndOfMonth();
    /** Дата операций — сегодня, а не последний день месяца, за который платят. */
    private static final LocalDate PAYMENT_DATE = TODAY;

    private SalaryRepository repository;
    private FinanceRepository financeRepository;
    private BalanceGuard balanceGuard;
    private SalaryService service;

    @BeforeEach
    void setUp() {
        repository = mock(SalaryRepository.class);
        financeRepository = mock(FinanceRepository.class);
        balanceGuard = mock(BalanceGuard.class);
        service = new SalaryService(repository, financeRepository, balanceGuard, CLOCK);
        when(repository.employedIn(PERIOD, MONTH_END)).thenReturn(List.of(
                new Employment(1L, "Лебедев", "Ветеринар", new BigDecimal("62000.00"), 40, LocalDate.of(2020, 1, 1), null),
                // Принят 20-го: 12 из 31 дня, половина ставки
                new Employment(2L, "Новикова", "Санитар", new BigDecimal("31000.00"), 20, MONTH.atDay(20), null),
                new Employment(3L, "Без часов", "Санитар", new BigDecimal("31000.00"), 0, LocalDate.of(2020, 1, 1), null)));
        when(financeRepository.insert(any(), any(), any(), any(), any(), any())).thenReturn(100L, 101L);
        when(financeRepository.availableFrom(PAYMENT_DATE)).thenReturn(new BigDecimal("100000"));
        when(repository.paymentDate(any())).thenReturn(java.util.Optional.empty());
        when(financeRepository.totalsUpTo(TODAY)).thenReturn(new Totals(new BigDecimal("100000"), BigDecimal.ZERO));
    }

    @Test
    void previewShowsDaysAndSkipsZeroAmountsAndSavesNothing() {
        var preview = service.preview(MONTH);

        assertThat(preview.periodMonth()).isEqualTo(MONTH);
        assertThat(preview.paymentDate()).isEqualTo(PAYMENT_DATE);
        assertThat(preview.alreadyAccrued()).isFalse();
        assertThat(preview.accruedOn()).isNull();
        assertThat(preview.employees()).extracting(l -> l.workedDays()).containsExactly("31 из 31 дней", "12 из 31 дней");
        assertThat(preview.employees()).extracting(l -> l.amount().toPlainString()).containsExactly("62000.00", "6000.00");
        assertThat(preview.zeroAmount()).extracting(l -> l.fullName()).containsExactly("Без часов");
        assertThat(preview.total()).isEqualByComparingTo("68000.00");
        assertThat(preview.enough()).isTrue();
        verify(financeRepository, never()).insert(any(), any(), any(), any(), any(), any());
        verify(repository, never()).insert(any(), any(), any());
    }

    @Test
    void accrualCreatesSalaryExpenseForEachEmployeeDatedToday() {
        var result = service.accrue(MONTH);

        InOrder order = inOrder(balanceGuard, repository, financeRepository);
        order.verify(balanceGuard).lock();
        order.verify(repository).existsForMonth(PERIOD);
        order.verify(balanceGuard).ensureCanSpend(eq(PAYMENT_DATE), eq(new BigDecimal("68000.00")), anyString());
        verify(financeRepository).insert(eq(PAYMENT_DATE), eq(MoneyDirection.EXPENSE), eq(FinanceCategory.SALARY),
                eq(new BigDecimal("62000.00")), anyString(), isNull());
        verify(repository).insert(100L, 1L, PERIOD);
        verify(repository).insert(101L, 2L, PERIOD);
        verify(repository, never()).insert(any(), eq(3L), any());
        assertThat(result.total()).isEqualByComparingTo("68000.00");
        assertThat(result.periodMonth()).isEqualTo(MONTH);
        assertThat(result.paymentDate()).isEqualTo(TODAY);
        assertThat(result.employees()).extracting(l -> l.transactionId()).containsExactly(100L, 101L);
    }

    @Test
    void secondAccrualForSameMonthIsConflict() {
        when(repository.existsForMonth(PERIOD)).thenReturn(true);
        assertThatThrownBy(() -> service.accrue(MONTH))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже начислена");
        verify(financeRepository, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void notEnoughMoneyForWholeAccrualSavesNothing() {
        doThrow(new BusinessRuleException("Недостаточно средств")).when(balanceGuard)
                .ensureCanSpend(any(), any(), anyString());
        assertThatThrownBy(() -> service.accrue(MONTH)).isInstanceOf(BusinessRuleException.class);
        verify(financeRepository, never()).insert(any(), any(), any(), any(), any(), any());
        verify(repository, never()).insert(any(), any(), any());
    }

    @Test
    void futureMonthIsBadRequestButCurrentAndPastAreAllowed() {
        assertThatThrownBy(() -> service.accrue(MONTH.plusMonths(1))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.preview(MONTH.plusMonths(1))).isInstanceOf(InvalidRequestException.class);
        service.accrue(MONTH);
        verify(repository, times(2)).insert(any(), any(), eq(PERIOD));
    }

    @Test
    void monthWithoutEmployeesIsConflict() {
        YearMonth past = MONTH.minusYears(10);
        when(repository.employedIn(past.atDay(1), past.atEndOfMonth())).thenReturn(List.of());
        assertThatThrownBy(() -> service.accrue(past))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("некому");
    }

    @Test
    void pastMonthIsPaidTodayAndPreviewShowsWhenItWasAccrued() {
        YearMonth past = MONTH.minusMonths(1);
        when(repository.employedIn(past.atDay(1), past.atEndOfMonth())).thenReturn(List.of(
                new Employment(1L, "Лебедев", "Ветеринар", new BigDecimal("62000.00"), 40, LocalDate.of(2020, 1, 1), null)));
        when(financeRepository.availableFrom(TODAY)).thenReturn(new BigDecimal("100000"));

        var result = service.accrue(past);

        assertThat(result.periodMonth()).isEqualTo(past);
        assertThat(result.paymentDate()).isEqualTo(TODAY);
        verify(balanceGuard).ensureCanSpend(eq(TODAY), eq(new BigDecimal("62000.00")), anyString());
        verify(financeRepository).insert(eq(TODAY), eq(MoneyDirection.EXPENSE), eq(FinanceCategory.SALARY), any(),
                anyString(), isNull());
        verify(repository).insert(100L, 1L, past.atDay(1));

        when(repository.paymentDate(past.atDay(1))).thenReturn(java.util.Optional.of(TODAY.minusDays(3)));
        var preview = service.preview(past);
        assertThat(preview.alreadyAccrued()).isTrue();
        assertThat(preview.accruedOn()).isEqualTo(TODAY.minusDays(3));
        assertThat(preview.paymentDate()).isEqualTo(TODAY);
    }

    @Test
    void cancellationIsWholeMonthAndNothingToCancelIsNotFound() {
        when(repository.deleteMonth(PERIOD)).thenReturn(2);
        service.cancel(MONTH);
        verify(repository).deleteMonth(PERIOD);

        when(repository.deleteMonth(PERIOD)).thenReturn(0);
        assertThatThrownBy(() -> service.cancel(MONTH)).isInstanceOf(NotFoundException.class);
    }
}
