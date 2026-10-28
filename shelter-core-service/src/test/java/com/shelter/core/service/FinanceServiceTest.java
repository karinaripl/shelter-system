package com.shelter.core.service;

import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.ManualTransactionRequest;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.TransactionResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.repository.FinanceRepository;
import com.shelter.core.repository.FinanceRepository.Totals;
import com.shelter.core.repository.FinanceRepository.TransactionRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Журнал: вручную только OTHER; расход и удаление прихода проходят проверку баланса. */
class FinanceServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("1200.00");

    private FinanceRepository repository;
    private BalanceGuard balanceGuard;
    private FinanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(FinanceRepository.class);
        balanceGuard = mock(BalanceGuard.class);
        service = new FinanceService(repository, balanceGuard, CLOCK);
        when(repository.insert(any(), any(), any(), any(), any(), any())).thenReturn(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(new TransactionResponse(10L, TODAY, MoneyDirection.EXPENSE,
                FinanceCategory.OTHER, AMOUNT, "Ремонт клетки", null, null, null, null, null)));
    }

    private static ManualTransactionRequest manual(MoneyDirection direction, FinanceCategory category) {
        return new ManualTransactionRequest(direction, category, AMOUNT, null, " Ремонт клетки ");
    }

    @ParameterizedTest
    @EnumSource(value = FinanceCategory.class, names = {"DONATION", "SALARY", "PURCHASE"})
    void documentCategoriesAreNotCreatedManually(FinanceCategory category) {
        assertThatThrownBy(() -> service.createManual(manual(MoneyDirection.EXPENSE, category)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(category.name());
        verify(repository, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void manualExpenseIsCheckedAgainstBalanceOnItsDate() {
        service.createManual(manual(MoneyDirection.EXPENSE, null));
        verify(balanceGuard).ensureCanSpend(eq(TODAY), eq(AMOUNT), anyString());
        verify(repository).insert(TODAY, MoneyDirection.EXPENSE, FinanceCategory.OTHER, AMOUNT, "Ремонт клетки", null);
    }

    @Test
    void notEnoughMoneyForExpenseSavesNothing() {
        doThrow(new BusinessRuleException("Недостаточно средств")).when(balanceGuard)
                .ensureCanSpend(any(), any(), anyString());
        assertThatThrownBy(() -> service.createManual(manual(MoneyDirection.EXPENSE, FinanceCategory.OTHER)))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void manualIncomeNeedsNoBalanceCheck() {
        service.createManual(manual(MoneyDirection.INCOME, null));
        verify(balanceGuard, never()).ensureCanSpend(any(), any(), anyString());
        verify(repository).insert(eq(TODAY), eq(MoneyDirection.INCOME), eq(FinanceCategory.OTHER), eq(AMOUNT),
                anyString(), isNull());
    }

    @Test
    void futureDateIsBadRequest() {
        var request = new ManualTransactionRequest(MoneyDirection.INCOME, null, AMOUNT, TODAY.plusDays(1), "Продажа");
        assertThatThrownBy(() -> service.createManual(request)).isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest
    @EnumSource(value = FinanceCategory.class, names = {"DONATION", "SALARY", "PURCHASE"})
    void documentOperationsAreNotDeletedManually(FinanceCategory category) {
        when(repository.lockById(5L)).thenReturn(Optional.of(
                new TransactionRow(5L, TODAY, MoneyDirection.EXPENSE, category, AMOUNT, null)));
        assertThatThrownBy(() -> service.deleteManual(5L)).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void deletingIncomeIsCheckedAgainstBalanceButDeletingExpenseIsNot() {
        when(repository.lockById(5L)).thenReturn(Optional.of(
                new TransactionRow(5L, TODAY.minusDays(3), MoneyDirection.INCOME, FinanceCategory.OTHER, AMOUNT, null)));
        service.deleteManual(5L);
        verify(balanceGuard).ensureCanSpend(eq(TODAY.minusDays(3)), eq(AMOUNT), anyString());
        verify(repository).delete(5L);

        when(repository.lockById(6L)).thenReturn(Optional.of(
                new TransactionRow(6L, TODAY, MoneyDirection.EXPENSE, FinanceCategory.OTHER, AMOUNT, null)));
        service.deleteManual(6L);
        verify(repository).delete(6L);
        verify(balanceGuard, never()).ensureCanSpend(eq(TODAY), any(), anyString());
    }

    @Test
    void balanceIsIncomeMinusExpenseUpToDate() {
        when(repository.totalsUpTo(TODAY)).thenReturn(new Totals(new BigDecimal("1000"), new BigDecimal("250.5")));
        var balance = service.balance(null);
        assertThat(balance.date()).isEqualTo(TODAY);
        assertThat(balance.balance()).isEqualByComparingTo("749.50");
        assertThat(balance.balance().scale()).isEqualTo(2);
    }
}
