package com.shelter.core.service;

import com.shelter.core.dto.CategoryResponse;
import com.shelter.core.dto.ExpenseRequest;
import com.shelter.core.dto.ExpenseResult;
import com.shelter.core.dto.ExpiredWriteOffPreview;
import com.shelter.core.dto.ExpiredWriteOffRequest;
import com.shelter.core.dto.MaterialSelection;
import com.shelter.core.dto.ProductResponse;
import com.shelter.core.dto.TransferRequest;
import com.shelter.core.dto.WarehouseResponse;
import com.shelter.core.dto.WriteOffReason;
import com.shelter.core.dto.WriteOffRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.EmployeeNotLinkedException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.ProductInfo;
import com.shelter.core.repository.ProductRepository;
import com.shelter.core.repository.StockJournalRepository;
import com.shelter.core.repository.StockJournalRepository.ExpenseRow;
import com.shelter.core.repository.StockRepository;
import com.shelter.core.repository.StockRow;
import com.shelter.core.repository.WarehouseReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.ANIMAL_ID;
import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.EMPLOYEE_ID;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static com.shelter.core.service.MedicalTestData.animal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockOperationServiceTest {

    private static final long FEED = 3L;
    private static final long MAIN = 1L;
    private static final long QUARANTINE = 3L;
    private static final long NEAR = 30L;
    private static final long FAR = 31L;
    private static final long EXPIRED = 32L;
    private static final long FRESH = 33L;

    private StockRepository stockRepository;
    private StockJournalRepository journalRepository;
    private ProductRepository productRepository;
    private WarehouseReferenceRepository referenceRepository;
    private AnimalRepository animalRepository;
    private EmployeeService employeeService;
    private StockOperationService service;

    private static StockRow row(long batch, long warehouse, LocalDate received, LocalDate expiration, String quantity) {
        return new StockRow(batch, warehouse, warehouse == MAIN ? "Основной склад" : "Карантинный блок", FEED,
                received, expiration, new BigDecimal(quantity));
    }

    private static StockRow near() {
        return row(NEAR, MAIN, TODAY.minusDays(50), TODAY.plusDays(10), "10");
    }

    private static StockRow far() {
        return row(FAR, MAIN, TODAY.minusDays(50), TODAY.plusDays(300), "20");
    }

    private static StockRow expired() {
        return row(EXPIRED, MAIN, TODAY.minusDays(400), TODAY.minusDays(1), "5");
    }

    /** Партия, поступившая сегодня — для расхода задним числом она ещё не существовала. */
    private static StockRow fresh() {
        return row(FRESH, MAIN, TODAY, TODAY.plusDays(500), "50");
    }

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        journalRepository = mock(StockJournalRepository.class);
        productRepository = mock(ProductRepository.class);
        referenceRepository = mock(WarehouseReferenceRepository.class);
        animalRepository = mock(AnimalRepository.class);
        employeeService = mock(EmployeeService.class);
        service = new StockOperationService(stockRepository, journalRepository, productRepository, referenceRepository,
                animalRepository, employeeService, CLOCK);

        when(productRepository.findById(FEED)).thenReturn(Optional.of(
                new ProductResponse(FEED, "Корм для взрослых кошек", new CategoryResponse(1L, "Корм"), "кг", true, null)));
        when(stockRepository.findProduct(FEED)).thenReturn(Optional.of(new ProductInfo(FEED, "Корм для взрослых кошек", "кг", true)));
        when(stockRepository.findProducts(any())).thenReturn(Map.of(FEED, new ProductInfo(FEED, "Корм для взрослых кошек", "кг", true)));
        when(referenceRepository.findWarehouse(MAIN)).thenReturn(Optional.of(new WarehouseResponse(MAIN, "Основной склад", null, null)));
        when(referenceRepository.findWarehouse(QUARANTINE)).thenReturn(Optional.of(new WarehouseResponse(QUARANTINE, "Карантинный блок", null, null)));
        when(employeeService.currentEmployeeId()).thenReturn(EMPLOYEE_ID);
        when(stockRepository.lockByProduct(FEED, MAIN)).thenReturn(List.of(expired(), far(), near(), fresh()));
        when(stockRepository.lockRow(anyLong(), anyLong())).thenAnswer(inv -> {
            long batch = inv.getArgument(0);
            long warehouse = inv.getArgument(1);
            if (warehouse == QUARANTINE) return Optional.of(row(batch, QUARANTINE, TODAY.minusDays(50), TODAY.plusDays(10), "0"));
            if (batch == NEAR) return Optional.of(near());
            if (batch == FAR) return Optional.of(far());
            if (batch == EXPIRED) return Optional.of(expired());
            if (batch == FRESH) return Optional.of(fresh());
            return Optional.empty();
        });
        when(stockRepository.findRow(anyLong(), anyLong())).thenAnswer(inv -> stockRepository.lockRow(inv.getArgument(0), inv.getArgument(1)));
        when(stockRepository.insertExpense(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(100L, 101L);
        when(journalRepository.findExpense(anyLong())).thenReturn(Optional.of(mock(com.shelter.core.dto.ExpenseResponse.class)));
    }

    private static ExpenseRequest expense(Long batchId, String quantity) {
        return new ExpenseRequest(FEED, MAIN, batchId, new BigDecimal(quantity), null, null, "Кормление");
    }

    private void assertNoStockWrites() {
        verify(stockRepository, never()).decrease(any(), any(), any());
        verify(stockRepository, never()).increase(any(), any(), any());
        verify(stockRepository, never()).insertExpense(any(), any(), any(), any(), any(), any(), any(), any());
        verify(journalRepository, never()).insertWriteOff(any(), any(), any(), any(), any(), any(), any());
        verify(journalRepository, never()).insertTransfer(any(), any(), any(), any(), any(), any(), any());
    }

    // ---------- ручной расход ----------

    @Nested
    class Expense {

        @Test
        void withoutBatchFefoTakesNearestThenNextAndSkipsExpired() {
            ExpenseResult result = service.createExpense(expense(null, "12"));

            verify(stockRepository).decrease(NEAR, MAIN, new BigDecimal("10"));
            verify(stockRepository).decrease(FAR, MAIN, new BigDecimal("2"));
            verify(stockRepository, never()).decrease(eq(EXPIRED), any(), any());
            verify(stockRepository).insertExpense(eq(NEAR), eq(MAIN), eq(EMPLOYEE_ID), isNull(), isNull(),
                    eq(new BigDecimal("10")), eq(TODAY), eq("Кормление"));
            assertThat(result.selection()).isEqualTo(MaterialSelection.FEFO);
        }

        @Test
        void backdatedFefoIgnoresBatchesReceivedLater() {
            // Партия FRESH (поступила сегодня) была бы ближайшей по сроку, но вчера её ещё не было
            when(stockRepository.lockByProduct(FEED, MAIN)).thenReturn(List.of(far(), near(),
                    row(FRESH, MAIN, TODAY, TODAY.plusDays(5), "50")));
            service.createExpense(new ExpenseRequest(FEED, MAIN, null, new BigDecimal("1"), TODAY.minusDays(1), null, null));

            verify(stockRepository).decrease(NEAR, MAIN, new BigDecimal("1"));
            verify(stockRepository, never()).decrease(eq(FRESH), any(), any());
        }

        @Test
        void chosenNonFefoBatchIsUsed() {
            ExpenseResult result = service.createExpense(expense(FAR, "3"));

            verify(stockRepository).decrease(FAR, MAIN, new BigDecimal("3"));
            verify(stockRepository, never()).decrease(eq(NEAR), any(), any());
            assertThat(result.selection()).isEqualTo(MaterialSelection.MANUAL);
        }

        @Test
        void expiredBatchCannotBeUsed() {
            assertThatThrownBy(() -> service.createExpense(expense(EXPIRED, "1")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Партия просрочена, её нужно списать");
            assertNoStockWrites();
        }

        @Test
        void moreThanBatchStockIsConflictWithNeededAndAvailable() {
            assertThatThrownBy(() -> service.createExpense(expense(NEAR, "11")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("нужно 11 кг, есть 10 кг");
            assertNoStockWrites();
        }

        @Test
        void fefoShortageIsConflictAndNothingIsWritten() {
            assertThatThrownBy(() -> service.createExpense(expense(null, "100")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("нужно 100 кг, есть 80 кг")
                    .hasMessageContaining("просроченные партии не учитываются");
            assertNoStockWrites();
        }

        @Test
        void batchMustBeOnChosenWarehouse() {
            assertThatThrownBy(() -> service.createExpense(expense(999L, "1")))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("нет на складе «Основной склад»");
        }

        @Test
        void batchMustBeOfChosenProduct() {
            when(stockRepository.lockRow(NEAR, MAIN)).thenReturn(Optional.of(
                    new StockRow(NEAR, MAIN, "Основной склад", 99L, TODAY.minusDays(1), null, BigDecimal.TEN)));

            assertThatThrownBy(() -> service.createExpense(expense(NEAR, "1")))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("не товар");
        }

        @Test
        void dateBeforeBatchReceiptIsConflict() {
            assertThatThrownBy(() -> service.createExpense(
                    new ExpenseRequest(FEED, MAIN, FRESH, BigDecimal.ONE, TODAY.minusDays(1), null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("раньше поступления партии");
        }

        @Test
        void futureDateIsBadRequest() {
            assertThatThrownBy(() -> service.createExpense(
                    new ExpenseRequest(FEED, MAIN, null, BigDecimal.ONE, TODAY.plusDays(1), null, null)))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void expenseForAnimal() {
            when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(MedicalTestData.presentAnimal()));

            service.createExpense(new ExpenseRequest(FEED, MAIN, NEAR, BigDecimal.ONE, null, ANIMAL_ID, null));

            verify(stockRepository).insertExpense(eq(NEAR), eq(MAIN), eq(EMPLOYEE_ID), eq(ANIMAL_ID), isNull(), any(), any(), any());
        }

        @Test
        void departedAnimalIsConflict() {
            when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal(TODAY.minusDays(30), TODAY.minusDays(1))));

            assertThatThrownBy(() -> service.createExpense(new ExpenseRequest(FEED, MAIN, NEAR, BigDecimal.ONE, null, ANIMAL_ID, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("выбыло");
            assertNoStockWrites();
        }

        @Test
        void expenseBeforeAnimalArrivalIsConflict() {
            when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(MedicalTestData.presentAnimal()));

            assertThatThrownBy(() -> service.createExpense(new ExpenseRequest(FEED, MAIN, NEAR, BigDecimal.ONE,
                    TODAY.minusDays(31), ANIMAL_ID, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("раньше поступления животного");
        }

        @Test
        void unknownProductWarehouseOrAnimalIsBadRequest() {
            assertThatThrownBy(() -> service.createExpense(new ExpenseRequest(404L, MAIN, null, BigDecimal.ONE, null, null, null)))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> service.createExpense(new ExpenseRequest(FEED, 404L, null, BigDecimal.ONE, null, null, null)))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> service.createExpense(new ExpenseRequest(FEED, MAIN, null, BigDecimal.ONE, null, 404L, null)))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void employeeWithoutCardIsConflict() {
            when(employeeService.currentEmployeeId()).thenThrow(new EmployeeNotLinkedException());

            assertThatThrownBy(() -> service.createExpense(expense(null, "1"))).isInstanceOf(EmployeeNotLinkedException.class);
            assertNoStockWrites();
        }
    }

    // ---------- удаление расхода ----------

    @Nested
    class ExpenseDeletion {

        @Test
        void manualExpenseReturnsQuantityToSameRow() {
            when(journalRepository.lockExpense(100L)).thenReturn(Optional.of(new ExpenseRow(100L, NEAR, MAIN, new BigDecimal("2.5"), null)));

            service.deleteExpense(100L);

            InOrder order = inOrder(stockRepository, journalRepository);
            order.verify(stockRepository).lockRow(NEAR, MAIN);
            order.verify(stockRepository).increase(NEAR, MAIN, new BigDecimal("2.5"));
            order.verify(journalRepository).deleteExpense(100L);
        }

        @Test
        void procedureExpenseIsDeletedOnlyWithProcedure() {
            when(journalRepository.lockExpense(100L)).thenReturn(Optional.of(new ExpenseRow(100L, NEAR, MAIN, BigDecimal.ONE, 7L)));

            assertThatThrownBy(() -> service.deleteExpense(100L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("DELETE /procedures/7");
            verify(stockRepository, never()).increase(any(), any(), any());
            verify(journalRepository, never()).deleteExpense(any());
        }

        @Test
        void unknownExpenseIsNotFound() {
            assertThatThrownBy(() -> service.deleteExpense(404L)).isInstanceOf(NotFoundException.class);
        }
    }

    // ---------- перемещение ----------

    @Nested
    class Transfer {

        private TransferRequest transfer(long from, long to, String quantity) {
            return new TransferRequest(NEAR, from, to, new BigDecimal(quantity), null, "В карантин");
        }

        @Test
        void quantityMovesBetweenRowsOfSameBatch() {
            when(journalRepository.insertTransfer(any(), any(), any(), any(), any(), any(), any())).thenReturn(1L);
            when(journalRepository.findTransfer(1L)).thenReturn(Optional.of(mock(com.shelter.core.dto.TransferResponse.class)));

            service.transfer(transfer(MAIN, QUARANTINE, "4"));

            verify(stockRepository).ensureRow(NEAR, QUARANTINE);
            verify(stockRepository).decrease(NEAR, MAIN, new BigDecimal("4"));
            verify(stockRepository).increase(NEAR, QUARANTINE, new BigDecimal("4"));
            verify(journalRepository).insertTransfer(NEAR, MAIN, QUARANTINE, EMPLOYEE_ID, new BigDecimal("4"), TODAY, "В карантин");
        }

        @Test
        void sameWarehouseIsBadRequest() {
            assertThatThrownBy(() -> service.transfer(transfer(MAIN, MAIN, "1"))).isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void moreThanSourceStockIsConflict() {
            assertThatThrownBy(() -> service.transfer(transfer(MAIN, QUARANTINE, "10.5")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("нужно 10.5 кг, есть 10 кг");
            assertNoStockWrites();
        }

        @Test
        void batchMissingOnSourceIsBadRequest() {
            assertThatThrownBy(() -> service.transfer(new TransferRequest(999L, MAIN, QUARANTINE, BigDecimal.ONE, null, null)))
                    .isInstanceOf(InvalidRequestException.class);
            verify(stockRepository, never()).ensureRow(any(), any());
        }

        @Test
        void rowsAreLockedInWarehouseOrder() {
            when(journalRepository.insertTransfer(any(), any(), any(), any(), any(), any(), any())).thenReturn(1L);
            when(journalRepository.findTransfer(1L)).thenReturn(Optional.of(mock(com.shelter.core.dto.TransferResponse.class)));
            when(stockRepository.lockRow(NEAR, QUARANTINE)).thenReturn(Optional.of(row(NEAR, QUARANTINE, TODAY.minusDays(50), TODAY.plusDays(10), "6")));

            service.transfer(new TransferRequest(NEAR, QUARANTINE, MAIN, BigDecimal.ONE, null, null));

            InOrder order = inOrder(stockRepository);
            order.verify(stockRepository).lockRow(NEAR, MAIN);
            order.verify(stockRepository).lockRow(NEAR, QUARANTINE);
        }
    }

    // ---------- списание ----------

    @Nested
    class WriteOff {

        @Test
        void damagedGoodsAreWrittenOffWithReason() {
            when(journalRepository.insertWriteOff(any(), any(), any(), any(), any(), any(), any())).thenReturn(5L);
            when(journalRepository.findWriteOff(5L)).thenReturn(Optional.of(mock(com.shelter.core.dto.WriteOffResponse.class)));

            service.writeOff(new WriteOffRequest(NEAR, MAIN, new BigDecimal("2"), WriteOffReason.DAMAGED, null, "Порван мешок"));

            verify(stockRepository).decrease(NEAR, MAIN, new BigDecimal("2"));
            verify(journalRepository).insertWriteOff(NEAR, MAIN, EMPLOYEE_ID, new BigDecimal("2"), TODAY,
                    WriteOffReason.DAMAGED, "Порван мешок");
        }

        @Test
        void expiredReasonOnlyForExpiredBatch() {
            assertThatThrownBy(() -> service.writeOff(new WriteOffRequest(NEAR, MAIN, BigDecimal.ONE, WriteOffReason.EXPIRED, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("не просрочена");
            assertNoStockWrites();
        }

        @Test
        void expiredBatchCanBeWrittenOff() {
            when(journalRepository.insertWriteOff(any(), any(), any(), any(), any(), any(), any())).thenReturn(5L);
            when(journalRepository.findWriteOff(5L)).thenReturn(Optional.of(mock(com.shelter.core.dto.WriteOffResponse.class)));

            service.writeOff(new WriteOffRequest(EXPIRED, MAIN, new BigDecimal("5"), WriteOffReason.EXPIRED, null, null));

            verify(stockRepository).decrease(EXPIRED, MAIN, new BigDecimal("5"));
        }

        @Test
        void moreThanStockIsConflict() {
            assertThatThrownBy(() -> service.writeOff(new WriteOffRequest(NEAR, MAIN, new BigDecimal("11"), WriteOffReason.LOST, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("нужно 11 кг, есть 10 кг");
            assertNoStockWrites();
        }
    }

    // ---------- списание всех просроченных ----------

    @Nested
    class ExpiredWriteOff {

        private final StockRow expiredA = row(EXPIRED, MAIN, TODAY.minusDays(400), TODAY.minusDays(1), "5");
        private final StockRow expiredB = row(40L, QUARANTINE, TODAY.minusDays(400), TODAY.minusDays(10), "1.5");

        @Test
        void previewShowsWhatAndHowMuchWillBeWrittenOff() {
            when(stockRepository.findExpired(TODAY)).thenReturn(List.of(expiredA, expiredB));

            ExpiredWriteOffPreview preview = service.previewExpired();

            assertThat(preview.items()).extracting(ExpiredWriteOffPreview.Item::batchId).containsExactly(EXPIRED, 40L);
            assertThat(preview.items().get(1).quantity()).isEqualByComparingTo("1.5");
            assertThat(preview.token()).isNotBlank();
            assertNoStockWrites();
        }

        @Test
        void confirmationWithSameTokenWritesOffEverything() {
            when(stockRepository.findExpired(TODAY)).thenReturn(List.of(expiredA, expiredB));
            when(stockRepository.lockExpired(TODAY)).thenReturn(List.of(expiredA, expiredB));
            when(journalRepository.insertWriteOff(any(), any(), any(), any(), any(), any(), any())).thenReturn(5L, 6L);
            when(journalRepository.findWriteOff(anyLong())).thenReturn(Optional.of(mock(com.shelter.core.dto.WriteOffResponse.class)));
            String token = service.previewExpired().token();

            assertThat(service.writeOffExpired(new ExpiredWriteOffRequest(token, null))).hasSize(2);

            verify(stockRepository).decrease(EXPIRED, MAIN, new BigDecimal("5"));
            verify(stockRepository).decrease(40L, QUARANTINE, new BigDecimal("1.5"));
            verify(journalRepository, times(2)).insertWriteOff(any(), any(), eq(EMPLOYEE_ID), any(), eq(TODAY),
                    eq(WriteOffReason.EXPIRED), any());
        }

        @Test
        void changedStockAfterPreviewIsConflictAndNothingIsWrittenOff() {
            when(stockRepository.findExpired(TODAY)).thenReturn(List.of(expiredA, expiredB));
            String token = service.previewExpired().token();
            when(stockRepository.lockExpired(TODAY)).thenReturn(List.of(expiredA,
                    row(40L, QUARANTINE, TODAY.minusDays(400), TODAY.minusDays(10), "1")));

            assertThatThrownBy(() -> service.writeOffExpired(new ExpiredWriteOffRequest(token, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("обновите предпросмотр");
            assertNoStockWrites();
        }

        @Test
        void tokenDependsOnRowsAndQuantitiesButNotOnScaleOrOrder() {
            String token = StockOperationService.token(TODAY, List.of(expiredA, expiredB));

            assertThat(StockOperationService.token(TODAY, List.of(expiredB, expiredA))).isEqualTo(token);
            assertThat(StockOperationService.token(TODAY, List.of(expiredA,
                    row(40L, QUARANTINE, TODAY.minusDays(400), TODAY.minusDays(10), "1.500")))).isEqualTo(token);
            assertThat(StockOperationService.token(TODAY, List.of(expiredA))).isNotEqualTo(token);
            assertThat(StockOperationService.token(TODAY.plusDays(1), List.of(expiredA, expiredB))).isNotEqualTo(token);
        }
    }
}
