package com.shelter.core.repository;

import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.ManualTransactionRequest;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.MoneyDonationRequest;
import com.shelter.core.dto.SupplyPaymentRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.service.DonationService;
import com.shelter.core.service.FinanceReportService;
import com.shelter.core.service.FinanceService;
import com.shelter.core.service.SalaryService;
import com.shelter.core.service.SupplyPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.shelter.model.generated.shelter.Tables.FINANCE_TRANSACTION;
import static com.shelter.model.generated.shelter.Tables.SALARY_PAYMENT;
import static com.shelter.model.generated.shelter.Tables.SUPPLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Деньги на настоящей базе: баланс сходится после серии операций, параллельные оплаты не уводят баланс в минус,
 * начисление зарплаты при нехватке денег откатывается целиком, расход задним числом проверяется на свою дату.
 */
class FinanceIT extends CoreApplicationIT {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private DonationService donationService;
    @Autowired
    private FinanceService financeService;
    @Autowired
    private SupplyPaymentService paymentService;
    @Autowired
    private SalaryService salaryService;
    @Autowired
    private FinanceReportService reportService;

    private void donate(String amount, LocalDate date) {
        donationService.createMoney(new MoneyDonationRequest(null, null, new BigDecimal(amount), date, null));
    }

    private BigDecimal balance() {
        return financeService.balance(null).balance();
    }

    /** Баланс независимо от сервиса: прямо по журналу. */
    private static BigDecimal journalBalance() {
        return dsl.resultQuery("SELECT COALESCE(SUM(CASE WHEN direction = 'INCOME' THEN amount ELSE -amount END), 0) "
                + "FROM shelter_schema.finance_transaction WHERE transaction_date <= current_date")
                .fetchOne(0, BigDecimal.class);
    }

    @Test
    void balanceConvergesAfterSeriesOfOperations() {
        YearMonth lastMonth = YearMonth.from(TODAY).minusMonths(1);
        Long vet = position("Ветеринар IT", "40000.00");
        employee("Лебедев", vet, lastMonth.atDay(1).minusYears(1));
        // Принят 11-го числа прошлого месяца: зарплата пропорционально дням
        employee("Козлова", vet, lastMonth.atDay(11));

        donate("200000.00", lastMonth.atDay(1));
        donationService.createMoney(new MoneyDonationRequest(null, "Смирнов", new BigDecimal("50000.50"), TODAY, null));
        var repair = financeService.createManual(new ManualTransactionRequest(MoneyDirection.EXPENSE, null,
                new BigDecimal("12000.00"), lastMonth.atDay(5), "Ремонт вольера"));
        financeService.createManual(new ManualTransactionRequest(MoneyDirection.INCOME, FinanceCategory.OTHER,
                new BigDecimal("700.25"), TODAY, "Продажа сувениров"));

        Long first = supply(TODAY.minusDays(3), "10", "450.00");
        Long second = supply(TODAY.minusDays(2), "3", "333.33");
        paymentService.pay(first, new SupplyPaymentRequest(null, null));
        paymentService.pay(second, null);
        paymentService.cancelPayment(second);

        var salary = salaryService.accrue(lastMonth);
        int days = lastMonth.lengthOfMonth();
        BigDecimal expectedSalary = new BigDecimal("40000.00").add(new BigDecimal("40000")
                .multiply(BigDecimal.valueOf(days - 10)).divide(BigDecimal.valueOf(days), 2, java.math.RoundingMode.HALF_UP));
        assertThat(salary.total()).isEqualByComparingTo(expectedSalary);

        financeService.deleteManual(repair.id());

        BigDecimal expected = new BigDecimal("200000.00").add(new BigDecimal("50000.50")).add(new BigDecimal("700.25"))
                .subtract(new BigDecimal("4500.00")).subtract(expectedSalary);
        assertThat(balance()).isEqualByComparingTo(expected);
        assertThat(journalBalance()).isEqualByComparingTo(expected);

        var report = reportService.byCategory(lastMonth.atDay(1), TODAY);
        assertThat(report.openingBalance()).isEqualByComparingTo("0");
        assertThat(report.closingBalance()).isEqualByComparingTo(expected);
        assertThat(report.totalIncome().subtract(report.totalExpense())).isEqualByComparingTo(expected);
        var monthly = reportService.byMonth(lastMonth.atDay(1), YearMonth.from(TODAY).atEndOfMonth());
        assertThat(monthly.months()).hasSize(2);
        assertThat(monthly.closingBalance()).isEqualByComparingTo(expected);
        // Неоплаченная после отмены поставка снова в списке
        assertThat(paymentService.findUnpaid(null)).extracting(s -> s.id()).containsExactly(second);
        assertThat(paymentService.findUnpaid(null).get(0).totalAmount()).isEqualByComparingTo("999.99");
    }

    @Test
    void concurrentPaymentsDoNotOverdraw() throws Exception {
        donate("1000.00", TODAY.minusDays(10));
        List<Long> supplies = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            supplies.add(supply(TODAY.minusDays(5), "1", "600.00"));
        }

        ExecutorService pool = Executors.newFixedThreadPool(supplies.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (Long id : supplies) {
            Callable<Boolean> pay = () -> {
                start.await();
                try {
                    paymentService.pay(id, null);
                    return true;
                } catch (BusinessRuleException e) {
                    assertThat(e.getMessage()).contains("Недостаточно средств");
                    return false;
                }
            };
            results.add(pool.submit(pay));
        }
        start.countDown();
        int paid = 0;
        for (Future<Boolean> result : results) {
            paid += result.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(paid).isEqualTo(1);
        assertThat(balance()).isEqualByComparingTo("400.00");
        assertThat(dsl.fetchCount(SUPPLY, SUPPLY.TRANSACTION_ID.isNotNull())).isEqualTo(1);
    }

    @Test
    void salaryAccrualIsRolledBackWhenMoneyIsShort() {
        Long vet = position("Ветеринар IT", "60000.00");
        employee("Лебедев", vet, TODAY.minusYears(2));
        employee("Козлова", vet, TODAY.minusYears(2));
        donate("100000.00", TODAY.minusYears(1));
        YearMonth lastMonth = YearMonth.from(TODAY).minusMonths(1);

        assertThat(salaryService.preview(lastMonth).enough()).isFalse();
        assertThatThrownBy(() -> salaryService.accrue(lastMonth))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("текущий баланс 100000.00")
                .hasMessageContaining("требуется 120000.00");

        assertThat(dsl.fetchCount(SALARY_PAYMENT)).isZero();
        assertThat(dsl.fetchCount(FINANCE_TRANSACTION, FINANCE_TRANSACTION.CATEGORY.eq("SALARY"))).isZero();
        assertThat(balance()).isEqualByComparingTo("100000.00");

        // Зарплату за прошлый месяц можно выплатить из денег, пришедших уже после его конца
        donate("20000.00", TODAY.withDayOfMonth(1));
        var accrued = salaryService.accrue(lastMonth);
        assertThat(accrued.employees()).hasSize(2);
        assertThat(accrued.periodMonth()).isEqualTo(lastMonth);
        assertThat(accrued.paymentDate()).isEqualTo(TODAY);
        assertThat(dsl.fetchCount(FINANCE_TRANSACTION, FINANCE_TRANSACTION.CATEGORY.eq("SALARY")
                .and(FINANCE_TRANSACTION.TRANSACTION_DATE.eq(TODAY)))).isEqualTo(2);
        assertThat(salaryService.findAccrued(lastMonth).paymentDate()).isEqualTo(TODAY);
        assertThat(salaryService.preview(lastMonth).accruedOn()).isEqualTo(TODAY);
        // В отчёте зарплата — в периоде выплаты, с указанием месяца, за который она начислена
        var report = reportService.byCategory(TODAY, TODAY);
        assertThat(report.salaries()).singleElement().satisfies(s -> {
            assertThat(s.periodMonth()).isEqualTo(lastMonth);
            assertThat(s.amount()).isEqualByComparingTo("120000.00");
            assertThat(s.employees()).isEqualTo(2);
        });
        assertThat(reportService.byCategory(lastMonth.atDay(1), lastMonth.atEndOfMonth()).salaries()).isEmpty();
        assertThatThrownBy(() -> salaryService.accrue(lastMonth))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже начислена");
        salaryService.cancel(lastMonth);
        assertThat(dsl.fetchCount(SALARY_PAYMENT)).isZero();
        assertThat(balance()).isEqualByComparingTo("120000.00");
    }

    @Test
    void backdatedExpenseIsCheckedOnItsDateAndLaterDays() {
        donate("1000.00", TODAY.minusDays(30));
        donate("100000.00", TODAY.minusDays(5));

        // Сейчас денег много, но 20 дней назад было только 1 000
        assertThatThrownBy(() -> financeService.createManual(new ManualTransactionRequest(MoneyDirection.EXPENSE, null,
                new BigDecimal("5000.00"), TODAY.minusDays(20), "Ремонт")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("текущий баланс 101000.00")
                .hasMessageContaining("не больше 1000.00");
        financeService.createManual(new ManualTransactionRequest(MoneyDirection.EXPENSE, null,
                new BigDecimal("5000.00"), TODAY, "Ремонт"));

        // Большое пожертвование удалить нельзя: после сегодняшнего расхода без него было бы 1 000 − 5 000 < 0
        var donations = donationService.findPage(null, null, null, null, 0, 20).content();
        Long big = donations.stream().filter(d -> d.amount().compareTo(new BigDecimal("100000")) == 0)
                .findFirst().orElseThrow().id();
        assertThatThrownBy(() -> donationService.delete(big))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Недостаточно средств");
        assertThat(balance()).isEqualByComparingTo("96000.00");
    }

    @Test
    void salaryOfCurrentMonthIsDatedTodayAndBackdatedExpenseCannotUseItsMoney() {
        Long vet = position("Ветеринар IT", "30000.00");
        employee("Лебедев", vet, TODAY.minusYears(1));
        donate("50000.00", TODAY.minusDays(1));
        YearMonth current = YearMonth.from(TODAY);

        var accrued = salaryService.accrue(current);
        assertThat(accrued.paymentDate()).isEqualTo(TODAY);
        // Операций будущим числом нет: зарплата уже в балансе на сегодня
        assertThat(balance()).isEqualByComparingTo("20000.00");
        assertThat(dsl.fetchCount(FINANCE_TRANSACTION, FINANCE_TRANSACTION.TRANSACTION_DATE.gt(TODAY))).isZero();
        // Вчера было 50 000, но сегодняшняя зарплата уже из них выплачена: вчерашним числом можно потратить только 20 000
        assertThatThrownBy(() -> financeService.createManual(new ManualTransactionRequest(MoneyDirection.EXPENSE, null,
                new BigDecimal("25000.00"), TODAY.minusDays(1), "Ремонт")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("текущий баланс 20000.00").hasMessageContaining("требуется 25000.00");
        financeService.createManual(new ManualTransactionRequest(MoneyDirection.EXPENSE, null,
                new BigDecimal("20000.00"), TODAY.minusDays(1), "Ремонт"));
        assertThat(balance()).isEqualByComparingTo("0.00");
    }
}
