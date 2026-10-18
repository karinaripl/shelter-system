package com.shelter.core.service;

import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.EmployeeNotLinkedException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.PublicAnimalMapper;
import com.shelter.core.repository.AnimalPhotoRepository;
import com.shelter.core.repository.EmployeeRepository;
import com.shelter.core.repository.PublicAnimalView;
import com.shelter.core.repository.PublicCatalogRepository;
import com.shelter.core.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicCatalogAndEmployeeServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    private final PublicCatalogRepository catalogRepository = mock(PublicCatalogRepository.class);
    private final AnimalPhotoRepository photoRepository = mock(AnimalPhotoRepository.class);
    private final PublicCatalogService catalog = new PublicCatalogService(catalogRepository, photoRepository,
            new PublicAnimalMapper(), Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @Test
    void catalogFiltersAreValidated() {
        assertThatThrownBy(() -> catalog.findPage("X", null, null, 0, 12)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> catalog.findPage(null, -1, null, 0, 12)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> catalog.findPage(null, 5, 2, 0, 12)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> catalog.findPage(null, null, null, 0, 0)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void animalOutsideCatalogIsNotFound() {
        when(catalogRepository.findVisible(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalog.findById(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("каталоге");
    }

    @Test
    void catalogCardHasAgeReservedFlagAndPublicPhotoLinks() {
        when(catalogRepository.findVisible(1L)).thenReturn(Optional.of(new PublicAnimalView(1L, "Снежка", "F",
                TODAY.minusYears(2).minusMonths(3), "белая", null, "Пугливая", null, "RESERVED", true, false, 7L)));
        when(photoRepository.findByAnimal(1L)).thenReturn(List.of());

        var card = catalog.findById(1L);

        assertThat(card.ageYears()).isEqualTo(2);
        assertThat(card.ageMonths()).isEqualTo(3);
        assertThat(card.reserved()).isTrue();
        assertThat(card.sterilized()).isTrue();
        assertThat(card.mainPhotoUrl()).isEqualTo("/public/photos/7");
    }

    // ---------- связь пользователь ↔ сотрудник ----------

    @Test
    void actionWithoutEmployeeCardGives409() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        CurrentUser user = mock(CurrentUser.class);
        when(user.id()).thenReturn(42L);
        when(employees.findActiveIdByUserId(42L)).thenReturn(Optional.empty());
        EmployeeService service = new EmployeeService(employees, user);

        assertThatThrownBy(service::currentEmployeeId)
                .isInstanceOf(EmployeeNotLinkedException.class)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("не связана с карточкой сотрудника");
    }

    @Test
    void linkedUserGetsEmployeeId() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        CurrentUser user = mock(CurrentUser.class);
        when(user.id()).thenReturn(42L);
        when(employees.findActiveIdByUserId(42L)).thenReturn(Optional.of(7L));

        assertThat(new EmployeeService(employees, user).currentEmployeeId()).isEqualTo(7L);
    }
}
