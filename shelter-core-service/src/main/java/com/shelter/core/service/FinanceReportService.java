package com.shelter.core.service;

import com.shelter.core.dto.BenefactorDonationRow;
import com.shelter.core.dto.BenefactorDonationsReport;
import com.shelter.core.dto.CategoryAmount;
import com.shelter.core.dto.CategoryReport;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.MonthRow;
import com.shelter.core.dto.MonthlyReport;
import com.shelter.core.dto.PeriodBalanceReport;
import com.shelter.core.dto.SalaryPeriodAmount;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.repository.DonationRepository;
import com.shelter.core.repository.FinanceRepository;
import com.shelter.core.repository.FinanceRepository.CategoryTotal;
import com.shelter.core.repository.FinanceRepository.MonthTotal;
import com.shelter.core.repository.FinanceRepository.Totals;
import com.shelter.core.repository.SalaryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Финансовые отчёты (только ADMIN). Период по умолчанию — с 1 января текущего года по сегодня; для отчёта
 * по месяцам — последние 12 месяцев, включая текущий. Баланс на начало — по операциям раньше from,
 * на конец — по операциям не позже to. Всё считается по дате операции (когда деньги ушли), а не по месяцу,
 * за который начислена зарплата: этот месяц показан отдельно в CategoryReport.salaries.
 */
@Service
public class FinanceReportService {

    /** Больше месяцев график не показывает: защита от случайного периода в сто лет. */
    static final int MAX_MONTHS = 120;

    private final FinanceRepository financeRepository;
    private final DonationRepository donationRepository;
    private final SalaryRepository salaryRepository;
    private final Clock clock;

    public FinanceReportService(FinanceRepository financeRepository, DonationRepository donationRepository,
                                SalaryRepository salaryRepository, Clock clock) {
        this.financeRepository = financeRepository;
        this.donationRepository = donationRepository;
        this.salaryRepository = salaryRepository;
        this.clock = clock;
    }

    public CategoryReport byCategory(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : today().withDayOfYear(1);
        LocalDate end = to != null ? to : today();
        ReceiptService.checkPeriod(start, end);
        List<CategoryTotal> totals = financeRepository.byCategory(start, end);
        List<CategoryAmount> income = amounts(totals, MoneyDirection.INCOME);
        List<CategoryAmount> expense = amounts(totals, MoneyDirection.EXPENSE);
        List<SalaryPeriodAmount> salaries = salaryRepository.paidBetween(start, end).stream()
                .map(s -> new SalaryPeriodAmount(s.periodMonth(), Money.of(s.amount()), s.employees()))
                .toList();
        return new CategoryReport(start, end, opening(start), income, expense, sum(income), sum(expense),
                closing(end), salaries);
    }

    public MonthlyReport byMonth(LocalDate from, LocalDate to) {
        YearMonth current = YearMonth.now(clock);
        LocalDate start = from != null ? from : current.minusMonths(11).atDay(1);
        LocalDate end = to != null ? to : current.atEndOfMonth();
        ReceiptService.checkPeriod(start, end);
        YearMonth first = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        if (first.plusMonths(MAX_MONTHS - 1).isBefore(last)) {
            throw new InvalidRequestException("Период отчёта по месяцам — не больше " + MAX_MONTHS + " месяцев");
        }
        Map<YearMonth, MonthTotal> totals = financeRepository.byMonth(start, end).stream()
                .collect(Collectors.toMap(t -> YearMonth.from(t.month()), Function.identity()));

        BigDecimal opening = opening(start);
        BigDecimal balance = opening;
        List<MonthRow> months = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            MonthTotal t = totals.get(month);
            BigDecimal income = t != null ? t.income() : BigDecimal.ZERO;
            BigDecimal expense = t != null ? t.expense() : BigDecimal.ZERO;
            BigDecimal net = income.subtract(expense);
            balance = balance.add(net);
            months.add(new MonthRow(month, Money.of(income), Money.of(expense), Money.of(net), Money.of(balance)));
        }
        return new MonthlyReport(start, end, opening, months, Money.of(balance));
    }

    public PeriodBalanceReport balance(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : today().withDayOfYear(1);
        LocalDate end = to != null ? to : today();
        ReceiptService.checkPeriod(start, end);
        Totals period = financeRepository.totals(new FinanceRepository.Filter(start, end, null, null));
        return new PeriodBalanceReport(start, end, opening(start), Money.of(period.income()),
                Money.of(period.expense()), closing(end));
    }

    public BenefactorDonationsReport byBenefactor(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : today().withDayOfYear(1);
        LocalDate end = to != null ? to : today();
        ReceiptService.checkPeriod(start, end);
        List<BenefactorDonationRow> rows = donationRepository.totalsByBenefactor(start, end).stream()
                .map(t -> new BenefactorDonationRow(t.benefactor(), t.benefactor() == null, t.moneyCount(),
                        Money.of(t.moneyTotal()), t.goodsCount(), t.lastDonation()))
                .toList();
        BigDecimal money = rows.stream().map(BenefactorDonationRow::moneyTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int goods = rows.stream().mapToInt(BenefactorDonationRow::goodsCount).sum();
        return new BenefactorDonationsReport(start, end, rows, Money.of(money), goods);
    }

    private static List<CategoryAmount> amounts(List<CategoryTotal> totals, MoneyDirection direction) {
        return totals.stream()
                .filter(t -> t.direction() == direction)
                .map(t -> new CategoryAmount(t.category(), t.category().title(), Money.of(t.amount()), t.count()))
                .toList();
    }

    private static BigDecimal sum(List<CategoryAmount> amounts) {
        return Money.of(amounts.stream().map(CategoryAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal opening(LocalDate from) {
        return Money.of(financeRepository.totalsBefore(from).balance());
    }

    private BigDecimal closing(LocalDate to) {
        return Money.of(financeRepository.totalsUpTo(to).balance());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
