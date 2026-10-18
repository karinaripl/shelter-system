package com.shelter.core.service;

import com.shelter.core.dto.CardFlag;
import com.shelter.core.dto.ConsumptionRateRequest;
import com.shelter.core.dto.BatchOption;
import com.shelter.core.dto.ExpiredBatch;
import com.shelter.core.dto.ProcedureMaterialsResponse;
import com.shelter.core.dto.ProcedureTypeRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.MedicalMapper;
import com.shelter.core.repository.ProcedureTypeRepository;
import com.shelter.core.repository.ProductInfo;
import com.shelter.core.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.SYRINGE;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static com.shelter.core.service.MedicalTestData.VACCINE;
import static com.shelter.core.service.MedicalTestData.rate;
import static com.shelter.core.service.MedicalTestData.stock;
import static com.shelter.core.service.MedicalTestData.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcedureTypeServiceTest {

    private static final long TYPE_ID = 2L;

    private ProcedureTypeRepository repository;
    private StockRepository stockRepository;
    private ProcedureTypeService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProcedureTypeRepository.class);
        stockRepository = mock(StockRepository.class);
        service = new ProcedureTypeService(repository, stockRepository, new MedicalMapper(), CLOCK);

        when(repository.findById(TYPE_ID)).thenReturn(Optional.of(type(TYPE_ID, "Вакцинация", "VACCINATED")));
        when(repository.insert(anyString(), any(), any())).thenReturn(TYPE_ID);
        when(stockRepository.findProducts(any())).thenReturn(Map.of(
                VACCINE, new ProductInfo(VACCINE, "Вакцина комплексная", "доза", true),
                SYRINGE, new ProductInfo(SYRINGE, "Шприц 5 мл", "шт", false)));
    }

    private static ConsumptionRateRequest rateRequest(long product, String quantity) {
        return new ConsumptionRateRequest(product, new BigDecimal(quantity), null);
    }

    @Test
    void createsTypeWithFlagAndRates() {
        List<ConsumptionRateRequest> rates = List.of(rateRequest(VACCINE, "1"));

        service.create(new ProcedureTypeRequest("  Ревакцинация ", null, CardFlag.VACCINATED, rates));

        verify(repository).insert("Ревакцинация", null, "VACCINATED");
        verify(repository).replaceRates(TYPE_ID, rates);
    }

    @Test
    void nameMustBeUnique() {
        when(repository.nameTaken("Вакцинация", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ProcedureTypeRequest("Вакцинация", null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("уже есть");
        verify(repository, never()).insert(anyString(), any(), any());
    }

    @Test
    void renameChecksUniquenessExcludingItself() {
        service.update(TYPE_ID, new ProcedureTypeRequest("Вакцинация", "Новое описание", CardFlag.VACCINATED, null));

        verify(repository).nameTaken("Вакцинация", TYPE_ID);
        verify(repository).update(TYPE_ID, "Вакцинация", "Новое описание", "VACCINATED");
        verify(repository, never()).replaceRates(anyLong(), any());
    }

    @Test
    void ratesReplaceWholeList() {
        List<ConsumptionRateRequest> rates = List.of(rateRequest(VACCINE, "2"));

        service.replaceRates(TYPE_ID, rates);

        verify(repository).replaceRates(TYPE_ID, rates);
    }

    @Test
    void sameProductTwiceInRatesIsRejected() {
        assertThatThrownBy(() -> service.replaceRates(TYPE_ID,
                List.of(rateRequest(VACCINE, "1"), rateRequest(VACCINE, "2"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("дважды");
    }

    @Test
    void unknownProductInRatesIsRejected() {
        assertThatThrownBy(() -> service.replaceRates(TYPE_ID, List.of(rateRequest(404L, "1"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("не найден");
        verify(repository, never()).replaceRates(anyLong(), any());
    }

    @Test
    void inactiveProductInRatesIsRejected() {
        assertThatThrownBy(() -> service.replaceRates(TYPE_ID, List.of(rateRequest(SYRINGE, "1"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("выведен из оборота");
    }

    @Test
    void typeWithRecordsCannotBeDeleted() {
        when(repository.hasRecords(TYPE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(TYPE_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("уже есть записи процедур");
        verify(repository, never()).delete(any());
    }

    @Test
    void typeWithoutRecordsIsDeleted() {
        service.delete(TYPE_ID);

        verify(repository).delete(TYPE_ID);
    }

    @Test
    void unknownTypeIsNotFound() {
        assertThatThrownBy(() -> service.findById(404L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(404L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void materialsRecommendFefoBatchAndListExpiredSeparately() {
        when(repository.rates(TYPE_ID)).thenReturn(List.of(rate(VACCINE, "Вакцина комплексная", "доза", "1")));
        when(stockRepository.findByProduct(VACCINE)).thenReturn(List.of(
                stock(17L, VACCINE, TODAY.minusDays(1), "5"),
                stock(19L, VACCINE, TODAY.plusDays(300), "20"),
                stock(18L, VACCINE, TODAY.plusDays(30), "10")));

        ProcedureMaterialsResponse response = service.materials(TYPE_ID);

        ProcedureMaterialsResponse.Material vaccine = response.materials().getFirst();
        assertThat(vaccine.batches()).extracting(BatchOption::batchId).containsExactly(18L, 19L);
        assertThat(vaccine.batches().get(0).recommended()).isTrue();
        assertThat(vaccine.batches().get(1).recommended()).isFalse();
        assertThat(vaccine.expiredBatches()).singleElement().satisfies(b -> {
            assertThat(b.batchId()).isEqualTo(17L);
            assertThat(b.note()).isEqualTo("просрочено — подлежит списанию");
        });
        assertThat(vaccine.totalAvailable()).isEqualByComparingTo("30");
        assertThat(response.enough()).isTrue();
    }

    @Test
    void materialsReportShortage() {
        when(repository.rates(TYPE_ID)).thenReturn(List.of(rate(VACCINE, "Вакцина комплексная", "доза", "1")));
        when(stockRepository.findByProduct(VACCINE)).thenReturn(List.of(stock(17L, VACCINE, TODAY.minusDays(1), "5")));

        ProcedureMaterialsResponse response = service.materials(TYPE_ID);

        assertThat(response.enough()).isFalse();
        assertThat(response.materials().getFirst().batches()).isEmpty();
    }
}
