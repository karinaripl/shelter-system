package com.shelter.core.service;

import com.shelter.core.dto.CardFlag;
import com.shelter.core.dto.ConsumedMaterial;
import com.shelter.core.dto.MaterialChoice;
import com.shelter.core.dto.MaterialSelection;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.ProcedureDeletionResponse;
import com.shelter.core.dto.ProcedureRequest;
import com.shelter.core.dto.ProcedureResponse;
import com.shelter.core.dto.ProcedureTypeResponse.Rate;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.MedicalMapper;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.AnimalView;
import com.shelter.core.repository.MedicalCardRepository;
import com.shelter.core.repository.ProcedureRepository;
import com.shelter.core.repository.ProcedureTypeRepository;
import com.shelter.core.repository.ProcedureView;
import com.shelter.core.repository.StockRepository;
import com.shelter.core.repository.StockRow;
import com.shelter.core.service.FefoAllocator.Allocation;
import com.shelter.core.service.FefoAllocator.Portion;
import com.shelter.model.generated.shelter.tables.records.MedicalCardRecord;
import com.shelter.model.generated.shelter.tables.records.ProcedureTypeRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Процедуры: внесение со списанием материалов по нормам и удаление с возвратом материалов.
 * Всё изменение — в одной транзакции: при любой ошибке не сохраняется ни процедура, ни расход.
 */
@Service
public class ProcedureService {

    private final AnimalRepository animalRepository;
    private final MedicalCardRepository medicalCardRepository;
    private final ProcedureRepository procedureRepository;
    private final ProcedureTypeRepository procedureTypeRepository;
    private final StockRepository stockRepository;
    private final MedicalCardService medicalCardService;
    private final EmployeeService employeeService;
    private final MedicalMapper mapper;
    private final Clock clock;

    public ProcedureService(AnimalRepository animalRepository,
                            MedicalCardRepository medicalCardRepository,
                            ProcedureRepository procedureRepository,
                            ProcedureTypeRepository procedureTypeRepository,
                            StockRepository stockRepository,
                            MedicalCardService medicalCardService,
                            EmployeeService employeeService,
                            MedicalMapper mapper,
                            Clock clock) {
        this.animalRepository = animalRepository;
        this.medicalCardRepository = medicalCardRepository;
        this.procedureRepository = procedureRepository;
        this.procedureTypeRepository = procedureTypeRepository;
        this.stockRepository = stockRepository;
        this.medicalCardService = medicalCardService;
        this.employeeService = employeeService;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ---------- чтение ----------

    public PageResponse<ProcedureResponse> findPage(Long animalId, int page, int size) {
        Paging.validate(page, size);
        animalRepository.findById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        return medicalCardRepository.findByAnimalId(animalId)
                .map(card -> {
                    List<ProcedureView> procedures = procedureRepository.findPage(card.getId(), page, size);
                    Map<Long, List<ConsumedMaterial>> materials =
                            stockRepository.expensesOfProcedures(procedures.stream().map(ProcedureView::id).toList());
                    List<ProcedureResponse> content = procedures.stream()
                            .map(p -> mapper.toProcedureResponse(p, materials.getOrDefault(p.id(), List.of()), null))
                            .toList();
                    return PageResponse.of(content, page, size, procedureRepository.count(card.getId()));
                })
                .orElseGet(() -> PageResponse.of(List.of(), page, size, 0));
    }

    public ProcedureResponse findById(Long id) {
        ProcedureView procedure = procedureRepository.findById(id).orElseThrow(() -> NotFoundException.procedure(id));
        return mapper.toProcedureResponse(procedure, stockRepository.expensesOfProcedure(id), null);
    }

    // ---------- внесение ----------

    /**
     * Процедура: дата не в будущем и не раньше поступления, животное не выбыло, исполнитель — текущий
     * сотрудник. Материалы норм списываются из партий, выбранных сотрудником, или (если список не передан)
     * из рекомендованных по FEFO. Если вид процедуры ставит флаг медкарты и флаг был снят — флаг ставится,
     * и процедура запоминает, что поставила его она.
     */
    @Transactional
    public ProcedureResponse create(Long animalId, ProcedureRequest request) {
        AnimalView animal = animalRepository.lockById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        MedicalChecks.ensureNotDeparted(animal);
        LocalDate today = LocalDate.now(clock);
        LocalDate date = request.procedureDate() != null ? request.procedureDate() : today;
        MedicalChecks.checkDate(date, today, animal, "процедуры");

        ProcedureTypeRecord type = procedureTypeRepository.findById(request.procedureTypeId())
                .orElseThrow(() -> new InvalidRequestException(
                        "Вид процедуры с id=" + request.procedureTypeId() + " не найден"));
        List<Rate> rates = procedureTypeRepository.rates(type.getId());
        Long employeeId = employeeService.currentEmployeeId();

        boolean manual = request.materials() != null && !request.materials().isEmpty();
        List<Portion> plan = manual
                ? planManual(type, rates, request.materials(), today)
                : planFefo(type, rates, today);

        MedicalCardRecord card = medicalCardService.lockCard(animal);
        CardFlag flag = MedicalMapper.cardFlag(type.getCardFlag());
        CardFlag setByThis = null;
        if (flag != null && !MedicalCardRepository.flagValue(card, flag)) {
            medicalCardRepository.setFlag(card.getId(), flag, true);
            setByThis = flag;
        }

        Long id = procedureRepository.insert(card.getId(), type.getId(), employeeId, date,
                MedicalChecks.trimToNull(request.result()), MedicalChecks.trimToNull(request.notes()),
                setByThis == null ? null : setByThis.name());
        String expenseNote = (manual ? "Процедура, партия выбрана вручную: " : "Процедура, по норме (FEFO): ")
                + type.getName();
        for (Portion portion : plan) {
            StockRow row = portion.row();
            stockRepository.decrease(row.batchId(), row.warehouseId(), portion.quantity());
            stockRepository.insertExpense(row.batchId(), row.warehouseId(), employeeId, animal.id(), id,
                    portion.quantity(), date, expenseNote);
        }

        ProcedureView saved = procedureRepository.findById(id).orElseThrow();
        return mapper.toProcedureResponse(saved, stockRepository.expensesOfProcedure(id),
                manual ? MaterialSelection.MANUAL : MaterialSelection.FEFO);
    }

    /** Рекомендованные партии по FEFO для каждого материала нормы; при нехватке хотя бы одного — 409. */
    private List<Portion> planFefo(ProcedureTypeRecord type, List<Rate> rates, LocalDate today) {
        List<Portion> plan = new ArrayList<>();
        List<String> shortages = new ArrayList<>();
        // Строки остатков блокируются в одном порядке (по товару), чтобы параллельные процедуры не ждали друг друга по кругу
        for (Rate rate : rates.stream().sorted(Comparator.comparing(Rate::productId)).toList()) {
            Allocation allocation = FefoAllocator.allocate(rate.quantity(),
                    stockRepository.lockByProduct(rate.productId()), today);
            if (allocation.enough()) {
                plan.addAll(allocation.portions());
            } else {
                shortages.add("«" + rate.productName() + "» — нужно " + amount(rate.quantity(), rate.unit())
                        + ", есть " + amount(allocation.available(), rate.unit()));
            }
        }
        if (!shortages.isEmpty()) {
            throw new BusinessRuleException("Недостаточно материалов на складе для процедуры «" + type.getName()
                    + "» (просроченные партии не учитываются): " + String.join("; ", shortages)
                    + ". Процедура не сохранена");
        }
        return plan;
    }

    /**
     * Партии, выбранные сотрудником. Только товары из норм (материалы вне норм — ручной расход склада,
     * который ветеринар не оформляет), и все товары норм должны быть указаны. Партия должна быть этого товара,
     * не просрочена и с достаточным остатком.
     */
    private List<Portion> planManual(ProcedureTypeRecord type, List<Rate> rates, List<MaterialChoice> choices,
                                     LocalDate today) {
        Map<Long, Rate> rateByProduct = rates.stream().collect(Collectors.toMap(Rate::productId, Function.identity()));
        for (MaterialChoice choice : choices) {
            if (!rateByProduct.containsKey(choice.productId())) {
                throw new InvalidRequestException("Товар id=" + choice.productId() + " не входит в нормы расхода "
                        + "процедуры «" + type.getName() + "». Материалы вне норм по процедуре не списываются: "
                        + "добавьте товар в нормы вида процедуры");
            }
        }
        Set<Long> chosenProducts = choices.stream().map(MaterialChoice::productId).collect(Collectors.toSet());
        for (Rate rate : rates) {
            if (!chosenProducts.contains(rate.productId())) {
                throw new InvalidRequestException("Не указан материал «" + rate.productName() + "» из норм процедуры «"
                        + type.getName() + "». Укажите партию или не передавайте список — тогда партии подберутся по FEFO");
            }
        }
        Set<String> seenRows = new HashSet<>();
        for (MaterialChoice choice : choices) {
            if (!seenRows.add(choice.batchId() + "/" + choice.warehouseId())) {
                throw new InvalidRequestException("Партия №" + choice.batchId() + " на складе id="
                        + choice.warehouseId() + " указана дважды");
            }
        }
        Map<Long, List<MaterialChoice>> byProduct =
                choices.stream().collect(Collectors.groupingBy(MaterialChoice::productId));
        byProduct.forEach((productId, list) -> {
            if (list.size() > 1 && list.stream().anyMatch(c -> c.quantity() == null)) {
                throw new InvalidRequestException("Материал «" + rateByProduct.get(productId).productName()
                        + "» взят из нескольких партий: укажите количество (quantity) для каждой");
            }
        });

        List<Portion> plan = new ArrayList<>();
        List<String> expired = new ArrayList<>();
        List<String> shortages = new ArrayList<>();
        List<MaterialChoice> ordered = choices.stream()
                .sorted(Comparator.comparing(MaterialChoice::batchId).thenComparing(MaterialChoice::warehouseId))
                .toList();
        for (MaterialChoice choice : ordered) {
            Rate rate = rateByProduct.get(choice.productId());
            StockRow row = stockRepository.lockRow(choice.batchId(), choice.warehouseId())
                    .orElseThrow(() -> new InvalidRequestException("Партия №" + choice.batchId()
                            + " на складе id=" + choice.warehouseId() + " не найдена"));
            if (!row.productId().equals(choice.productId())) {
                throw new InvalidRequestException("Партия №" + row.batchId() + " — не товар «" + rate.productName() + "»");
            }
            String where = "«" + rate.productName() + "», партия №" + row.batchId() + ", склад «" + row.warehouseName() + "»";
            if (FefoAllocator.isExpired(row, today)) {
                expired.add(where + " — срок годности истёк " + row.expirationDate());
                continue;
            }
            BigDecimal quantity = choice.quantity() != null ? choice.quantity() : rate.quantity();
            if (quantity.compareTo(row.quantity()) > 0) {
                shortages.add(where + " — нужно " + amount(quantity, rate.unit()) + ", есть " + amount(row.quantity(), rate.unit()));
                continue;
            }
            plan.add(new Portion(row, quantity));
        }
        if (!expired.isEmpty()) {
            throw new BusinessRuleException("Партия просрочена, её нужно списать: для животных просроченные партии "
                    + "не используются. " + String.join("; ", expired) + ". Процедура не сохранена");
        }
        if (!shortages.isEmpty()) {
            throw new BusinessRuleException("Недостаточно остатка в выбранных партиях: " + String.join("; ", shortages)
                    + ". Процедура не сохранена");
        }
        return plan;
    }

    // ---------- удаление ----------

    /**
     * Удаление ошибочной процедуры: материалы возвращаются в те же партии. Флаг медкарты снимается,
     * только если его поставила эта процедура и у животного не осталось других процедур с тем же флагом;
     * флаг, стоявший до процедуры, не трогается.
     */
    @Transactional
    public ProcedureDeletionResponse delete(Long id) {
        ProcedureView procedure = procedureRepository.lockById(id).orElseThrow(() -> NotFoundException.procedure(id));
        AnimalView animal = animalRepository.lockById(procedure.animalId())
                .orElseThrow(() -> NotFoundException.animal(procedure.animalId()));
        MedicalChecks.ensureNotDeparted(animal);
        MedicalCardRecord card = medicalCardService.lockCard(animal);

        List<ConsumedMaterial> materials = stockRepository.expensesOfProcedure(id);
        for (ConsumedMaterial material : materials) {
            stockRepository.increase(material.batchId(), material.warehouseId(), material.quantity());
        }
        stockRepository.deleteExpensesOfProcedure(id);
        procedureRepository.delete(id);

        StringBuilder message = new StringBuilder("Процедура «" + procedure.procedureTypeName() + "» удалена.");
        message.append(materials.isEmpty() ? " Материалы по ней не списывались." : " Материалы возвращены на склад.");

        CardFlag setByThis = MedicalMapper.cardFlag(procedure.flagSetByThis());
        CardFlag typeFlag = MedicalMapper.cardFlag(procedure.procedureTypeCardFlag());
        CardFlag cleared = null;
        if (setByThis != null) {
            if (procedureRepository.existsOtherWithFlag(card.getId(), setByThis.name(), id)) {
                message.append(" Флаг «").append(setByThis.title())
                        .append("» оставлен: у животного есть другие процедуры, которые его ставят.");
            } else {
                medicalCardRepository.setFlag(card.getId(), setByThis, false);
                cleared = setByThis;
                message.append(" Флаг «").append(setByThis.title()).append("» снят: его поставила эта процедура.");
            }
        } else if (typeFlag != null) {
            message.append(" Флаг «").append(typeFlag.title())
                    .append("» не изменён: он был отмечен до этой процедуры.");
        }
        return new ProcedureDeletionResponse(id, materials, cleared, message.toString());
    }

    private static String amount(BigDecimal quantity, String unit) {
        return MedicalChecks.format(quantity) + " " + unit;
    }
}
