package com.shelter.core.service;

import com.shelter.core.dto.ExpenseRequest;
import com.shelter.core.dto.ExpenseResponse;
import com.shelter.core.dto.ExpenseResult;
import com.shelter.core.dto.ExpiredWriteOffPreview;
import com.shelter.core.dto.ExpiredWriteOffRequest;
import com.shelter.core.dto.MaterialSelection;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.ProductResponse;
import com.shelter.core.dto.TransferRequest;
import com.shelter.core.dto.TransferResponse;
import com.shelter.core.dto.WarehouseResponse;
import com.shelter.core.dto.WriteOffReason;
import com.shelter.core.dto.WriteOffRequest;
import com.shelter.core.dto.WriteOffResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.AnimalView;
import com.shelter.core.repository.ProductInfo;
import com.shelter.core.repository.ProductRepository;
import com.shelter.core.repository.StockJournalRepository;
import com.shelter.core.repository.StockJournalRepository.ExpenseRow;
import com.shelter.core.repository.StockJournalRepository.Filter;
import com.shelter.core.repository.StockRepository;
import com.shelter.core.repository.StockRow;
import com.shelter.core.repository.WarehouseReferenceRepository;
import com.shelter.core.service.FefoAllocator.Allocation;
import com.shelter.core.service.FefoAllocator.Portion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Движение по складу: ручной расход, перемещение между складами, списание (в том числе всех просроченных).
 * Изменяют ADMIN и CURATOR, читают ещё и VET. Строки остатков блокируются до конца транзакции
 * в общем порядке (товар, партия, склад); остаток не может стать отрицательным.
 */
@Service
public class StockOperationService {

    private final StockRepository stockRepository;
    private final StockJournalRepository journalRepository;
    private final ProductRepository productRepository;
    private final WarehouseReferenceRepository referenceRepository;
    private final AnimalRepository animalRepository;
    private final EmployeeService employeeService;
    private final Clock clock;

    public StockOperationService(StockRepository stockRepository,
                                 StockJournalRepository journalRepository,
                                 ProductRepository productRepository,
                                 WarehouseReferenceRepository referenceRepository,
                                 AnimalRepository animalRepository,
                                 EmployeeService employeeService,
                                 Clock clock) {
        this.stockRepository = stockRepository;
        this.journalRepository = journalRepository;
        this.productRepository = productRepository;
        this.referenceRepository = referenceRepository;
        this.animalRepository = animalRepository;
        this.employeeService = employeeService;
        this.clock = clock;
    }

    // ---------- расход ----------

    public PageResponse<ExpenseResponse> findExpenses(Filter filter, int page, int size) {
        Paging.validate(page, size);
        ReceiptService.checkPeriod(filter.from(), filter.to());
        return PageResponse.of(journalRepository.findExpenses(filter, page, size), page, size,
                journalRepository.countExpenses(filter));
    }

    public ExpenseResponse findExpense(Long id) {
        return journalRepository.findExpense(id).orElseThrow(() -> expenseNotFound(id));
    }

    /**
     * Ручной расход со склада. Партию выбирает сотрудник (batchId); без выбора — FEFO на этом складе
     * с добором из следующих партий. Просроченные партии и партии, поступившие позже даты расхода,
     * не используются; при нехватке — 409 «нужно / есть», ничего не сохраняется.
     */
    @Transactional
    public ExpenseResult createExpense(ExpenseRequest request) {
        ProductResponse product = product(request.productId());
        WarehouseResponse warehouse = warehouse(request.warehouseId());
        LocalDate today = LocalDate.now(clock);
        LocalDate date = operationDate(request.expenseDate(), today, "расхода");
        Long animalId = checkAnimal(request.animalId(), date);
        Long employeeId = employeeService.currentEmployeeId();
        String notes = MedicalChecks.trimToNull(request.notes());

        List<Portion> plan;
        MaterialSelection selection;
        if (request.batchId() != null) {
            StockRow row = lockRow(request.batchId(), warehouse);
            if (!row.productId().equals(product.id())) {
                throw new InvalidRequestException("Партия №" + row.batchId() + " — не товар «" + product.name() + "»");
            }
            StockHints.ensureNotExpired(row, today, product.name());
            checkReceivedBefore(row, date, "расхода");
            StockHints.ensureEnough(row, request.quantity(), product.name(), product.unitOfMeasure());
            plan = List.of(new Portion(row, request.quantity()));
            selection = MaterialSelection.MANUAL;
        } else {
            List<StockRow> rows = stockRepository.lockByProduct(product.id(), warehouse.id()).stream()
                    .filter(r -> !r.receivedDate().isAfter(date))
                    .toList();
            Allocation allocation = FefoAllocator.allocate(request.quantity(), rows, today);
            if (!allocation.enough()) {
                throw new BusinessRuleException("Недостаточно «" + product.name() + "» на складе «" + warehouse.name()
                        + "»: нужно " + StockHints.amount(request.quantity(), product.unitOfMeasure())
                        + ", есть " + StockHints.amount(allocation.available(), product.unitOfMeasure())
                        + " (просроченные партии не учитываются)");
            }
            plan = allocation.portions();
            selection = MaterialSelection.FEFO;
        }

        List<ExpenseResponse> expenses = new ArrayList<>();
        for (Portion portion : plan) {
            StockRow row = portion.row();
            stockRepository.decrease(row.batchId(), row.warehouseId(), portion.quantity());
            Long id = stockRepository.insertExpense(row.batchId(), row.warehouseId(), employeeId, animalId, null,
                    portion.quantity(), date, notes);
            expenses.add(findExpense(id));
        }
        return new ExpenseResult(selection, expenses);
    }

    /**
     * Удаление ошибочного ручного расхода: количество возвращается в ту же строку остатка.
     * Расход по процедуре удаляется только вместе с процедурой.
     */
    @Transactional
    public void deleteExpense(Long id) {
        ExpenseRow expense = journalRepository.lockExpense(id).orElseThrow(() -> expenseNotFound(id));
        if (expense.procedureId() != null) {
            throw new BusinessRuleException("Это расход по процедуре №" + expense.procedureId()
                    + ": он удаляется только вместе с процедурой (DELETE /procedures/" + expense.procedureId() + ")");
        }
        stockRepository.lockRow(expense.batchId(), expense.warehouseId()).orElseThrow();
        stockRepository.increase(expense.batchId(), expense.warehouseId(), expense.quantity());
        journalRepository.deleteExpense(id);
    }

    // ---------- перемещение ----------

    public PageResponse<TransferResponse> findTransfers(Filter filter, int page, int size) {
        Paging.validate(page, size);
        ReceiptService.checkPeriod(filter.from(), filter.to());
        return PageResponse.of(journalRepository.findTransfers(filter, page, size), page, size,
                journalRepository.countTransfers(filter));
    }

    /**
     * Перемещение остатка партии на другой склад: склады разные, количество не больше остатка
     * на исходном складе. Просроченную партию перемещать можно (например, на утилизацию).
     */
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw new InvalidRequestException("Склад назначения совпадает с исходным");
        }
        WarehouseResponse from = warehouse(request.fromWarehouseId());
        WarehouseResponse to = warehouse(request.toWarehouseId());
        LocalDate today = LocalDate.now(clock);
        LocalDate date = operationDate(request.transferDate(), today, "перемещения");
        Long employeeId = employeeService.currentEmployeeId();

        if (stockRepository.findRow(request.batchId(), from.id()).isEmpty()) {
            throw new InvalidRequestException("Партии №" + request.batchId() + " нет на складе «" + from.name() + "»");
        }
        stockRepository.ensureRow(request.batchId(), to.id());
        // Обе строки одной партии — в порядке складов, как во всех операциях
        StockRow source = null;
        for (Long warehouseId : List.of(Math.min(from.id(), to.id()), Math.max(from.id(), to.id()))) {
            StockRow row = stockRepository.lockRow(request.batchId(), warehouseId).orElseThrow();
            if (warehouseId.equals(from.id())) {
                source = row;
            }
        }
        ProductInfo product = productInfo(source.productId());
        checkReceivedBefore(source, date, "перемещения");
        StockHints.ensureEnough(source, request.quantity(), product.name(), product.unit());

        stockRepository.decrease(source.batchId(), from.id(), request.quantity());
        stockRepository.increase(source.batchId(), to.id(), request.quantity());
        Long id = journalRepository.insertTransfer(source.batchId(), from.id(), to.id(), employeeId,
                request.quantity(), date, MedicalChecks.trimToNull(request.notes()));
        return journalRepository.findTransfer(id).orElseThrow();
    }

    // ---------- списание ----------

    public PageResponse<WriteOffResponse> findWriteOffs(Filter filter, WriteOffReason reason, int page, int size) {
        Paging.validate(page, size);
        ReceiptService.checkPeriod(filter.from(), filter.to());
        return PageResponse.of(journalRepository.findWriteOffs(filter, reason, page, size), page, size,
                journalRepository.countWriteOffs(filter, reason));
    }

    public WriteOffResponse findWriteOff(Long id) {
        return journalRepository.findWriteOff(id)
                .orElseThrow(() -> new NotFoundException("Списание с id=" + id + " не найдено"));
    }

    /**
     * Списание остатка партии на складе: количество не больше остатка, причина EXPIRED — только для
     * просроченной партии, сотрудник — текущий пользователь.
     */
    @Transactional
    public WriteOffResponse writeOff(WriteOffRequest request) {
        WarehouseResponse warehouse = warehouse(request.warehouseId());
        LocalDate today = LocalDate.now(clock);
        LocalDate date = operationDate(request.writeOffDate(), today, "списания");
        Long employeeId = employeeService.currentEmployeeId();

        StockRow row = lockRow(request.batchId(), warehouse);
        ProductInfo product = productInfo(row.productId());
        if (request.reason() == WriteOffReason.EXPIRED && !FefoAllocator.isExpired(row, today)) {
            throw new BusinessRuleException("Партия №" + row.batchId() + " («" + product.name() + "») не просрочена"
                    + (row.expirationDate() == null ? " (срока годности нет)" : " (годна до " + row.expirationDate() + ")")
                    + ": укажите другую причину списания");
        }
        checkReceivedBefore(row, date, "списания");
        StockHints.ensureEnough(row, request.quantity(), product.name(), product.unit());

        stockRepository.decrease(row.batchId(), row.warehouseId(), request.quantity());
        Long id = journalRepository.insertWriteOff(row.batchId(), row.warehouseId(), employeeId, request.quantity(),
                date, request.reason(), MedicalChecks.trimToNull(request.notes()));
        return findWriteOff(id);
    }

    /** Предпросмотр «списать все просроченные»: что и сколько будет списано, и контрольный признак списка. */
    public ExpiredWriteOffPreview previewExpired() {
        LocalDate today = LocalDate.now(clock);
        List<StockRow> rows = stockRepository.findExpired(today);
        return new ExpiredWriteOffPreview(token(today, rows), today, previewItems(rows));
    }

    /**
     * Списание всех просроченных остатков по подтверждённому предпросмотру. Если остатки с момента
     * предпросмотра изменились (другой token) — 409, ничего не списывается.
     */
    @Transactional
    public List<WriteOffResponse> writeOffExpired(ExpiredWriteOffRequest request) {
        LocalDate today = LocalDate.now(clock);
        List<StockRow> rows = stockRepository.lockExpired(today);
        if (!token(today, rows).equals(request.token())) {
            throw new BusinessRuleException("Просроченные остатки изменились после предпросмотра: "
                    + "обновите предпросмотр (GET /write-offs/expired/preview) и подтвердите заново");
        }
        Long employeeId = employeeService.currentEmployeeId();
        String notes = MedicalChecks.trimToNull(request.notes());
        List<WriteOffResponse> result = new ArrayList<>();
        for (StockRow row : rows) {
            stockRepository.decrease(row.batchId(), row.warehouseId(), row.quantity());
            Long id = journalRepository.insertWriteOff(row.batchId(), row.warehouseId(), employeeId, row.quantity(),
                    today, WriteOffReason.EXPIRED, notes != null ? notes : "Списание всех просроченных");
            result.add(findWriteOff(id));
        }
        return result;
    }

    private List<ExpiredWriteOffPreview.Item> previewItems(List<StockRow> rows) {
        Map<Long, ProductInfo> products = stockRepository.findProducts(rows.stream().map(StockRow::productId).toList());
        return rows.stream()
                .map(r -> {
                    ProductInfo p = products.get(r.productId());
                    return new ExpiredWriteOffPreview.Item(r.batchId(), r.warehouseId(), r.warehouseName(),
                            p.id(), p.name(), p.unit(), r.expirationDate(), r.quantity());
                })
                .toList();
    }

    /** Контрольный признак списка: дата и строки «партия/склад/количество» — SHA-256. */
    static String token(LocalDate today, List<StockRow> rows) {
        StringBuilder source = new StringBuilder(today.toString());
        rows.stream()
                .sorted(java.util.Comparator.comparing(StockRow::batchId).thenComparing(StockRow::warehouseId))
                .forEach(r -> source.append('|').append(r.batchId()).append('/').append(r.warehouseId())
                        .append('/').append(r.quantity().stripTrailingZeros().toPlainString()));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- общее ----------

    private ProductResponse product(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new InvalidRequestException("Товар с id=" + id + " не найден"));
    }

    private ProductInfo productInfo(Long id) {
        return stockRepository.findProduct(id).orElseThrow();
    }

    private WarehouseResponse warehouse(Long id) {
        return referenceRepository.findWarehouse(id)
                .orElseThrow(() -> new InvalidRequestException("Склад с id=" + id + " не найден"));
    }

    private StockRow lockRow(Long batchId, WarehouseResponse warehouse) {
        return stockRepository.lockRow(batchId, warehouse.id())
                .orElseThrow(() -> new InvalidRequestException("Партии №" + batchId + " нет на складе «"
                        + warehouse.name() + "»"));
    }

    private static LocalDate operationDate(LocalDate requested, LocalDate today, String what) {
        LocalDate date = requested != null ? requested : today;
        if (date.isAfter(today)) {
            throw new InvalidRequestException("Дата " + what + " не может быть в будущем");
        }
        return date;
    }

    private static void checkReceivedBefore(StockRow row, LocalDate date, String what) {
        if (date.isBefore(row.receivedDate())) {
            throw new BusinessRuleException("Дата " + what + " " + date + " раньше поступления партии №"
                    + row.batchId() + " (" + row.receivedDate() + ")");
        }
    }

    /** Расход на животное: животное существует, не выбыло и поступило не позже даты расхода. */
    private Long checkAnimal(Long animalId, LocalDate date) {
        if (animalId == null) {
            return null;
        }
        AnimalView animal = animalRepository.findById(animalId)
                .orElseThrow(() -> new InvalidRequestException("Животное с id=" + animalId + " не найдено"));
        if (animal.departureDate() != null) {
            throw new BusinessRuleException("Животное выбыло " + animal.departureDate() + " («" + animal.statusName()
                    + "»): расход на него оформить нельзя");
        }
        if (date.isBefore(animal.arrivalDate())) {
            throw new BusinessRuleException("Дата расхода " + date + " раньше поступления животного " + animal.arrivalDate());
        }
        return animalId;
    }

    private static NotFoundException expenseNotFound(Long id) {
        return new NotFoundException("Расход с id=" + id + " не найден");
    }
}
