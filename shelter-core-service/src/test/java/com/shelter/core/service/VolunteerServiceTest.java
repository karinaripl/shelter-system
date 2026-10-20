package com.shelter.core.service;

import com.shelter.core.dto.AssignmentRequest;
import com.shelter.core.dto.AssignmentResponse;
import com.shelter.core.dto.VolunteerRequest;
import com.shelter.core.dto.VolunteerResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.AssignmentRepository;
import com.shelter.core.repository.VolunteerRepository;
import com.shelter.model.generated.shelter.tables.records.VolunteerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.ANIMAL_ID;
import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static com.shelter.core.service.MedicalTestData.animal;
import static com.shelter.core.service.MedicalTestData.presentAnimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Волонтёры и закрепления: правила шага 6.1. */
class VolunteerServiceTest {

    private static final long VOLUNTEER_ID = 5L;
    private static final long ASSIGNMENT_ID = 50L;

    private VolunteerRepository volunteerRepository;
    private AssignmentRepository assignmentRepository;
    private AnimalRepository animalRepository;
    private VolunteerService volunteerService;
    private AssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        volunteerRepository = mock(VolunteerRepository.class);
        assignmentRepository = mock(AssignmentRepository.class);
        animalRepository = mock(AnimalRepository.class);
        volunteerService = new VolunteerService(volunteerRepository, CLOCK);
        assignmentService = new AssignmentService(assignmentRepository, volunteerRepository, animalRepository, CLOCK);

        when(volunteerRepository.insert(any())).thenReturn(VOLUNTEER_ID);
        when(volunteerRepository.findById(VOLUNTEER_ID)).thenReturn(Optional.of(volunteer(true)));
        when(volunteerRepository.lockById(VOLUNTEER_ID)).thenReturn(Optional.of(volunteer(true)));
        when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(presentAnimal()));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(presentAnimal()));
        when(assignmentRepository.insert(anyLong(), anyLong(), any(), any())).thenReturn(ASSIGNMENT_ID);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment(null)));
    }

    private static VolunteerResponse volunteer(boolean active) {
        return new VolunteerResponse(VOLUNTEER_ID, "Гусева Полина", "89100000001", null, null, null,
                TODAY.minusDays(100), "Фото", "1234", "567890", active);
    }

    private static VolunteerRequest request(String series, String number) {
        return new VolunteerRequest("  Гусева Полина ", "89100000001", null, null, null, null, "Фото", series, number);
    }

    private static AssignmentResponse assignment(LocalDate endDate) {
        return new AssignmentResponse(ASSIGNMENT_ID, VOLUNTEER_ID, "Гусева Полина", ANIMAL_ID, "Барсик",
                TODAY.minusDays(10), endDate, endDate == null, null);
    }

    @Nested
    class Profile {

        @Test
        void createsWithTodayAsRegistrationDateAndTrimmedName() {
            volunteerService.create(request("1234", "567890"));

            ArgumentCaptor<VolunteerRecord> captor = ArgumentCaptor.forClass(VolunteerRecord.class);
            verify(volunteerRepository).insert(captor.capture());
            assertThat(captor.getValue().getRegistrationDate()).isEqualTo(TODAY);
            assertThat(captor.getValue().getFullName()).isEqualTo("Гусева Полина");
        }

        @Test
        void passportNeedsBothSeriesAndNumber() {
            assertThatThrownBy(() -> volunteerService.create(request("1234", null)))
                    .isInstanceOf(InvalidRequestException.class).hasMessageContaining("и серию");
            assertThatThrownBy(() -> volunteerService.create(request(null, "567890")))
                    .isInstanceOf(InvalidRequestException.class);
            verify(volunteerRepository, never()).insert(any());
        }

        @Test
        void withoutPassportIsFine() {
            volunteerService.create(request(null, null));

            verify(volunteerRepository).insert(any());
        }

        @Test
        void passportMustBeUnique() {
            when(volunteerRepository.passportTaken("1234", "567890", null)).thenReturn(true);

            assertThatThrownBy(() -> volunteerService.create(request("1234", "567890")))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже указан");
        }

        @Test
        void registrationDateIsNotInFuture() {
            var future = new VolunteerRequest("Гусева", null, null, null, null, TODAY.plusDays(1), null, null, null);

            assertThatThrownBy(() -> volunteerService.create(future)).isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void birthDateIsBeforeRegistration() {
            var bad = new VolunteerRequest("Гусева", null, null, null, TODAY, TODAY, null, null, null);

            assertThatThrownBy(() -> volunteerService.create(bad)).isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void updateKeepsRegistrationDateAndChecksItAgainstAssignments() {
            when(volunteerRepository.firstAssignmentStart(VOLUNTEER_ID)).thenReturn(Optional.of(TODAY.minusDays(50)));
            var later = new VolunteerRequest("Гусева", null, null, null, null, TODAY.minusDays(20), null, null, null);

            assertThatThrownBy(() -> volunteerService.update(VOLUNTEER_ID, later))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("позже начала закрепления");

            volunteerService.update(VOLUNTEER_ID, request(null, null));
            ArgumentCaptor<VolunteerRecord> captor = ArgumentCaptor.forClass(VolunteerRecord.class);
            verify(volunteerRepository).update(eq(VOLUNTEER_ID), captor.capture());
            assertThat(captor.getValue().getRegistrationDate()).isEqualTo(TODAY.minusDays(100));
        }

        @Test
        void volunteerWithAssignmentsIsNotDeleted() {
            when(volunteerRepository.countAssignments(VOLUNTEER_ID, false)).thenReturn(2);

            assertThatThrownBy(() -> volunteerService.delete(VOLUNTEER_ID))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("неактивным");
            verify(volunteerRepository, never()).delete(any());
        }

        @Test
        void volunteerWithoutAssignmentsIsDeleted() {
            volunteerService.delete(VOLUNTEER_ID);

            verify(volunteerRepository).delete(VOLUNTEER_ID);
        }

        @Test
        void unknownVolunteerIsNotFound() {
            assertThatThrownBy(() -> volunteerService.findById(99L)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Active {

        @Test
        void cannotDeactivateWithCurrentAssignments() {
            when(volunteerRepository.countAssignments(VOLUNTEER_ID, true)).thenReturn(1);

            assertThatThrownBy(() -> volunteerService.setActive(VOLUNTEER_ID, false))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Сначала закройте");
            verify(volunteerRepository, never()).updateActive(anyLong(), eq(false));
        }

        @Test
        void deactivatesWithOnlyClosedAssignments() {
            volunteerService.setActive(VOLUNTEER_ID, false);

            verify(volunteerRepository).updateActive(VOLUNTEER_ID, false);
        }

        @Test
        void reactivationIsAlwaysAllowed() {
            when(volunteerRepository.countAssignments(VOLUNTEER_ID, true)).thenReturn(1);

            volunteerService.setActive(VOLUNTEER_ID, true);

            verify(volunteerRepository).updateActive(VOLUNTEER_ID, true);
        }
    }

    @Nested
    class Assignments {

        private AssignmentRequest assign(LocalDate start) {
            return new AssignmentRequest(VOLUNTEER_ID, ANIMAL_ID, start, " Приучение к рукам ");
        }

        @Test
        void startsTodayByDefault() {
            assignmentService.create(assign(null));

            verify(assignmentRepository).insert(VOLUNTEER_ID, ANIMAL_ID, TODAY, "Приучение к рукам");
        }

        @Test
        void secondActiveAssignmentForSamePairIsConflict() {
            when(assignmentRepository.activeExists(VOLUNTEER_ID, ANIMAL_ID)).thenReturn(true);

            assertThatThrownBy(() -> assignmentService.create(assign(null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже закреплён");
            verify(assignmentRepository, never()).insert(anyLong(), anyLong(), any(), any());
        }

        @Test
        void departedAnimalIsNotAssigned() {
            when(animalRepository.lockById(ANIMAL_ID)).thenReturn(
                    Optional.of(animal(TODAY.minusDays(30), TODAY.minusDays(1))));

            assertThatThrownBy(() -> assignmentService.create(assign(null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("выбыло");
        }

        @Test
        void inactiveVolunteerIsNotAssigned() {
            when(volunteerRepository.lockById(VOLUNTEER_ID)).thenReturn(Optional.of(volunteer(false)));

            assertThatThrownBy(() -> assignmentService.create(assign(null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("неактивен");
        }

        @Test
        void startIsNotInFutureNorBeforeArrivalOrRegistration() {
            assertThatThrownBy(() -> assignmentService.create(assign(TODAY.plusDays(1))))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> assignmentService.create(assign(TODAY.minusDays(31))))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("поступления");

            when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(animal(TODAY.minusDays(300), null)));
            assertThatThrownBy(() -> assignmentService.create(assign(TODAY.minusDays(101))))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("регистрации");
        }

        @Test
        void unknownVolunteerOrAnimalIsNotFound() {
            assertThatThrownBy(() -> assignmentService.create(new AssignmentRequest(99L, ANIMAL_ID, null, null)))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> assignmentService.create(new AssignmentRequest(VOLUNTEER_ID, 99L, null, null)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void closesTodayByDefault() {
            when(assignmentRepository.lockById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment(null)));

            assignmentService.close(ASSIGNMENT_ID, null);

            verify(assignmentRepository).close(ASSIGNMENT_ID, TODAY);
        }

        @Test
        void endDateIsNotBeforeStartNorInFuture() {
            when(assignmentRepository.lockById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment(null)));

            assertThatThrownBy(() -> assignmentService.close(ASSIGNMENT_ID, TODAY.minusDays(11)))
                    .isInstanceOf(InvalidRequestException.class).hasMessageContaining("раньше даты начала");
            assertThatThrownBy(() -> assignmentService.close(ASSIGNMENT_ID, TODAY.plusDays(1)))
                    .isInstanceOf(InvalidRequestException.class);
            verify(assignmentRepository, never()).close(anyLong(), any());

            assignmentService.close(ASSIGNMENT_ID, TODAY.minusDays(10));
            verify(assignmentRepository).close(ASSIGNMENT_ID, TODAY.minusDays(10));
        }

        @Test
        void closedAssignmentIsNotClosedAgain() {
            when(assignmentRepository.lockById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment(TODAY.minusDays(2))));

            assertThatThrownBy(() -> assignmentService.close(ASSIGNMENT_ID, null))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже закрыто");
        }

        @Test
        void listsByAnimalCheckAnimalExists() {
            assertThatThrownBy(() -> assignmentService.findByAnimal(99L, null)).isInstanceOf(NotFoundException.class);

            assignmentService.findByAnimal(ANIMAL_ID, true);
            verify(assignmentRepository).findByAnimal(ANIMAL_ID, true);
        }
    }
}
