package com.shelter.core.service;

import com.shelter.core.dto.BenefactorBrief;
import com.shelter.core.dto.GoodsDonationRequest;
import com.shelter.core.dto.GoodsDonationResponse;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.ProductResponse;
import com.shelter.core.dto.ReceiptItemRequest;
import com.shelter.core.dto.ReceiptItemResponse;
import com.shelter.core.dto.SupplyRequest;
import com.shelter.core.dto.SupplyResponse;
import com.shelter.core.dto.WarehouseResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.ProductRepository;
import com.shelter.core.repository.ReceiptRepository;
import com.shelter.core.repository.ReceiptRepository.DonationHeader;
import com.shelter.core.repository.ReceiptRepository.ReceiptItem;
import com.shelter.core.repository.ReceiptRepository.SupplyHeader;
import com.shelter.core.repository.WarehouseReferenceRepository;
import com.shelter.core.security.CurrentUser;
import com.shelter.core.security.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Приход на склад: поставки (CURATOR, ADMIN; создаются неоплаченными — оплату вносит ADMIN, шаг 6)
 * и натуральные пожертвования. Цены и сумму поставки видит только ADMIN.
 */
@Service
public class ReceiptService {

    private static final int BENEFACTOR_LIST_LIMIT = 50;

    private final ReceiptRepository receiptRepository;
    private final ProductRepository productRepository;
    private final WarehouseReferenceRepository referenceRepository;
    private final EmployeeService employeeService;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ReceiptService(ReceiptRepository receiptRepository,
                          ProductRepository productRepository,
                          WarehouseReferenceRepository referenceRepository,
                          EmployeeService employeeService,
                          CurrentUser currentUser,
                          Clock clock) {
        this.receiptRepository = receiptRepository;
        this.productRepository = productRepository;
        this.referenceRepository = referenceRepository;
        this.employeeService = employeeService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ---------- поставки ----------

    public PageResponse<SupplyResponse> findSupplies(Long supplierId, LocalDate from, LocalDate to, int page, int size) {
        Paging.validate(page, size);
        checkPeriod(from, to);
        List<SupplyHeader> headers = receiptRepository.findSupplies(supplierId, from, to, page, size);
        Map<Long, List<ReceiptItem>> items = receiptRepository.supplyItems(headers.stream().map(SupplyHeader::id).toList());
        List<SupplyResponse> content = headers.stream()
                .map(h -> toSupply(h, items.getOrDefault(h.id(), List.of())))
                .toList();
        return PageResponse.of(content, page, size, receiptRepository.countSupplies(supplierId, from, to));
    }

    public SupplyResponse findSupply(Long id) {
        SupplyHeader header = receiptRepository.findSupply(id)
                .orElseThrow(() -> new NotFoundException("Поставка с id=" + id + " не найдена"));
        return toSupply(header, receiptRepository.supplyItems(List.of(id)).getOrDefault(id, List.of()));
    }

    /**
     * Поставка: дата не в будущем, поставщик и склад существуют, позиции — товары в обороте, количество
     * больше нуля, срок годности позже даты прихода. Создаются партии и остатки на складе; оплаты нет.
     */
    @Transactional
    public SupplyResponse createSupply(SupplyRequest request) {
        LocalDate date = receiptDate(request.supplyDate(), "поставки");
        if (referenceRepository.findSupplier(request.supplierId()).isEmpty()) {
            throw new InvalidRequestException("Поставщик с id=" + request.supplierId() + " не найден");
        }
        warehouse(request.warehouseId());
        checkItems(request.items(), date, true);
        Long employeeId = employeeService.currentEmployeeId();

        Long supplyId = receiptRepository.insertSupply(request.supplierId(), date, employeeId,
                MedicalChecks.trimToNull(request.notes()));
        for (ReceiptItemRequest item : request.items()) {
            receiptRepository.insertBatch(item.productId(), supplyId, null, date, item.expirationDate(),
                    item.quantity(), item.unitPrice(), employeeId, request.warehouseId());
        }
        return findSupply(supplyId);
    }

    /**
     * Удаление ошибочной поставки целиком: только неоплаченной и только если по её партиям не было расхода,
     * списания и перемещений. Оплаченную поставку удаляет администратор вместе с оплатой (шаг 6).
     */
    @Transactional
    public void deleteSupply(Long id) {
        boolean paid = receiptRepository.lockSupply(id)
                .orElseThrow(() -> new NotFoundException("Поставка с id=" + id + " не найдена"));
        if (paid) {
            throw new BusinessRuleException("Поставка оплачена: удалить её вместе с оплатой может только администратор");
        }
        List<Long> batchIds = batchIds(receiptRepository.supplyItems(List.of(id)).getOrDefault(id, List.of()));
        deleteBatches(batchIds, "поставки");
        receiptRepository.deleteSupply(id);
    }

    private SupplyResponse toSupply(SupplyHeader h, List<ReceiptItem> items) {
        boolean admin = currentUser.hasRole(Roles.ADMIN);
        List<ReceiptItemResponse> itemResponses = items.stream()
                .map(i -> admin ? i.item() : withoutPrice(i.item()))
                .toList();
        BigDecimal total = admin
                ? items.stream()
                        .map(ReceiptItem::item)
                        .filter(i -> i.unitPrice() != null)
                        .map(i -> i.unitPrice().multiply(i.quantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP)
                : null;
        return new SupplyResponse(h.id(), h.supplier(), h.supplyDate(), receiptWarehouse(items), h.paid(),
                h.employee(), h.notes(), itemResponses, total, h.createdAt());
    }

    private static ReceiptItemResponse withoutPrice(ReceiptItemResponse i) {
        return new ReceiptItemResponse(i.batchId(), i.productId(), i.productName(), i.unit(), i.quantity(),
                i.expirationDate(), null);
    }

    // ---------- натуральные пожертвования ----------

    public PageResponse<GoodsDonationResponse> findGoodsDonations(LocalDate from, LocalDate to, int page, int size) {
        Paging.validate(page, size);
        checkPeriod(from, to);
        List<DonationHeader> headers = receiptRepository.findGoodsDonations(from, to, page, size);
        Map<Long, List<ReceiptItem>> items =
                receiptRepository.donationItems(headers.stream().map(DonationHeader::id).toList());
        List<GoodsDonationResponse> content = headers.stream()
                .map(h -> toDonation(h, items.getOrDefault(h.id(), List.of())))
                .toList();
        return PageResponse.of(content, page, size, receiptRepository.countGoodsDonations(from, to));
    }

    public GoodsDonationResponse findGoodsDonation(Long id) {
        DonationHeader header = receiptRepository.findGoodsDonation(id)
                .orElseThrow(() -> new NotFoundException("Натуральное пожертвование с id=" + id + " не найдено"));
        return toDonation(header, receiptRepository.donationItems(List.of(id)).getOrDefault(id, List.of()));
    }

    /**
     * Натуральное пожертвование: благотворитель из списка, новый по имени или аноним; позиции без цены.
     * Создаются партии с источником «пожертвование» и остатки на складе.
     */
    @Transactional
    public GoodsDonationResponse createGoodsDonation(GoodsDonationRequest request) {
        LocalDate date = receiptDate(request.donationDate(), "пожертвования");
        String benefactorName = MedicalChecks.trimToNull(request.benefactorName());
        if (request.benefactorId() != null && benefactorName != null) {
            throw new InvalidRequestException("Укажите благотворителя из списка (benefactorId) или имя нового "
                    + "(benefactorName), но не оба сразу");
        }
        if (request.benefactorId() != null && !receiptRepository.benefactorExists(request.benefactorId())) {
            throw new InvalidRequestException("Благотворитель с id=" + request.benefactorId() + " не найден");
        }
        warehouse(request.warehouseId());
        checkItems(request.items(), date, false);
        Long employeeId = employeeService.currentEmployeeId();

        Long benefactorId = benefactorName != null ? receiptRepository.insertBenefactor(benefactorName) : request.benefactorId();
        Long donationId = receiptRepository.insertGoodsDonation(benefactorId, date, MedicalChecks.trimToNull(request.notes()));
        for (ReceiptItemRequest item : request.items()) {
            receiptRepository.insertBatch(item.productId(), null, donationId, date, item.expirationDate(),
                    item.quantity(), null, employeeId, request.warehouseId());
        }
        return findGoodsDonation(donationId);
    }

    /** Натуральное пожертвование удаляется, только если его товары ещё не расходовались, не списывались и не перемещались. */
    @Transactional
    public void deleteGoodsDonation(Long id) {
        if (!receiptRepository.lockGoodsDonation(id)) {
            throw new NotFoundException("Натуральное пожертвование с id=" + id + " не найдено");
        }
        List<Long> batchIds = batchIds(receiptRepository.donationItems(List.of(id)).getOrDefault(id, List.of()));
        deleteBatches(batchIds, "пожертвования");
        receiptRepository.deleteDonation(id);
    }

    public List<BenefactorBrief> findBenefactors(String name) {
        return receiptRepository.findBenefactors(name, BENEFACTOR_LIST_LIMIT);
    }

    private GoodsDonationResponse toDonation(DonationHeader h, List<ReceiptItem> items) {
        return new GoodsDonationResponse(h.id(), h.benefactor(), h.donationDate(), receiptWarehouse(items),
                h.notes(), items.stream().map(ReceiptItem::item).toList(), h.createdAt());
    }

    // ---------- общее ----------

    private LocalDate receiptDate(LocalDate requested, String what) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = requested != null ? requested : today;
        if (date.isAfter(today)) {
            throw new InvalidRequestException("Дата " + what + " не может быть в будущем");
        }
        return date;
    }

    /** Позиции: товар существует и в обороте, срок годности позже даты прихода, цена — только в поставке. */
    private void checkItems(List<ReceiptItemRequest> items, LocalDate date, boolean pricesAllowed) {
        for (ReceiptItemRequest item : items) {
            ProductResponse product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new InvalidRequestException("Товар с id=" + item.productId() + " не найден"));
            if (!product.active()) {
                throw new BusinessRuleException("Товар «" + product.name() + "» в архиве: принимать его на склад нельзя. "
                        + "Верните товар в оборот (PUT /products/" + product.id() + "/active)");
            }
            if (item.expirationDate() != null && !item.expirationDate().isAfter(date)) {
                throw new InvalidRequestException("Срок годности «" + product.name() + "» (" + item.expirationDate()
                        + ") должен быть позже даты прихода " + date);
            }
            if (!pricesAllowed && item.unitPrice() != null) {
                throw new InvalidRequestException("У позиций натурального пожертвования нет цены (unitPrice)");
            }
        }
    }

    private void deleteBatches(List<Long> batchIds, String what) {
        if (batchIds.isEmpty()) {
            return;
        }
        receiptRepository.lockStockOfBatches(batchIds);
        if (receiptRepository.batchesHaveMovements(batchIds)) {
            throw new BusinessRuleException("Нельзя удалить приход " + what + ": по его партиям уже были расход, "
                    + "списание или перемещение");
        }
        receiptRepository.deleteBatches(batchIds);
    }

    private void warehouse(Long warehouseId) {
        if (referenceRepository.findWarehouse(warehouseId).isEmpty()) {
            throw new InvalidRequestException("Склад с id=" + warehouseId + " не найден");
        }
    }

    private WarehouseResponse receiptWarehouse(List<ReceiptItem> items) {
        return items.stream()
                .map(ReceiptItem::warehouseId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .flatMap(referenceRepository::findWarehouse)
                .orElse(null);
    }

    private static List<Long> batchIds(List<ReceiptItem> items) {
        return items.stream().map(i -> i.item().batchId()).toList();
    }

    static void checkPeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("Начало периода позже его конца");
        }
    }
}
