package com.memphisreo.media;

import com.memphisreo.common.ValidationException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void largeImage_isDownscaledKeepingAspect_smallImageIsNotUpscaled() throws IOException {
        ImageProcessor.Processed big = processor.process(png(4000, 1000));
        assertThat(big.large().width()).isEqualTo(1920);
        assertThat(big.large().height()).isEqualTo(480);
        assertThat(big.thumb().width()).isEqualTo(480);

        ImageProcessor.Processed small = processor.process(png(300, 200));
        assertThat(small.large().width()).isEqualTo(300);
        assertThat(small.thumb().width()).isEqualTo(300);
    }

    @Test
    void outputIsJpeg() throws IOException {
        byte[] jpeg = processor.process(png(100, 100)).large().jpeg();
        assertThat(FileSignature.detect(jpeg)).contains(FileSignature.JPEG);
    }

    @Test
    void garbage_isRejected() {
        assertThatThrownBy(() -> processor.process(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3}))
                .isInstanceOf(ValidationException.class);
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
