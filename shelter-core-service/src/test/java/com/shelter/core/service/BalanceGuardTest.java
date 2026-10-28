package com.shelter.core.service;

import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.repository.FinanceRepository;
import com.shelter.core.repository.FinanceRepository.Totals;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Баланс не уходит в минус ни сегодня, ни на дату операции, ни на любую следующую дату. */
class BalanceGuardTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void spendingUpToAvailableAmountIsAllowed() {
        assertThatCode(() -> BalanceGuard.check(money("1000"), money("1000"), TODAY, money("1000.00"), "оплаты"))
                .doesNotThrowAnyException();
    }

    @Test
    void spendingMoreThanCurrentBalanceIsConflictWithCurrentBalanceInMessage() {
        assertThatThrownBy(() -> BalanceGuard.check(money("1000"), money("1000"), TODAY, money("1000.01"), "оплаты поставки №5"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("оплаты поставки №5")
                .hasMessageContaining("текущий баланс 1000.00")
                .hasMessageContaining("требуется 1000.01");
    }

    @Test
    void backdatedExpenseOnPoorDayIsConflictEvenIfThereIsMoneyNow() {
        LocalDate past = TODAY.minusDays(100);
        // Сейчас 50 000, но на ту дату (и до следующего прихода) было только 2 000
        assertThatThrownBy(() -> BalanceGuard.check(money("50000"), money("2000"), past, money("3000"), "расхода"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("датой " + past)
                .hasMessageContaining("текущий баланс 50000.00")
                .hasMessageContaining("не больше 2000.00");
    }

    @Test
    void laterExpenseReducesWhatCanBeSpentEarlier() {
        // Десять дней назад было 10 000, но после этого уже потрачено 8 000
        assertThatThrownBy(() -> BalanceGuard.check(money("10000"), money("2000"), TODAY.minusDays(10), money("5000"), "расхода"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("не больше 2000.00");
    }

    @Test
    void ensureCanSpendLocksBeforeReadingBalance() {
        FinanceRepository repository = mock(FinanceRepository.class);
        when(repository.totalsUpTo(TODAY)).thenReturn(new Totals(money("500"), money("0")));
        when(repository.availableFrom(TODAY)).thenReturn(money("500"));

        new BalanceGuard(repository, CLOCK).ensureCanSpend(TODAY, money("500"), "оплаты");

        InOrder order = inOrder(repository);
        order.verify(repository).lockBalance();
        order.verify(repository).totalsUpTo(TODAY);
        order.verify(repository).availableFrom(TODAY);
    }
}
