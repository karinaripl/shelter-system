package com.shelter.core.service;

import com.shelter.core.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator();

    @Test
    void acceptsRealPngAndJpeg() throws IOException {
        assertThat(validator.validate(image("png"))).isEqualTo(ImageValidator.ImageType.PNG);
        assertThat(validator.validate(image("jpg"))).isEqualTo(ImageValidator.ImageType.JPEG);
    }

    @Test
    void rejectsTextFileRegardlessOfName() {
        byte[] text = "Это не картинка".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> validator.validate(text))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("JPG и PNG");
    }

    @Test
    void rejectsOtherImageFormats() throws IOException {
        assertThatThrownBy(() -> validator.validate(image("gif")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsPngSignatureWithBrokenContent() {
        byte[] fake = Arrays.copyOf(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 64);
        assertThatThrownBy(() -> validator.validate(fake))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("повреждён");
    }

    @Test
    void rejectsEmptyAndTooLarge() {
        assertThatThrownBy(() -> validator.validate(new byte[0])).isInstanceOf(InvalidRequestException.class);
        byte[] big = new byte[(int) ImageValidator.MAX_SIZE_BYTES + 1];
        big[0] = (byte) 0xFF;
        assertThatThrownBy(() -> validator.validate(big))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("5 МБ");
    }

    private static byte[] image(String format) throws IOException {
        BufferedImage img = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }
}
