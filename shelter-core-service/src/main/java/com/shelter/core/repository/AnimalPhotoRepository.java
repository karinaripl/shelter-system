package com.shelter.core.repository;

import com.shelter.model.generated.shelter.tables.records.AnimalPhotoRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.ANIMAL_PHOTO;

@Repository
public class AnimalPhotoRepository {

    private final DSLContext dsl;

    public AnimalPhotoRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public AnimalPhotoRecord insert(Long animalId, String filePath, int sortOrder, boolean main) {
        return dsl.insertInto(ANIMAL_PHOTO)
                .set(ANIMAL_PHOTO.ANIMAL_ID, animalId)
                .set(ANIMAL_PHOTO.FILE_PATH, filePath)
                .set(ANIMAL_PHOTO.SORT_ORDER, sortOrder)
                .set(ANIMAL_PHOTO.IS_MAIN, main)
                .returning()
                .fetchOne();
    }

    /** Фото животного по порядку показа. */
    public List<AnimalPhotoRecord> findByAnimal(Long animalId) {
        return dsl.selectFrom(ANIMAL_PHOTO)
                .where(ANIMAL_PHOTO.ANIMAL_ID.eq(animalId))
                .orderBy(ANIMAL_PHOTO.SORT_ORDER, ANIMAL_PHOTO.ID)
                .fetch();
    }

    public Optional<AnimalPhotoRecord> findById(Long photoId) {
        return dsl.selectFrom(ANIMAL_PHOTO).where(ANIMAL_PHOTO.ID.eq(photoId)).fetchOptional();
    }

    public int nextSortOrder(Long animalId) {
        Integer max = dsl.select(DSL.max(ANIMAL_PHOTO.SORT_ORDER))
                .from(ANIMAL_PHOTO)
                .where(ANIMAL_PHOTO.ANIMAL_ID.eq(animalId))
                .fetchOne(0, Integer.class);
        return max == null ? 0 : max + 1;
    }

    /** Делает фото главным. Сначала снимается признак с остальных — иначе сработает уникальный индекс. */
    public void makeMain(Long animalId, Long photoId) {
        dsl.update(ANIMAL_PHOTO)
                .set(ANIMAL_PHOTO.IS_MAIN, false)
                .where(ANIMAL_PHOTO.ANIMAL_ID.eq(animalId).and(ANIMAL_PHOTO.IS_MAIN.isTrue()))
                .execute();
        dsl.update(ANIMAL_PHOTO)
                .set(ANIMAL_PHOTO.IS_MAIN, true)
                .where(ANIMAL_PHOTO.ID.eq(photoId))
                .execute();
    }

    public void delete(Long photoId) {
        dsl.deleteFrom(ANIMAL_PHOTO).where(ANIMAL_PHOTO.ID.eq(photoId)).execute();
    }

    public List<String> findAllFilePaths() {
        return dsl.select(ANIMAL_PHOTO.FILE_PATH).from(ANIMAL_PHOTO).fetch(ANIMAL_PHOTO.FILE_PATH);
    }
}
