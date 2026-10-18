package com.shelter.core.service;

import com.shelter.core.dto.CategoryResponse;
import com.shelter.core.dto.GoodsDonationRequest;
import com.shelter.core.dto.ProductResponse;
import com.shelter.core.dto.ReceiptItemRequest;
import com.shelter.core.dto.ReceiptItemResponse;
import com.shelter.core.dto.SupplierResponse;
import com.shelter.core.dto.SupplyRequest;
import com.shelter.core.dto.SupplyResponse;
import com.shelter.core.dto.WarehouseResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.EmployeeNotLinkedException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.repository.ProductRepository;
import com.shelter.core.repository.ReceiptRepository;
import com.shelter.core.repository.ReceiptRepository.DonationHeader;
import com.shelter.core.repository.ReceiptRepository.ReceiptItem;
import com.shelter.core.repository.ReceiptRepository.SupplyHeader;
import com.shelter.core.repository.WarehouseReferenceRepository;
import com.shelter.core.security.CurrentUser;
import com.shelter.core.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.EMPLOYEE_ID;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptServiceTest {

    private static final long SUPPLY_ID = 8L;
    private static final long DONATION_ID = 6L;
    private static final long FEED = 3L;
    private static final long ARCHIVED = 4L;
    private static final long WAREHOUSE = 1L;
    private static final long SUPPLIER = 1L;

    private ReceiptRepository receiptRepository;
    private ProductRepository productRepository;
    private WarehouseReferenceRepository referenceRepository;
    private EmployeeService employeeService;
    private CurrentUser currentUser;
    private ReceiptService service;

    @BeforeEach
    void setUp() {
        receiptRepository = mock(ReceiptRepository.class);
        productRepository = mock(ProductRepository.class);
        referenceRepository = mock(WarehouseReferenceRepository.class);
        employeeService = mock(EmployeeService.class);
        currentUser = mock(CurrentUser.class);
        service = new ReceiptService(receiptRepository, productRepository, referenceRepository, employeeService,
                currentUser, CLOCK);

        CategoryResponse feedCategory = new CategoryResponse(1L, "Корм");
        when(productRepository.findById(FEED)).thenReturn(Optional.of(new ProductResponse(FEED, "Корм", feedCategory, "кг", true, null)));
        when(productRepository.findById(ARCHIVED)).thenReturn(Optional.of(new ProductResponse(ARCHIVED, "Старый корм", feedCategory, "кг", false, null)));
        WarehouseResponse warehouse = new WarehouseResponse(WAREHOUSE, "Основной склад", null, null);
        when(referenceRepository.findWarehouse(WAREHOUSE)).thenReturn(Optional.of(warehouse));
        SupplierResponse supplier = new SupplierResponse(SUPPLIER, "ЗооОпт", null, null, null);
        when(referenceRepository.findSupplier(SUPPLIER)).thenReturn(Optional.of(supplier));
        when(employeeService.currentEmployeeId()).thenReturn(EMPLOYEE_ID);
        when(receiptRepository.insertSupply(anyLong(), any(), anyLong(), any())).thenReturn(SUPPLY_ID);
        when(receiptRepository.insertGoodsDonation(any(), any(), any())).thenReturn(DONATION_ID);

        when(receiptRepository.findSupply(SUPPLY_ID)).thenReturn(Optional.of(
                new SupplyHeader(SUPPLY_ID, supplier, TODAY, false, null, null, null)));
        when(receiptRepository.supplyItems(List.of(SUPPLY_ID))).thenReturn(Map.of(SUPPLY_ID, List.of(
                new ReceiptItem(SUPPLY_ID, WAREHOUSE, new ReceiptItemResponse(20L, FEED, "Корм", "кг", new BigDecimal("10"),
                        TODAY.plusDays(100), new BigDecimal("100.00"))),
                new ReceiptItem(SUPPLY_ID, WAREHOUSE, new ReceiptItemResponse(21L, FEED, "Корм", "кг", new BigDecimal("2.5"),
                        TODAY.plusDays(200), new BigDecimal("40.00"))))));
        when(receiptRepository.findGoodsDonation(DONATION_ID)).thenReturn(Optional.of(
                new DonationHeader(DONATION_ID, null, TODAY, null, null)));
    }

    private static ReceiptItemRequest item(long product, String quantity, String price, LocalDate expiration) {
        return new ReceiptItemRequest(product, new BigDecimal(quantity), price == null ? null : new BigDecimal(price), expiration);
    }

    private static SupplyRequest supply(LocalDate date, ReceiptItemRequest... items) {
        return new SupplyRequest(SUPPLIER, WAREHOUSE, date, null, List.of(items));
    }

    private void assertNothingSaved() {
        verify(receiptRepository, never()).insertSupply(anyLong(), any(), anyLong(), any());
        verify(receiptRepository, never()).insertGoodsDonation(any(), any(), any());
        verify(receiptRepository, never()).insertBatch(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Nested
    class Supplies {

        @Test
        void supplyCreatesUnpaidBatchesAndStockOnWarehouse() {
            service.createSupply(supply(null, item(FEED, "10", "100", TODAY.plusDays(100)), item(FEED, "2.5", "40", null)));

            verify(receiptRepository).insertSupply(SUPPLIER, TODAY, EMPLOYEE_ID, null);
            verify(receiptRepository).insertBatch(FEED, SUPPLY_ID, null, TODAY, TODAY.plusDays(100), new BigDecimal("10"),
                    new BigDecimal("100"), EMPLOYEE_ID, WAREHOUSE);
            verify(receiptRepository).insertBatch(eq(FEED), eq(SUPPLY_ID), isNull(), eq(TODAY), isNull(),
                    eq(new BigDecimal("2.5")), eq(new BigDecimal("40")), eq(EMPLOYEE_ID), eq(WAREHOUSE));
        }

        @Test
        void curatorSeesNoPricesAndNoTotal() {
            when(currentUser.hasRole(Roles.ADMIN)).thenReturn(false);

            SupplyResponse response = service.findSupply(SUPPLY_ID);

            assertThat(response.totalAmount()).isNull();
            assertThat(response.items()).allSatisfy(i -> assertThat(i.unitPrice()).isNull());
        }

        @Test
        void adminSeesPricesAndTotalCalculatedFromItems() {
            when(currentUser.hasRole(Roles.ADMIN)).thenReturn(true);

            SupplyResponse response = service.findSupply(SUPPLY_ID);

            assertThat(response.totalAmount()).isEqualByComparingTo("1100.00");
            assertThat(response.items().getFirst().unitPrice()).isEqualByComparingTo("100.00");
            assertThat(response.paid()).isFalse();
        }

        @Test
        void futureDateIsBadRequest() {
            assertThatThrownBy(() -> service.createSupply(supply(TODAY.plusDays(1), item(FEED, "1", null, null))))
                    .isInstanceOf(InvalidRequestException.class);
            assertNothingSaved();
        }

        @Test
        void expirationMustBeAfterReceiptDate() {
            assertThatThrownBy(() -> service.createSupply(supply(TODAY.minusDays(3), item(FEED, "1", null, TODAY.minusDays(3)))))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("позже даты прихода");
            assertNothingSaved();
        }

        @Test
        void archivedProductIsNotAccepted() {
            assertThatThrownBy(() -> service.createSupply(supply(null, item(FEED, "1", null, null), item(ARCHIVED, "1", null, null))))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("в архиве");
            assertNothingSaved();
        }

        @Test
        void unknownSupplierWarehouseOrProductIsBadRequest() {
            assertThatThrownBy(() -> service.createSupply(new SupplyRequest(404L, WAREHOUSE, null, null, List.of(item(FEED, "1", null, null)))))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> service.createSupply(new SupplyRequest(SUPPLIER, 404L, null, null, List.of(item(FEED, "1", null, null)))))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> service.createSupply(supply(null, item(404L, "1", null, null))))
                    .isInstanceOf(InvalidRequestException.class);
            assertNothingSaved();
        }

        @Test
        void employeeWithoutCardIsConflict() {
            when(employeeService.currentEmployeeId()).thenThrow(new EmployeeNotLinkedException());

            assertThatThrownBy(() -> service.createSupply(supply(null, item(FEED, "1", null, null))))
                    .isInstanceOf(EmployeeNotLinkedException.class);
            assertNothingSaved();
        }

        @Test
        void unpaidSupplyWithoutMovementsIsDeleted() {
            when(receiptRepository.lockSupply(SUPPLY_ID)).thenReturn(Optional.of(false));

            service.deleteSupply(SUPPLY_ID);

            verify(receiptRepository).lockStockOfBatches(List.of(20L, 21L));
            verify(receiptRepository).deleteBatches(List.of(20L, 21L));
            verify(receiptRepository).deleteSupply(SUPPLY_ID);
        }

        @Test
        void supplyWithUsedBatchesIsNotDeleted() {
            when(receiptRepository.lockSupply(SUPPLY_ID)).thenReturn(Optional.of(false));
            when(receiptRepository.batchesHaveMovements(List.of(20L, 21L))).thenReturn(true);

            assertThatThrownBy(() -> service.deleteSupply(SUPPLY_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("уже были расход");
            verify(receiptRepository, never()).deleteBatches(any());
            verify(receiptRepository, never()).deleteSupply(any());
        }

        @Test
        void paidSupplyIsDeletedOnlyByAdminWithPayment() {
            when(receiptRepository.lockSupply(SUPPLY_ID)).thenReturn(Optional.of(true));

            assertThatThrownBy(() -> service.deleteSupply(SUPPLY_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("оплачена");
            verify(receiptRepository, never()).deleteSupply(any());
        }

        @Test
        void periodMustBeOrdered() {
            assertThatThrownBy(() -> service.findSupplies(null, TODAY, TODAY.minusDays(1), 0, 20))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    class GoodsDonations {

        private GoodsDonationRequest donation(Long benefactorId, String name, ReceiptItemRequest... items) {
            return new GoodsDonationRequest(benefactorId, name, WAREHOUSE, null, null, List.of(items));
        }

        @Test
        void anonymousDonationCreatesBatchesWithDonationSource() {
            service.createGoodsDonation(donation(null, null, item(FEED, "3", null, null)));

            verify(receiptRepository).insertGoodsDonation(null, TODAY, null);
            verify(receiptRepository).insertBatch(FEED, null, DONATION_ID, TODAY, null, new BigDecimal("3"), null,
                    EMPLOYEE_ID, WAREHOUSE);
        }

        @Test
        void existingBenefactorFromList() {
            when(receiptRepository.benefactorExists(2L)).thenReturn(true);

            service.createGoodsDonation(donation(2L, null, item(FEED, "3", null, null)));

            verify(receiptRepository).insertGoodsDonation(2L, TODAY, null);
            verify(receiptRepository, never()).insertBenefactor(any());
        }

        @Test
        void newBenefactorByNameIsCreatedWithNameOnly() {
            when(receiptRepository.insertBenefactor("Иван Петров")).thenReturn(9L);

            service.createGoodsDonation(donation(null, "  Иван Петров ", item(FEED, "3", null, null)));

            verify(receiptRepository).insertGoodsDonation(9L, TODAY, null);
        }

        @Test
        void benefactorIdAndNameTogetherIsBadRequest() {
            assertThatThrownBy(() -> service.createGoodsDonation(donation(2L, "Иван", item(FEED, "3", null, null))))
                    .isInstanceOf(InvalidRequestException.class);
            assertNothingSaved();
        }

        @Test
        void unknownBenefactorIsBadRequest() {
            assertThatThrownBy(() -> service.createGoodsDonation(donation(404L, null, item(FEED, "3", null, null))))
                    .isInstanceOf(InvalidRequestException.class);
            assertNothingSaved();
        }

        @Test
        void donationItemsHaveNoPrice() {
            assertThatThrownBy(() -> service.createGoodsDonation(donation(null, null, item(FEED, "3", "10", null))))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("нет цены");
            assertNothingSaved();
        }

        @Test
        void usedDonationIsNotDeleted() {
            when(receiptRepository.lockGoodsDonation(DONATION_ID)).thenReturn(true);
            when(receiptRepository.donationItems(List.of(DONATION_ID))).thenReturn(Map.of(DONATION_ID, List.of(
                    new ReceiptItem(DONATION_ID, WAREHOUSE, new ReceiptItemResponse(25L, FEED, "Корм", "кг", BigDecimal.ONE, null, null)))));
            when(receiptRepository.batchesHaveMovements(List.of(25L))).thenReturn(true);

            assertThatThrownBy(() -> service.deleteGoodsDonation(DONATION_ID)).isInstanceOf(BusinessRuleException.class);
            verify(receiptRepository, never()).deleteDonation(any());
        }
    }
}
