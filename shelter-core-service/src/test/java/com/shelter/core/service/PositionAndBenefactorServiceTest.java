package com.shelter.core.service;

import com.shelter.core.dto.BenefactorRequest;
import com.shelter.core.dto.BenefactorResponse;
import com.shelter.core.dto.PositionRequest;
import com.shelter.core.dto.PositionResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.repository.BenefactorRepository;
import com.shelter.core.repository.PositionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Справочник должностей и благотворители: уникальность названия, запрет удаления со ссылками. */
class PositionAndBenefactorServiceTest {

    private final PositionRepository positions = mock(PositionRepository.class);
    private final PositionService positionService = new PositionService(positions);
    private final BenefactorRepository benefactors = mock(BenefactorRepository.class);
    private final BenefactorService benefactorService = new BenefactorService(benefactors);

    @Test
    void positionNameIsUniqueIgnoringCaseAndSalaryIsRoundedToKopecks() {
        when(positions.nameTaken("ветеринар", null)).thenReturn(true);
        assertThatThrownBy(() -> positionService.create(new PositionRequest(" ветеринар ", new BigDecimal("1"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже есть");

        when(positions.insert(any(), any())).thenReturn(9L);
        when(positions.findById(9L)).thenReturn(Optional.of(new PositionResponse(9L, "Санитар", new BigDecimal("45000.00"), 0)));
        positionService.create(new PositionRequest("Санитар", new BigDecimal("45000")));
        verify(positions).insert(eq("Санитар"), eq(new BigDecimal("45000.00")));
    }

    @Test
    void positionWithEmployeesIsNotDeleted() {
        when(positions.lockById(2L)).thenReturn(true);
        when(positions.findById(2L)).thenReturn(Optional.of(new PositionResponse(2L, "Ветеринар", BigDecimal.TEN, 1)));
        assertThatThrownBy(() -> positionService.delete(2L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("сотрудники (1)");
        verify(positions, never()).delete(any());
    }

    @Test
    void benefactorWithDonationsIsNotDeleted() {
        when(benefactors.lockById(3L)).thenReturn(true);
        when(benefactors.findById(3L)).thenReturn(Optional.of(new BenefactorResponse(3L, "Фонд", null, null, null, 2,
                BigDecimal.ZERO, null, null)));
        assertThatThrownBy(() -> benefactorService.delete(3L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("пожертвования (2)");
        verify(benefactors, never()).delete(any());
    }

    @Test
    void benefactorContactsAreTrimmedAndBlankBecomesNull() {
        when(benefactors.insert(any())).thenReturn(5L);
        when(benefactors.findById(5L)).thenReturn(Optional.of(new BenefactorResponse(5L, "Фонд", null, null, null, 0,
                BigDecimal.ZERO, null, null)));
        benefactorService.create(new BenefactorRequest(" Фонд ", " ", "fund@test.local ", ""));
        verify(benefactors).insert(new BenefactorRequest("Фонд", null, "fund@test.local", null));
    }
}
