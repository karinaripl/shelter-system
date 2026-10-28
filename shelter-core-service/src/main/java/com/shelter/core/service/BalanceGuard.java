package com.shelter.core.service;

import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.repository.FinanceRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Баланс не уходит в минус. Проверяется не только сегодняшний баланс, но и баланс на дату операции и на каждую
 * следующую дату с операциями: расход задним числом в день, когда денег не было, — тоже 409, даже если сейчас
 * денег достаточно (иначе отчёт «баланс на начало периода» показал бы минус).
 *
 * Перед проверкой берётся блокировка «кассы» до конца транзакции: две параллельные оплаты не пройдут проверку
 * по одному и тому же балансу. Вызывать внутри транзакции, после блокировок документов (поставки, пожертвования).
 */
@Component
public class BalanceGuard {

    private final FinanceRepository repository;
    private final Clock clock;

    public BalanceGuard(FinanceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Блокировка «кассы» без проверки — для отмен, которые баланс не уменьшают, но меняют журнал. */
    public void lock() {
        repository.lockBalance();
    }

    /**
     * Блокирует «кассу» и проверяет, что операция, уменьшающая баланс на amount с датой date (расход или
     * удаление прихода), не уведёт его в минус. what — что делаем, для сообщения: «оплаты поставки №5».
     */
    public void ensureCanSpend(LocalDate date, BigDecimal amount, String what) {
        repository.lockBalance();
        BigDecimal current = repository.totalsUpTo(LocalDate.now(clock)).balance();
        BigDecimal available = repository.availableFrom(date);
        check(current, available, date, amount, what);
    }

    /**
     * current — баланс на сегодня; available — сколько можно потратить датой date, не уйдя в минус ни в один
     * последующий день (с учётом операций, датированных позже: расход задним числом не должен «съесть» деньги, уже потраченные потом).
     */
    static void check(BigDecimal current, BigDecimal available, LocalDate date, BigDecimal amount, String what) {
        if (available.compareTo(amount) >= 0) {
            return;
        }
        if (current.compareTo(amount) < 0) {
            throw new BusinessRuleException("Недостаточно средств для " + what + ": текущий баланс "
                    + Money.format(current) + ", требуется " + Money.format(amount));
        }
        throw new BusinessRuleException("Недостаточно средств для " + what + " датой " + date
                + ": текущий баланс " + Money.format(current) + ", но с учётом операций на эту дату и позже можно "
                + "потратить не больше " + Money.format(available.max(BigDecimal.ZERO)) + ", требуется "
                + Money.format(amount));
    }
}
