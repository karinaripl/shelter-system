package com.shelter.core.repository;

import org.junit.jupiter.api.Test;
import org.jooq.exception.DataAccessException;

import java.time.LocalDate;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;
import static com.shelter.model.generated.shelter.Tables.ANIMAL_PHOTO;
import static com.shelter.model.generated.shelter.Tables.MEDICAL_CARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoAndCatalogRepositoryIT extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @Test
    void animalHasAtMostOneMainPhoto() {
        Long id = animal("Барсик", "AVAILABLE", cage("A-01", 2), TODAY, null);
        AnimalPhotoRepository photos = new AnimalPhotoRepository(dsl);
        photos.insert(id, "a.png", 0, true);

        assertThatThrownBy(() -> photos.insert(id, "b.png", 1, true))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_photo_main");
    }

    @Test
    void makeMainSwitchesMainPhotoAndOrderGrows() {
        Long id = animal("Барсик", "AVAILABLE", cage("A-01", 2), TODAY, null);
        AnimalPhotoRepository photos = new AnimalPhotoRepository(dsl);
        assertThat(photos.nextSortOrder(id)).isZero();
        Long first = photos.insert(id, "a.png", 0, true).getId();
        Long second = photos.insert(id, "b.png", photos.nextSortOrder(id), false).getId();

        photos.makeMain(id, second);

        assertThat(photos.findByAnimal(id)).extracting(r -> r.getId() + ":" + r.getIsMain())
                .containsExactly(first + ":false", second + ":true");
        assertThat(photos.nextSortOrder(id)).isEqualTo(2);
    }

    @Test
    void photosAreDeletedWithAnimal() {
        Long id = animal("Барсик", "AVAILABLE", cage("A-01", 2), TODAY, null);
        new AnimalPhotoRepository(dsl).insert(id, "a.png", 0, true);

        dsl.deleteFrom(ANIMAL).where(ANIMAL.ID.eq(id)).execute();

        assertThat(dsl.fetchCount(ANIMAL_PHOTO)).isZero();
    }

    @Test
    void catalogShowsOnlyAvailableOrReservedAnimalsInShelterWithoutQuarantine() {
        PublicCatalogRepository catalog = new PublicCatalogRepository(dsl);
        Long a1 = cage("A-01", 10);
        Long available = animal("Готов", "AVAILABLE", a1, TODAY, null);
        Long reserved = animal("Бронь", "RESERVED", a1, TODAY, null);
        Long inShelter = animal("Адаптация", "IN_SHELTER", a1, TODAY, null);
        Long foster = animal("Передержка", "FOSTER", null, TODAY, null);
        Long quarantine = animal("Карантин", "AVAILABLE", a1, TODAY, null);
        dsl.update(ANIMAL).set(ANIMAL.HEALTH_STATE, "QUARANTINE").where(ANIMAL.ID.eq(quarantine)).execute();
        Long treatment = animal("Лечение", "AVAILABLE", a1, TODAY, null);
        dsl.update(ANIMAL).set(ANIMAL.HEALTH_STATE, "TREATMENT").where(ANIMAL.ID.eq(treatment)).execute();
        Long adopted = animal("Пристроен", "ADOPTED", null, TODAY.minusDays(5), TODAY);

        assertThat(catalog.findPage(null, null, null, TODAY, 0, 50))
                .extracting(PublicAnimalView::name)
                .containsExactlyInAnyOrder("Готов", "Бронь", "Лечение");
        assertThat(catalog.count(null, null, null, TODAY)).isEqualTo(3);
        assertThat(catalog.isVisible(available)).isTrue();
        assertThat(catalog.isVisible(reserved)).isTrue();
        assertThat(catalog.isVisible(inShelter)).isFalse();
        assertThat(catalog.isVisible(foster)).isFalse();
        assertThat(catalog.isVisible(quarantine)).isFalse();
        assertThat(catalog.isVisible(adopted)).isFalse();
    }

    @Test
    void catalogFiltersByGenderAndAgeAndShowsMedicalFlags() {
        PublicCatalogRepository catalog = new PublicCatalogRepository(dsl);
        Long a1 = cage("A-01", 10);
        Long kitten = animal("Котёнок", "AVAILABLE", a1, TODAY, null);
        Long adult = animal("Взрослый", "AVAILABLE", a1, TODAY, null);
        Long unknownAge = animal("Без даты", "AVAILABLE", a1, TODAY, null);
        dsl.update(ANIMAL).set(ANIMAL.BIRTH_DATE, TODAY.minusMonths(4)).set(ANIMAL.GENDER, "F").where(ANIMAL.ID.eq(kitten)).execute();
        dsl.update(ANIMAL).set(ANIMAL.BIRTH_DATE, TODAY.minusYears(5)).set(ANIMAL.GENDER, "M").where(ANIMAL.ID.eq(adult)).execute();
        dsl.insertInto(MEDICAL_CARD).set(MEDICAL_CARD.ANIMAL_ID, adult).set(MEDICAL_CARD.IS_STERILIZED, true).execute();

        assertThat(catalog.findPage("F", null, null, TODAY, 0, 10)).extracting(PublicAnimalView::name).containsExactly("Котёнок");
        assertThat(catalog.findPage(null, 0, 0, TODAY, 0, 10)).extracting(PublicAnimalView::name).containsExactly("Котёнок");
        assertThat(catalog.findPage(null, 3, 10, TODAY, 0, 10)).extracting(PublicAnimalView::name).containsExactly("Взрослый");
        assertThat(catalog.findPage(null, 6, null, TODAY, 0, 10)).isEmpty();
        assertThat(catalog.count(null, null, null, TODAY)).isEqualTo(3);
        assertThat(catalog.findVisible(adult)).get().extracting(PublicAnimalView::sterilized).isEqualTo(true);
        assertThat(catalog.findVisible(unknownAge)).get().extracting(PublicAnimalView::sterilized).isEqualTo(false);
    }
}
