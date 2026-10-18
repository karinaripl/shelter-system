package com.shelter.core.service;

import com.shelter.core.config.PhotoStorageProperties;
import com.shelter.core.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Файлы фотографий на диске. Имя файла генерируется сервисом (UUID), поэтому пользователь
 * не может повлиять на путь; имена из базы дополнительно проверяются шаблоном.
 */
@Component
public class PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorage.class);
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,200}");

    private final Path root;

    public PhotoStorage(PhotoStorageProperties properties) {
        this.root = Path.of(properties.dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось создать каталог для фото: " + root, e);
        }
    }

    /** Сохраняет файл и возвращает его имя. */
    public String save(byte[] content, String extension) {
        String name = UUID.randomUUID() + "." + extension;
        try {
            Files.write(resolve(name), content);
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить фото", e);
        }
    }

    public void saveAs(String name, InputStream content) throws IOException {
        Files.copy(content, resolve(name));
    }

    public boolean exists(String name) {
        return Files.isRegularFile(resolve(name));
    }

    public byte[] read(String name) {
        Path path = resolve(name);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Файл фото не найден");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать фото", e);
        }
    }

    /** Удаление файла не должно ломать операцию: запись в базе уже удалена, «осиротевший» файл безвреден. */
    public void deleteQuietly(String name) {
        try {
            Files.deleteIfExists(resolve(name));
        } catch (IOException | RuntimeException e) {
            log.warn("Не удалось удалить файл фото {}: {}", name, e.getMessage());
        }
    }

    private Path resolve(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Недопустимое имя файла фото");
        }
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Недопустимое имя файла фото");
        }
        return path;
    }
}
