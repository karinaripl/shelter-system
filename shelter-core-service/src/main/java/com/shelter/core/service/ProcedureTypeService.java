package com.shelter.core.service;

import com.shelter.core.dto.ConsumptionRateRequest;
import com.shelter.core.dto.ProcedureMaterialsResponse;
import com.shelter.core.dto.ProcedureTypeRequest;
import com.shelter.core.dto.ProcedureTypeResponse;
import com.shelter.core.dto.ProcedureTypeResponse.Rate;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.MedicalMapper;
import com.shelter.core.repository.ProcedureTypeRepository;
import com.shelter.core.repository.ProductInfo;
import com.shelter.core.repository.StockRepository;
import com.shelter.core.repository.StockRow;
import com.shelter.model.generated.shelter.tables.records.ProcedureTypeRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** Виды процедур и нормы расхода: читают ADMIN, VET, CURATOR; изменяют ADMIN и VET. */
@Service
public class ProcedureTypeService {

    private final ProcedureTypeRepository procedureTypeRepository;
    private final StockRepository stockRepository;
    private final MedicalMapper mapper;
    private final Clock clock;

    public ProcedureTypeService(ProcedureTypeRepository procedureTypeRepository,
                                StockRepository stockRepository,
                                MedicalMapper mapper,
                                Clock clock) {
        this.procedureTypeRepository = procedureTypeRepository;
        this.stockRepository = stockRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ---------- чтение ----------

    public List<ProcedureTypeResponse> findAll() {
        List<ProcedureTypeRecord> types = procedureTypeRepository.findAll();
        Map<Long, List<Rate>> rates = procedureTypeRepository.ratesOf(types.stream().map(ProcedureTypeRecord::getId).toList());
        return types.stream()
                .map(t -> mapper.toTypeResponse(t, rates.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    public ProcedureTypeResponse findById(Long id) {
        return mapper.toTypeResponse(type(id), procedureTypeRepository.rates(id));
    }

    /**
     * Подсказка перед процедурой: по каждому материалу нормы — годные партии в порядке FEFO
     * (первая рекомендуемая) и отдельно просроченные с пометкой «подлежит списанию».
     */
    public ProcedureMaterialsResponse materials(Long id) {
        ProcedureTypeRecord type = type(id);
        LocalDate today = LocalDate.now(clock);
        List<ProcedureMaterialsResponse.Material> materials = procedureTypeRepository.rates(id).stream()
                .map(rate -> material(rate, stockRepository.findByProduct(rate.productId()), today))
                .toList();
        boolean enough = materials.stream().allMatch(ProcedureMaterialsResponse.Material::enough);
        return new ProcedureMaterialsResponse(type.getId(), type.getName(), enough, materials);
    }

    private static ProcedureMaterialsResponse.Material material(Rate rate, List<StockRow> rows, LocalDate today) {
        List<StockRow> usable = FefoAllocator.usable(rows, today);
        BigDecimal available = FefoAllocator.total(usable);
        List<ProcedureMaterialsResponse.BatchOption> batches = IntStream.range(0, usable.size())
                .mapToObj(i -> {
                    StockRow r = usable.get(i);
                    return new ProcedureMaterialsResponse.BatchOption(r.batchId(), r.warehouseId(), r.warehouseName(),
                            r.receivedDate(), r.expirationDate(), r.quantity(), i == 0);
                })
                .toList();
        List<ProcedureMaterialsResponse.ExpiredBatch> expired = FefoAllocator.expired(rows, today).stream()
                .map(r -> new ProcedureMaterialsResponse.ExpiredBatch(r.batchId(), r.warehouseId(), r.warehouseName(),
                        r.expirationDate(), r.quantity(), ProcedureMaterialsResponse.EXPIRED_NOTE))
                .toList();
        return new ProcedureMaterialsResponse.Material(rate.productId(), rate.productName(), rate.unit(),
                rate.quantity(), available, available.compareTo(rate.quantity()) >= 0, batches, expired);
    }

    // ---------- изменение ----------

    @Transactional
    public ProcedureTypeResponse create(ProcedureTypeRequest request) {
        String name = request.name().trim();
        checkNameFree(name, null);
        if (request.rates() != null) {
            validateRates(request.rates());
        }
        Long id = procedureTypeRepository.insert(name, MedicalChecks.trimToNull(request.description()),
                request.cardFlag() == null ? null : request.cardFlag().name());
        if (request.rates() != null) {
            procedureTypeRepository.replaceRates(id, request.rates());
        }
        return findById(id);
    }

    /** Изменение названия, описания и флага; нормы — если переданы (заменяют прежние целиком). */
    @Transactional
    public ProcedureTypeResponse update(Long id, ProcedureTypeRequest request) {
        type(id);
        String name = request.name().trim();
        checkNameFree(name, id);
        if (request.rates() != null) {
            validateRates(request.rates());
        }
        procedureTypeRepository.update(id, name, MedicalChecks.trimToNull(request.description()),
                request.cardFlag() == null ? null : request.cardFlag().name());
        if (request.rates() != null) {
            procedureTypeRepository.replaceRates(id, request.rates());
        }
        return findById(id);
    }

    /** Новый список норм заменяет прежний целиком. */
    @Transactional
    public ProcedureTypeResponse replaceRates(Long id, List<ConsumptionRateRequest> rates) {
        type(id);
        validateRates(rates);
        procedureTypeRepository.replaceRates(id, rates);
        return findById(id);
    }

    /** Вид процедуры, по которому уже есть записи, удалить нельзя — история не должна исчезать. */
    @Transactional
    public void delete(Long id) {
        ProcedureTypeRecord type = type(id);
        if (procedureTypeRepository.hasRecords(id)) {
            throw new BusinessRuleException("Нельзя удалить вид процедуры «" + type.getName()
                    + "»: по нему уже есть записи процедур");
        }
        procedureTypeRepository.delete(id);
    }

    // ---------- проверки ----------

    private void checkNameFree(String name, Long excludeId) {
        if (procedureTypeRepository.nameTaken(name, excludeId)) {
            throw new BusinessRuleException("Вид процедуры «" + name + "» уже есть");
        }
    }

    /** Товар в списке норм один раз, существует и в обороте; количество больше нуля проверяет валидация. */
    private void validateRates(List<ConsumptionRateRequest> rates) {
        Set<Long> seen = new HashSet<>();
        for (ConsumptionRateRequest rate : rates) {
            if (!seen.add(rate.productId())) {
                throw new InvalidRequestException("Товар id=" + rate.productId() + " указан в нормах дважды");
            }
        }
        Map<Long, ProductInfo> products = stockRepository.findProducts(seen);
        for (ConsumptionRateRequest rate : rates) {
            ProductInfo product = products.get(rate.productId());
            if (product == null) {
                throw new InvalidRequestException("Товар id=" + rate.productId() + " не найден");
            }
            if (!product.active()) {
                throw new InvalidRequestException("Товар «" + product.name() + "» выведен из оборота: "
                        + "его нельзя включать в нормы");
            }
        }
    }

    private ProcedureTypeRecord type(Long id) {
        return procedureTypeRepository.findById(id).orElseThrow(() -> NotFoundException.procedureType(id));
    }
}
