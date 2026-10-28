package com.shelter.core.service;

import com.shelter.core.dto.DonationType;
import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.MoneyDonationRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.repository.DonationRepository;
import com.shelter.core.repository.DonationRepository.DonationHeader;
import com.shelter.core.repository.FinanceRepository;
import com.shelter.core.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Денежные пожертвования: приход DONATION; удаление — по правилам вида пожертвования. */
class DonationServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("5000.00");

    private DonationRepository repository;
    private ReceiptRepository receiptRepository;
    private ReceiptService receiptService;
    private FinanceRepository financeRepository;
    private BalanceGuard balanceGuard;
    private DonationService service;

    @BeforeEach
    void setUp() {
        repository = mock(DonationRepository.class);
        receiptRepository = mock(ReceiptRepository.class);
        receiptService = mock(ReceiptService.class);
        financeRepository = mock(FinanceRepository.class);
        balanceGuard = mock(BalanceGuard.class);
        service = new DonationService(repository, receiptRepository, receiptService, financeRepository, balanceGuard, CLOCK);
        when(repository.insertMoneyDonation(any(), any(), any())).thenReturn(30L);
        when(repository.findById(30L)).thenReturn(Optional.of(money(30L)));
        when(receiptRepository.donationItems(any())).thenReturn(Map.of());
    }

    private static DonationHeader money(Long id) {
        return new DonationHeader(id, DonationType.MONEY, null, TODAY.minusDays(2), AMOUNT, 77L, null, null);
    }

    @Test
    void moneyDonationByNameCreatesBenefactorAndIncome() {
        when(receiptRepository.insertBenefactor("Смирнов")).thenReturn(4L);

        service.createMoney(new MoneyDonationRequest(null, " Смирнов ", AMOUNT, null, null));

        verify(repository).insertMoneyDonation(4L, TODAY, null);
        verify(financeRepository).insert(eq(TODAY), eq(MoneyDirection.INCOME), eq(FinanceCategory.DONATION), eq(AMOUNT),
                anyString(), eq(30L));
    }

    @Test
    void anonymousDonationHasNoBenefactor() {
        service.createMoney(new MoneyDonationRequest(null, null, AMOUNT, TODAY.minusDays(1), "Ящик"));
        verify(repository).insertMoneyDonation(null, TODAY.minusDays(1), "Ящик");
        verify(receiptRepository, never()).insertBenefactor(anyString());
    }

    @Test
    void invalidDonationDataIsBadRequest() {
        assertThatThrownBy(() -> service.createMoney(new MoneyDonationRequest(null, null, AMOUNT, TODAY.plusDays(1), null)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("будущем");
        assertThatThrownBy(() -> service.createMoney(new MoneyDonationRequest(1L, "Смирнов", AMOUNT, null, null)))
                .isInstanceOf(InvalidRequestException.class);
        when(receiptRepository.benefactorExists(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.createMoney(new MoneyDonationRequest(99L, null, AMOUNT, null, null)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("не найден");
        verify(financeRepository, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void deletingMoneyDonationChecksBalanceAtDonationDate() {
        when(repository.lockById(30L)).thenReturn(Optional.of(money(30L)));

        service.delete(30L);

        InOrder order = inOrder(repository, balanceGuard, financeRepository);
        order.verify(repository).lockById(30L);
        order.verify(balanceGuard).ensureCanSpend(eq(TODAY.minusDays(2)), eq(AMOUNT), anyString());
        order.verify(financeRepository).delete(77L);
        order.verify(repository).delete(30L);
    }

    @Test
    void moneyDonationAlreadySpentIsNotDeleted() {
        when(repository.lockById(30L)).thenReturn(Optional.of(money(30L)));
        doThrow(new BusinessRuleException("Недостаточно средств")).when(balanceGuard).ensureCanSpend(any(), any(), anyString());

        assertThatThrownBy(() -> service.delete(30L)).isInstanceOf(BusinessRuleException.class);
        verify(financeRepository, never()).delete(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void goodsDonationIsDeletedByWarehouseRules() {
        when(repository.lockById(31L)).thenReturn(Optional.of(
                new DonationHeader(31L, DonationType.GOODS, null, TODAY, null, null, null, null)));
        service.delete(31L);
        verify(receiptService).deleteGoodsDonation(31L);
        verify(balanceGuard, never()).ensureCanSpend(any(), any(), anyString());
    }
}
