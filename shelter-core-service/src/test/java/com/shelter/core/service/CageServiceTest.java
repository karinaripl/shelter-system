package com.shelter.core.service;

import com.shelter.core.dto.CageRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.AnimalMapper;
import com.shelter.core.mapper.CageMapper;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.CageRepository;
import com.shelter.core.repository.CageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CageServiceTest {

    private CageRepository cageRepository;
    private CageService service;

    @BeforeEach
    void setUp() {
        cageRepository = mock(CageRepository.class);
        service = new CageService(cageRepository, mock(AnimalRepository.class), new CageMapper(), new AnimalMapper());
    }

    @Test
    void cageNumberMustBeUnique() {
        when(cageRepository.existsByNumber("A-01", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CageRequest("A-01", 2, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("уже есть");
        verify(cageRepository, never()).insert(anyString(), anyInt(), anyString());
    }

    @Test
    void capacityCannotDropBelowOccupancy() {
        when(cageRepository.lockById(1L)).thenReturn(Optional.of(new CageView(1L, "A-01", 4, null, 3)));

        assertThatThrownBy(() -> service.update(1L, new CageRequest("A-01", 2, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("вместимость");
        verify(cageRepository, never()).update(anyLong(), anyString(), anyInt(), anyString());
    }

    @Test
    void capacityCanDropToOccupancy() {
        CageView cage = new CageView(1L, "A-01", 4, null, 2);
        when(cageRepository.lockById(1L)).thenReturn(Optional.of(cage));
        when(cageRepository.findById(1L)).thenReturn(Optional.of(new CageView(1L, "A-01", 2, null, 2)));

        assertThat(service.update(1L, new CageRequest("A-01", 2, null)).free()).isZero();
        verify(cageRepository).update(1L, "A-01", 2, null);
    }

    @Test
    void occupiedCageCannotBeDeleted() {
        when(cageRepository.lockById(1L)).thenReturn(Optional.of(new CageView(1L, "A-01", 2, null, 1)));

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessRuleException.class);
        verify(cageRepository, never()).delete(anyLong());
    }

    @Test
    void emptyCageIsDeleted() {
        when(cageRepository.lockById(1L)).thenReturn(Optional.of(new CageView(1L, "A-01", 2, null, 0)));

        service.delete(1L);

        verify(cageRepository).delete(1L);
    }

    @Test
    void reservePlaceFailsWhenCageIsFull() {
        when(cageRepository.lockById(1L)).thenReturn(Optional.of(new CageView(1L, "A-01", 2, null, 2)));

        assertThatThrownBy(() -> service.reservePlace(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("заполнена: занято 2 из 2");
    }

    @Test
    void reservePlaceForUnknownCageIsBadRequest() {
        when(cageRepository.lockById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reservePlace(99L)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void unknownCageGives404() {
        when(cageRepository.findById(99L)).thenReturn(Optional.empty());
        when(cageRepository.lockById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
    }
}
