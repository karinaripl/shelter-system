package com.shelter.core.service;

import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.SupplyPaymentRequest;
import com.shelter.core.dto.SupplyPaymentResponse;
import com.shelter.core.dto.UnpaidSupplyResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.FinanceRepository;
import com.shelter.core.repository.SupplyPaymentRepository;
import com.shelter.core.repository.SupplyPaymentRepository.LockedSupply;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static com.shelter.core.service.MedicalChecks.trimToNull;

/**
 * Оплата поставок (только ADMIN). Сумма оплаты — всегда сумма поставки из позиций; дата не раньше поставки
 * и не в будущем; баланс не уходит в минус; повторная оплата — 409. Создаётся операция EXPENSE/PURCHASE,
 * поставка ссылается на неё (supply.transaction_id). Блокировки: сначала поставка, потом «касса».
 */
@Service
public class SupplyPaymentService {

    private final SupplyPaymentRepository repository;
    private final FinanceRepository financeRepository;
    private final ReceiptService receiptService;
    private final BalanceGuard balanceGuard;
    private final Clock clock;

    public SupplyPaymentService(SupplyPaymentRepository repository, FinanceRepository financeRepository,
                                ReceiptService receiptService, BalanceGuard balanceGuard, Clock clock) {
        this.repository = repository;
        this.financeRepository = financeRepository;
        this.receiptService = receiptService;
        this.balanceGuard = balanceGuard;
        this.clock = clock;
    }

    public List<UnpaidSupplyResponse> findUnpaid(Long supplierId) {
        return repository.findUnpaid(supplierId);
    }

    @Transactional
    public SupplyPaymentResponse pay(Long id, SupplyPaymentRequest request) {
        LockedSupply supply = repository.lock(id).orElseThrow(() -> NotFoundException.supply(id));
        if (supply.transactionId() != null) {
            throw new BusinessRuleException("Поставка №" + id + " уже оплачена (операция №" + supply.transactionId() + ")");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate date = request != null && request.paymentDate() != null ? request.paymentDate() : today;
        if (date.isAfter(today)) {
            throw new InvalidRequestException("Дата оплаты не может быть в будущем");
        }
        if (date.isBefore(supply.supplyDate())) {
            throw new BusinessRuleException("Дата оплаты " + date + " раньше даты поставки " + supply.supplyDate());
        }
        BigDecimal amount = payableAmount(supply);

        balanceGuard.ensureCanSpend(date, amount, "оплаты поставки №" + id);
        String note = request != null ? trimToNull(request.description()) : null;
        Long transactionId = financeRepository.insert(date, MoneyDirection.EXPENSE, FinanceCategory.PURCHASE, amount,
                "Оплата поставки №" + id + " («" + supply.supplierName() + "»)" + (note != null ? ": " + note : ""),
                null);
        repository.setTransaction(id, transactionId);
        return new SupplyPaymentResponse(id, transactionId, amount, date,
                Money.of(financeRepository.totalsUpTo(today).balance()));
    }

    /** Отмена ошибочной оплаты: операция удаляется, поставка снова неоплачена. */
    @Transactional
    public void cancelPayment(Long id) {
        LockedSupply supply = repository.lock(id).orElseThrow(() -> NotFoundException.supply(id));
        if (supply.transactionId() == null) {
            throw new BusinessRuleException("Поставка №" + id + " не оплачена — отменять нечего");
        }
        repository.setTransaction(id, null);
        financeRepository.delete(supply.transactionId());
    }

    /** Удаление поставки администратором — вместе с оплатой, если по партиям не было движения (правило шага 5). */
    @Transactional
    public void deleteSupply(Long id) {
        LockedSupply supply = repository.lock(id).orElseThrow(() -> NotFoundException.supply(id));
        receiptService.deleteSupplyRecord(id);
        if (supply.transactionId() != null) {
            financeRepository.delete(supply.transactionId());
        }
    }

    /** Сумма к оплате: у всех позиций есть цена, сумма больше нуля. */
    private BigDecimal payableAmount(LockedSupply supply) {
        UnpaidSupplyResponse summary = repository.summary(supply.id());
        if (summary.itemsWithoutPrice() > 0) {
            throw new BusinessRuleException("У " + summary.itemsWithoutPrice() + " позиций поставки №" + supply.id()
                    + " нет цены: сумму поставки посчитать нельзя. Удалите поставку и оформите её заново с ценами");
        }
        if (summary.totalAmount().signum() <= 0) {
            throw new BusinessRuleException("Сумма поставки №" + supply.id() + " равна 0 — оплачивать нечего");
        }
        return Money.of(summary.totalAmount());
    }
}
