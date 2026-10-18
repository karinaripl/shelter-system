package com.shelter.core.config;

import com.shelter.core.repository.AnimalPhotoRepository;
import com.shelter.core.service.PhotoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Демо-данные (контекст demo) ссылаются на фото-заглушки demo-*.png. Сами файлы входят в сервис
 * (resources/demo-photos) и при запуске копируются в хранилище, если их там ещё нет.
 * Без демо-данных в базе нет таких записей, и ничего не копируется.
 */
@Component
public class DemoPhotoInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoPhotoInitializer.class);

    private final AnimalPhotoRepository photoRepository;
    private final PhotoStorage storage;

    public DemoPhotoInitializer(AnimalPhotoRepository photoRepository, PhotoStorage storage) {
        this.photoRepository = photoRepository;
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) {
        int copied = 0;
        for (String fileName : photoRepository.findAllFilePaths()) {
            if (!fileName.startsWith("demo-") || storage.exists(fileName)) {
                continue;
            }
            ClassPathResource resource = new ClassPathResource("demo-photos/" + fileName);
            if (!resource.exists()) {
                log.warn("Нет файла-заглушки для демо-фото {}", fileName);
                continue;
            }
            try (InputStream content = resource.getInputStream()) {
                storage.saveAs(fileName, content);
                copied++;
            } catch (IOException e) {
                log.warn("Не удалось скопировать демо-фото {}: {}", fileName, e.getMessage());
            }
        }
        if (copied > 0) {
            log.info("Скопировано демо-фото в хранилище: {}", copied);
        }
    }
}
