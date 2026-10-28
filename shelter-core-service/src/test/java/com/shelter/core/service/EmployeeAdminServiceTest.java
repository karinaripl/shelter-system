package com.shelter.core.service;

import com.shelter.core.client.AuthAccounts;
import com.shelter.core.client.AuthUser;
import com.shelter.core.dto.AccountRequest;
import com.shelter.core.dto.EmployeeCreateRequest;
import com.shelter.core.dto.EmployeeDetailsResponse;
import com.shelter.core.dto.EmployeeRequest;
import com.shelter.core.dto.PositionResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.ServiceUnavailableException;
import com.shelter.core.repository.EmployeeRepository;
import com.shelter.core.repository.PositionRepository;
import com.shelter.core.repository.SalaryRepository;
import com.shelter.core.security.CurrentUser;
import com.shelter.model.generated.shelter.tables.records.EmployeeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Сотрудник и учётная запись живут в разных базах: создание с компенсацией и ключом запроса,
 * увольнение с блокировкой входа и обратным вызовом при сбое.
 */
class EmployeeAdminServiceTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final long POSITION = 1L;
    private static final long USER = 42L;
    private static final long EMPLOYEE = 7L;

    private EmployeeRepository repository;
    private PositionRepository positionRepository;
    private SalaryRepository salaryRepository;
    private AuthAccounts auth;
    private CurrentUser currentUser;
    private EmployeeAdminService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmployeeRepository.class);
        positionRepository = mock(PositionRepository.class);
        salaryRepository = mock(SalaryRepository.class);
        auth = mock(AuthAccounts.class);
        currentUser = mock(CurrentUser.class);
        service = new EmployeeAdminService(repository, positionRepository, salaryRepository, auth, currentUser,
                TransactionOperations.withoutTransaction(), CLOCK);
        when(positionRepository.findById(POSITION)).thenReturn(Optional.of(new PositionResponse(POSITION, "Ветеринар",
                new BigDecimal("60000.00"), 0)));
        when(repository.findDetails(anyLong())).thenReturn(Optional.of(details()));
        when(auth.create(any(), anyString(), anyString(), anyString(), any())).thenReturn(user(USER, "VET"));
        when(repository.insert(any())).thenReturn(EMPLOYEE);
        when(repository.findIdByUserId(USER)).thenReturn(Optional.empty());
        when(currentUser.id()).thenReturn(1L);
    }

    private static EmployeeRequest card() {
        return new EmployeeRequest("Новикова Анна", POSITION, TODAY.minusDays(3), 40, null, null, null, null, null, null);
    }

    private static AccountRequest account(String... roles) {
        return new AccountRequest("novikova", "novikova@test.local", "password1", Set.of(roles));
    }

    private static AuthUser user(Long id, String... roles) {
        return new AuthUser(id, "novikova", "novikova@test.local", List.of(roles), true);
    }

    private static EmployeeDetailsResponse details() {
        return new EmployeeDetailsResponse(EMPLOYEE, "Новикова Анна", POSITION, "Ветеринар", BigDecimal.TEN,
                TODAY.minusDays(3), null, true, (short) 40, null, null, null, null, null, null, null, null, false,
                null, null);
    }

    @Nested
    class Creation {

        @Test
        void accountIsCreatedWithRequestKeyThenCardIsSaved() {
            var result = service.create(new EmployeeCreateRequest(card(), account("VET"), null), KEY);

            assertThat(result.created()).isTrue();
            InOrder order = inOrder(auth, repository);
            order.verify(auth).create(eq(KEY), eq("novikova"), eq("novikova@test.local"), eq("password1"), eq(Set.of("VET")));
            order.verify(repository).insert(any(EmployeeRecord.class));
            verify(auth, never()).deleteCreated(anyLong(), any());
        }

        @Test
        void takenUsernameInAuthServiceLeavesNoCard() {
            when(auth.create(any(), anyString(), anyString(), anyString(), any()))
                    .thenThrow(new BusinessRuleException("Пользователь с таким именем уже существует"));

            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(card(), account("VET"), null), KEY))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже существует");
            verify(repository, never()).insert(any());
        }

        @Test
        void authServiceDownLeavesNoCard() {
            when(auth.create(any(), anyString(), anyString(), anyString(), any()))
                    .thenThrow(new ServiceUnavailableException("Сервис учётных записей недоступен"));
            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(card(), account("VET"), null), KEY))
                    .isInstanceOf(ServiceUnavailableException.class);
            verify(repository, never()).insert(any());
        }

        @Test
        void failedCardRemovesCreatedAccount() {
            when(repository.insert(any())).thenThrow(new DataIntegrityViolationException("сбой"));

            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(card(), account("VET"), null), KEY))
                    .isInstanceOf(DataIntegrityViolationException.class);
            verify(auth).deleteCreated(USER, KEY);
        }

        @Test
        void failedCompensationKeepsOriginalErrorAndLogsAccountForRetry() {
            when(repository.insert(any())).thenThrow(new DataIntegrityViolationException("сбой"));
            doThrow(new ServiceUnavailableException("недоступен")).when(auth).deleteCreated(USER, KEY);

            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(card(), account("VET"), null), KEY))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
        }

        @Test
        void accountLinkedByParallelRepeatIsNotRemoved() {
            when(repository.insert(any())).thenThrow(new DataIntegrityViolationException("user_id уже занят"));
            when(repository.findIdByUserId(USER)).thenReturn(Optional.empty(), Optional.of(EMPLOYEE));

            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(card(), account("VET"), null), KEY))
                    .isInstanceOf(DataIntegrityViolationException.class);
            verify(auth, never()).deleteCreated(anyLong(), any());
        }

        @Test
        void repeatedKeyReturnsCardBeforeAnyCheckOrCall() {
            when(repository.findIdByCreationRequestId(KEY)).thenReturn(Optional.of(EMPLOYEE));
            var withOwnPassport = new EmployeeRequest("Новикова", POSITION, null, null, null, null, null, null, "1234", "567890");
            when(repository.passportTaken("1234", "567890", null)).thenReturn(true);

            var result = service.create(new EmployeeCreateRequest(withOwnPassport, account("VET"), null), KEY);

            assertThat(result.created()).isFalse();
            verify(auth, never()).create(any(), anyString(), anyString(), anyString(), any());
            verify(repository, never()).insert(any());
        }

        @Test
        void cardRemembersRequestKey() {
            service.create(new EmployeeCreateRequest(card(), account("VET"), null), KEY);
            verify(repository).insert(org.mockito.ArgumentMatchers.argThat(r -> KEY.equals(r.getCreationRequestId())
                    && Long.valueOf(USER).equals(r.getUserId())));
        }

        @Test
        void parallelRepeatThatAlreadySavedCardReturnsIt() {
            when(repository.findIdByUserId(USER)).thenReturn(Optional.of(EMPLOYEE));

            var result = service.create(new EmployeeCreateRequest(card(), account("VET"), null), KEY);

            assertThat(result.created()).isFalse();
            assertThat(result.employee().id()).isEqualTo(EMPLOYEE);
            verify(repository, never()).insert(any());
        }

        @Test
        void localErrorsAreFoundBeforeAccountIsCreated() {
            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(card(), account("VISITOR"), null), KEY))
                    .isInstanceOf(InvalidRequestException.class).hasMessageContaining("VISITOR");

            when(repository.passportTaken("1234", "567890", null)).thenReturn(true);
            var withPassport = new EmployeeRequest("Новикова", POSITION, null, null, null, null, null, null, "1234", "567890");
            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(withPassport, account("VET"), null), KEY))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Паспорт");

            var futureHire = new EmployeeRequest("Новикова", POSITION, TODAY.plusDays(1), null, null, null, null, null, null, null);
            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(futureHire, account("VET"), null), KEY))
                    .isInstanceOf(InvalidRequestException.class);

            var noPosition = new EmployeeRequest("Новикова", 99L, null, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(noPosition, account("VET"), null), KEY))
                    .isInstanceOf(InvalidRequestException.class).hasMessageContaining("Должность");

            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(card(), account("VET"), 5L), KEY))
                    .isInstanceOf(InvalidRequestException.class);
            verify(auth, never()).create(any(), anyString(), anyString(), anyString(), any());
        }

        @Test
        void existingAccountMustHaveStaffRoleAndBeFree() {
            when(auth.find(5L)).thenReturn(user(5L, "VISITOR"));
            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(card(), null, 5L), KEY))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("нет ролей сотрудника");

            when(auth.find(6L)).thenReturn(user(6L, "CURATOR"));
            when(repository.findIdByUserId(6L)).thenReturn(Optional.of(3L));
            assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(card(), null, 6L), KEY))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже связана");
            verify(repository, never()).insert(any());
        }
    }

    @Nested
    class Termination {

        private EmployeeRecord employee;

        @BeforeEach
        void working() {
            employee = new EmployeeRecord();
            employee.setId(EMPLOYEE);
            employee.setFullName("Новикова Анна");
            employee.setHireDate(TODAY.minusYears(1));
            employee.setUserId(USER);
            when(repository.findById(EMPLOYEE)).thenReturn(Optional.of(employee));
            when(repository.lockById(EMPLOYEE)).thenReturn(Optional.of(employee));
            when(repository.eventsOrganizedAfter(anyLong(), any())).thenReturn(List.of());
            when(salaryRepository.lastPaidMonth(EMPLOYEE)).thenReturn(Optional.empty());
        }

        @Test
        void terminationBlocksLoginThenSavesDate() {
            service.terminate(EMPLOYEE, TODAY);
            InOrder order = inOrder(auth, repository);
            order.verify(auth).setEnabled(USER, false);
            order.verify(repository).updateTermination(EMPLOYEE, TODAY);
        }

        @Test
        void failedLocalSaveUnblocksLogin() {
            doThrow(new DataIntegrityViolationException("сбой")).when(repository).updateTermination(EMPLOYEE, TODAY);
            assertThatThrownBy(() -> service.terminate(EMPLOYEE, TODAY)).isInstanceOf(DataIntegrityViolationException.class);
            verify(auth).setEnabled(USER, false);
            verify(auth).setEnabled(USER, true);
        }

        @Test
        void terminationRules() {
            assertThatThrownBy(() -> service.terminate(EMPLOYEE, TODAY.plusDays(1)))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> service.terminate(EMPLOYEE, TODAY.minusYears(2)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("раньше даты приёма");

            when(salaryRepository.lastPaidMonth(EMPLOYEE)).thenReturn(Optional.of(TODAY.withDayOfMonth(1)));
            assertThatThrownBy(() -> service.terminate(EMPLOYEE, TODAY.minusMonths(1)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("начислена зарплата");
            when(salaryRepository.lastPaidMonth(EMPLOYEE)).thenReturn(Optional.empty());

            when(repository.eventsOrganizedAfter(EMPLOYEE, TODAY)).thenReturn(List.of("«Выставка» (2026-11-01)"));
            assertThatThrownBy(() -> service.terminate(EMPLOYEE, TODAY))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Выставка");

            verify(auth, never()).setEnabled(anyLong(), anyBoolean());
            verify(repository, never()).updateTermination(anyLong(), any());
        }

        @Test
        void salaryForTerminationMonthDoesNotBlockTermination() {
            when(salaryRepository.lastPaidMonth(EMPLOYEE)).thenReturn(Optional.of(TODAY.withDayOfMonth(1)));
            service.terminate(EMPLOYEE, TODAY);
            verify(repository).updateTermination(EMPLOYEE, TODAY);
        }

        @Test
        void adminCannotTerminateThemselves() {
            when(currentUser.id()).thenReturn(USER);
            assertThatThrownBy(() -> service.terminate(EMPLOYEE, TODAY))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("самого себя");
        }

        @Test
        void alreadyTerminatedIsConflictAndCancellationUnblocks() {
            employee.setTerminationDate(TODAY.minusDays(1));
            assertThatThrownBy(() -> service.terminate(EMPLOYEE, TODAY))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже уволен");

            service.cancelTermination(EMPLOYEE);
            InOrder order = inOrder(auth, repository);
            order.verify(auth).setEnabled(USER, true);
            order.verify(repository).updateTermination(EMPLOYEE, null);
        }

        @Test
        void cancellingForWorkingEmployeeIsConflict() {
            assertThatThrownBy(() -> service.cancelTermination(EMPLOYEE))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("не уволен");
            verify(auth, never()).setEnabled(anyLong(), anyBoolean());
        }

        @Test
        void hireDateCannotMoveAfterPaidMonth() {
            when(salaryRepository.firstPaidMonth(EMPLOYEE)).thenReturn(Optional.of(LocalDate.of(2026, 9, 1)));
            var moved = new EmployeeRequest("Новикова Анна", POSITION, LocalDate.of(2026, 10, 1), 40, null, null, null,
                    null, null, null);
            assertThatThrownBy(() -> service.update(EMPLOYEE, moved))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("2026-09");
        }
    }
}
