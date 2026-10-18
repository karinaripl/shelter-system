package com.shelter.core.service;

import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.PhotoMapper;
import com.shelter.core.repository.AnimalPhotoRepository;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.AnimalView;
import com.shelter.core.repository.PublicCatalogRepository;
import com.shelter.model.generated.shelter.tables.records.AnimalPhotoRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnimalPhotoServiceTest {

    private static final long ANIMAL_ID = 5L;

    private AnimalPhotoRepository photoRepository;
    private AnimalRepository animalRepository;
    private PublicCatalogRepository catalogRepository;
    private PhotoStorage storage;
    private AnimalPhotoService service;

    @BeforeEach
    void setUp() {
        photoRepository = mock(AnimalPhotoRepository.class);
        animalRepository = mock(AnimalRepository.class);
        catalogRepository = mock(PublicCatalogRepository.class);
        storage = mock(PhotoStorage.class);
        service = new AnimalPhotoService(photoRepository, animalRepository, catalogRepository, storage,
                new ImageValidator(), new PhotoMapper());

        when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(mock(AnimalView.class)));
        when(storage.save(any(), anyString())).thenReturn("saved.png");
        when(photoRepository.insert(anyLong(), anyString(), anyInt(), anyBoolean()))
                .thenAnswer(inv -> photo(1L, ANIMAL_ID, inv.getArgument(2), inv.getArgument(3)));
    }

    @Test
    void firstPhotoBecomesMain() throws IOException {
        when(photoRepository.findByAnimal(ANIMAL_ID)).thenReturn(List.of());

        var result = service.upload(ANIMAL_ID, png());

        assertThat(result.main()).isTrue();
        verify(photoRepository).insert(ANIMAL_ID, "saved.png", 0, true);
    }

    @Test
    void nextPhotoIsNotMainAndGoesLast() throws IOException {
        when(photoRepository.findByAnimal(ANIMAL_ID)).thenReturn(List.of(photo(1L, ANIMAL_ID, 0, true)));
        when(photoRepository.nextSortOrder(ANIMAL_ID)).thenReturn(1);

        var result = service.upload(ANIMAL_ID, png());

        assertThat(result.main()).isFalse();
        verify(photoRepository).insert(ANIMAL_ID, "saved.png", 1, false);
    }

    @Test
    void notAnImageIsRejectedBeforeSaving() {
        var file = new MockMultipartFile("file", "cat.png", "image/png", "просто текст".getBytes());

        assertThatThrownBy(() -> service.upload(ANIMAL_ID, file)).isInstanceOf(InvalidRequestException.class);
        verify(storage, never()).save(any(), anyString());
    }

    @Test
    void fileIsRemovedWhenDatabaseInsertFails() throws IOException {
        when(photoRepository.findByAnimal(ANIMAL_ID)).thenReturn(List.of());
        when(photoRepository.insert(anyLong(), anyString(), anyInt(), anyBoolean()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.upload(ANIMAL_ID, png())).isInstanceOf(IllegalStateException.class);
        verify(storage).deleteQuietly("saved.png");
    }

    @Test
    void deletingMainPhotoMakesNextOneMain() {
        when(photoRepository.findById(1L)).thenReturn(Optional.of(photo(1L, ANIMAL_ID, 0, true)));
        when(photoRepository.findByAnimal(ANIMAL_ID)).thenReturn(List.of(photo(2L, ANIMAL_ID, 1, false)));

        service.delete(ANIMAL_ID, 1L);

        verify(photoRepository).delete(1L);
        verify(photoRepository).makeMain(ANIMAL_ID, 2L);
        verify(storage).deleteQuietly("photo-1.png");
    }

    @Test
    void photoOfAnotherAnimalIsNotFound() {
        when(photoRepository.findById(1L)).thenReturn(Optional.of(photo(1L, 999L, 0, true)));

        assertThatThrownBy(() -> service.makeMain(ANIMAL_ID, 1L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(ANIMAL_ID, 1L)).isInstanceOf(NotFoundException.class);
        verify(photoRepository, never()).delete(anyLong());
    }

    @Test
    void publicPhotoOnlyForAnimalsInCatalog() {
        when(photoRepository.findById(1L)).thenReturn(Optional.of(photo(1L, ANIMAL_ID, 0, true)));
        when(catalogRepository.isVisible(ANIMAL_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.publicFile(1L)).isInstanceOf(NotFoundException.class);

        when(catalogRepository.isVisible(ANIMAL_ID)).thenReturn(true);
        when(storage.read("photo-1.png")).thenReturn(new byte[]{1});
        assertThat(service.publicFile(1L).content()).containsExactly(1);
    }

    @Test
    void uploadToUnknownAnimalGives404() throws IOException {
        when(animalRepository.lockById(eq(404L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(404L, png())).isInstanceOf(NotFoundException.class);
    }

    private static AnimalPhotoRecord photo(Long id, Long animalId, int sortOrder, boolean main) {
        AnimalPhotoRecord record = new AnimalPhotoRecord();
        record.setId(id);
        record.setAnimalId(animalId);
        record.setFilePath("photo-" + id + ".png");
        record.setSortOrder(sortOrder);
        record.setIsMain(main);
        return record;
    }

    private static MockMultipartFile png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "cat.png", "image/png", out.toByteArray());
    }
}
