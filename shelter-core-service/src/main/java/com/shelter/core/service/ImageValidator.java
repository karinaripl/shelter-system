package com.shelter.core.service;

import com.shelter.core.exception.InvalidRequestException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Проверка загружаемого фото по содержимому, а не по расширению или заголовку Content-Type:
 * сигнатура файла (JPEG или PNG) и разбор заголовка изображения.
 * Изображение целиком не декодируется — иначе маленький файл с огромными размерами занял бы всю память.
 */
@Component
public class ImageValidator {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 10_000;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public enum ImageType {
        JPEG("jpg", MediaType.IMAGE_JPEG),
        PNG("png", MediaType.IMAGE_PNG);

        private final String extension;
        private final MediaType mediaType;

        ImageType(String extension, MediaType mediaType) {
            this.extension = extension;
            this.mediaType = mediaType;
        }

        public String extension() {
            return extension;
        }

        public MediaType mediaType() {
            return mediaType;
        }

        public static MediaType mediaTypeOf(String fileName) {
            return fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        }
    }

    public ImageType validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidRequestException("Файл пустой");
        }
        if (content.length > MAX_SIZE_BYTES) {
            throw new InvalidRequestException("Файл слишком большой: допускается не больше 5 МБ");
        }

        ImageType type;
        if (startsWith(content, JPEG_SIGNATURE)) {
            type = ImageType.JPEG;
        } else if (startsWith(content, PNG_SIGNATURE)) {
            type = ImageType.PNG;
        } else {
            throw new InvalidRequestException("Допускаются только изображения JPG и PNG");
        }

        checkReadable(content, type);
        return type;
    }

    private static void checkReadable(byte[] content, ImageType type) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(type == ImageType.JPEG ? "jpeg" : "png");
            if (input == null || !readers.hasNext()) {
                throw new InvalidRequestException("Не удалось прочитать изображение");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new InvalidRequestException(
                            "Размер изображения должен быть не больше " + MAX_DIMENSION + "×" + MAX_DIMENSION + " пикселей");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidRequestException invalid) {
                throw invalid;
            }
            throw new InvalidRequestException("Файл повреждён или не является изображением JPG/PNG");
        }
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length
                && Arrays.equals(Arrays.copyOf(content, signature.length), signature);
    }
}
