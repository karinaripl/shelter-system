package com.shelter.core.service;

import com.shelter.core.dto.DonationResponse;
import com.shelter.core.dto.DonationType;
import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.MoneyDonationRequest;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.ReceiptItemResponse;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.DonationRepository;
import com.shelter.core.repository.DonationRepository.DonationHeader;
import com.shelter.core.repository.DonationRepository.Filter;
import com.shelter.core.repository.FinanceRepository;
import com.shelter.core.repository.ReceiptRepository;
import com.shelter.core.repository.ReceiptRepository.ReceiptItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.shelter.core.service.MedicalChecks.trimToNull;

/**
 * Пожертвования в админ-панели (только ADMIN): денежные создаются здесь (операция INCOME/DONATION),
 * натуральные — при приёме на склад (CURATOR, /goods-donations). Список — оба вида, у натуральных товары без цен.
 * Удаление: натуральное — только если товары не расходовались; денежное — если баланс не уйдёт в минус.
 */
@Service
public class DonationService {

    private final DonationRepository repository;
    private final ReceiptRepository receiptRepository;
    private final ReceiptService receiptService;
    private final FinanceRepository financeRepository;
    private final BalanceGuard balanceGuard;
    private final Clock clock;

    public DonationService(DonationRepository repository, ReceiptRepository receiptRepository,
                           ReceiptService receiptService, FinanceRepository financeRepository,
                           BalanceGuard balanceGuard, Clock clock) {
        this.repository = repository;
        this.receiptRepository = receiptRepository;
        this.receiptService = receiptService;
        this.financeRepository = financeRepository;
        this.balanceGuard = balanceGuard;
        this.clock = clock;
    }

    public PageResponse<DonationResponse> findPage(LocalDate from, LocalDate to, DonationType type, Long benefactorId,
                                                   int page, int size) {
        Paging.validate(page, size);
        ReceiptService.checkPeriod(from, to);
        Filter filter = new Filter(from, to, type, benefactorId);
        List<DonationHeader> headers = repository.findPage(filter, page, size);
        Map<Long, List<ReceiptItem>> items = receiptRepository.donationItems(headers.stream()
                .filter(h -> h.type() == DonationType.GOODS).map(DonationHeader::id).toList());
        List<DonationResponse> content = headers.stream()
                .map(h -> toResponse(h, items.getOrDefault(h.id(), List.of())))
                .toList();
        return PageResponse.of(content, page, size, repository.count(filter));
    }

    public DonationResponse findById(Long id) {
        DonationHeader header = repository.findById(id).orElseThrow(() -> NotFoundException.donation(id));
        return toResponse(header, receiptRepository.donationItems(List.of(id)).getOrDefault(id, List.of()));
    }

    /** Денежное пожертвование: сумма больше 0, дата не в будущем; создаётся операция прихода. */
    @Transactional
    public DonationResponse createMoney(MoneyDonationRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = request.donationDate() != null ? request.donationDate() : today;
        if (date.isAfter(today)) {
            throw new InvalidRequestException("Дата пожертвования не может быть в будущем");
        }
        String benefactorName = trimToNull(request.benefactorName());
        if (request.benefactorId() != null && benefactorName != null) {
            throw new InvalidRequestException("Укажите благотворителя из списка (benefactorId) или имя нового "
                    + "(benefactorName), но не оба сразу");
        }
        if (request.benefactorId() != null && !receiptRepository.benefactorExists(request.benefactorId())) {
            throw new InvalidRequestException("Благотворитель с id=" + request.benefactorId() + " не найден");
        }
        Long benefactorId = benefactorName != null ? receiptRepository.insertBenefactor(benefactorName) : request.benefactorId();
        Long donationId = repository.insertMoneyDonation(benefactorId, date, trimToNull(request.notes()));
        financeRepository.insert(date, MoneyDirection.INCOME, FinanceCategory.DONATION, Money.of(request.amount()),
                "Пожертвование №" + donationId, donationId);
        return findById(donationId);
    }

    @Transactional
    public void delete(Long id) {
        DonationHeader donation = repository.lockById(id).orElseThrow(() -> NotFoundException.donation(id));
        if (donation.type() == DonationType.GOODS) {
            receiptService.deleteGoodsDonation(id);
            return;
        }
        balanceGuard.ensureCanSpend(donation.donationDate(), donation.amount(), "удаления пожертвования №" + id);
        // Сначала операция: она ссылается на пожертвование (ON DELETE RESTRICT)
        financeRepository.delete(donation.transactionId());
        repository.delete(id);
    }

    private static DonationResponse toResponse(DonationHeader h, List<ReceiptItem> items) {
        List<ReceiptItemResponse> goods = items.stream().map(ReceiptItem::item).toList();
        return new DonationResponse(h.id(), h.type(), h.benefactor(), h.donationDate(), h.amount(), h.transactionId(),
                h.notes(), goods, h.createdAt());
    }
}
