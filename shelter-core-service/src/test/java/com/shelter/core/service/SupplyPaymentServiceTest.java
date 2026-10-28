package com.shelter.core.service;

import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.SupplyPaymentRequest;
import com.shelter.core.dto.UnpaidSupplyResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.repository.FinanceRepository;
import com.shelter.core.repository.FinanceRepository.Totals;
import com.shelter.core.repository.SupplyPaymentRepository;
import com.shelter.core.repository.SupplyPaymentRepository.LockedSupply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Оплата поставки: сумма из позиций, дата, повтор, баланс; отмена и удаление вместе с оплатой. */
class SupplyPaymentServiceTest {

    private static final long SUPPLY = 7L;
    private static final BigDecimal TOTAL = new BigDecimal("4500.00");

    private SupplyPaymentRepository repository;
    private FinanceRepository financeRepository;
    private ReceiptService receiptService;
    private BalanceGuard balanceGuard;
    private SupplyPaymentService service;

    @BeforeEach
    void setUp() {
        repository = mock(SupplyPaymentRepository.class);
        financeRepository = mock(FinanceRepository.class);
        receiptService = mock(ReceiptService.class);
        balanceGuard = mock(BalanceGuard.class);
        service = new SupplyPaymentService(repository, financeRepository, receiptService, balanceGuard, CLOCK);
        unpaid();
        summary(0, TOTAL);
        when(financeRepository.insert(any(), any(), any(), any(), any(), any())).thenReturn(90L);
        when(financeRepository.totalsUpTo(TODAY)).thenReturn(new Totals(new BigDecimal("10000"), new BigDecimal("4500")));
    }

    private void unpaid() {
        when(repository.lock(SUPPLY)).thenReturn(Optional.of(new LockedSupply(SUPPLY, TODAY.minusDays(5), "ЗооОпт", null)));
    }

    private void summary(int withoutPrice, BigDecimal total) {
        when(repository.summary(SUPPLY)).thenReturn(
                new UnpaidSupplyResponse(SUPPLY, 1L, "ЗооОпт", TODAY.minusDays(5), 2, withoutPrice, total));
    }

    @Test
    void paymentCreatesPurchaseExpenseForSupplyTotalAndLinksIt() {
        var result = service.pay(SUPPLY, new SupplyPaymentRequest(null, null));

        InOrder order = inOrder(repository, balanceGuard, financeRepository);
        order.verify(repository).lock(SUPPLY);
        order.verify(balanceGuard).ensureCanSpend(eq(TODAY), eq(TOTAL), anyString());
        order.verify(financeRepository).insert(eq(TODAY), eq(MoneyDirection.EXPENSE), eq(FinanceCategory.PURCHASE),
                eq(TOTAL), anyString(), isNull());
        order.verify(repository).setTransaction(SUPPLY, 90L);
        assertThat(result.amount()).isEqualByComparingTo(TOTAL);
        assertThat(result.balance()).isEqualByComparingTo("5500.00");
    }

    @Test
    void repeatedPaymentIsConflict() {
        when(repository.lock(SUPPLY)).thenReturn(Optional.of(new LockedSupply(SUPPLY, TODAY.minusDays(5), "ЗооОпт", 90L)));
        assertThatThrownBy(() -> service.pay(SUPPLY, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже оплачена");
        verify(financeRepository, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void paymentDateBeforeSupplyIsConflictAndInFutureIsBadRequest() {
        assertThatThrownBy(() -> service.pay(SUPPLY, new SupplyPaymentRequest(TODAY.minusDays(6), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("раньше даты поставки");
        assertThatThrownBy(() -> service.pay(SUPPLY, new SupplyPaymentRequest(TODAY.plusDays(1), null)))
                .isInstanceOf(InvalidRequestException.class);
        verify(financeRepository, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void supplyWithoutPricesOrWithZeroTotalCannotBePaid() {
        summary(1, new BigDecimal("100.00"));
        assertThatThrownBy(() -> service.pay(SUPPLY, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("нет цены");
        summary(0, BigDecimal.ZERO);
        assertThatThrownBy(() -> service.pay(SUPPLY, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("равна 0");
    }

    @Test
    void notEnoughMoneySavesNothing() {
        doThrow(new BusinessRuleException("Недостаточно средств")).when(balanceGuard)
                .ensureCanSpend(any(), any(), anyString());
        assertThatThrownBy(() -> service.pay(SUPPLY, null)).isInstanceOf(BusinessRuleException.class);
        verify(financeRepository, never()).insert(any(), any(), any(), any(), any(), any());
        verify(repository, never()).setTransaction(any(), any());
    }

    @Test
    void cancellingPaymentUnlinksSupplyAndDeletesOperation() {
        assertThatThrownBy(() -> service.cancelPayment(SUPPLY))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("не оплачена");

        when(repository.lock(SUPPLY)).thenReturn(Optional.of(new LockedSupply(SUPPLY, TODAY.minusDays(5), "ЗооОпт", 90L)));
        service.cancelPayment(SUPPLY);
        InOrder order = inOrder(repository, financeRepository);
        order.verify(repository).setTransaction(SUPPLY, null);
        order.verify(financeRepository).delete(90L);
    }

    @Test
    void paidSupplyIsDeletedTogetherWithPayment() {
        when(repository.lock(SUPPLY)).thenReturn(Optional.of(new LockedSupply(SUPPLY, TODAY.minusDays(5), "ЗооОпт", 90L)));
        service.deleteSupply(SUPPLY);
        InOrder order = inOrder(receiptService, financeRepository);
        order.verify(receiptService).deleteSupplyRecord(SUPPLY);
        order.verify(financeRepository).delete(90L);
    }

    @Test
    void supplyWithMovementsIsNotDeletedAndPaymentStays() {
        when(repository.lock(SUPPLY)).thenReturn(Optional.of(new LockedSupply(SUPPLY, TODAY.minusDays(5), "ЗооОпт", 90L)));
        doThrow(new BusinessRuleException("по его партиям уже были расход")).when(receiptService).deleteSupplyRecord(SUPPLY);
        assertThatThrownBy(() -> service.deleteSupply(SUPPLY)).isInstanceOf(BusinessRuleException.class);
        verify(financeRepository, never()).delete(any());
    }
}
