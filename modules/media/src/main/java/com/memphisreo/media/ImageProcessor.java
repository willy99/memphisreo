package com.memphisreo.media;

import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Готує фото до зберігання: застосовує EXIF-орієнтацію, зменшує й
 * перекодовує в JPEG. Перекодування не переносить метадані — EXIF/GPS
 * (де саме знято фото) не потрапляють у сховище.
 */
@Component
public class ImageProcessor {

    static final int LARGE_EDGE = 1920;
    static final int THUMB_EDGE = 480;
    /** Захист від "декомпресійних бомб": 50 Мп ≈ 150 МБ у пам'яті після декодування. */
    static final long MAX_PIXELS = 50_000_000L;
    private static final float JPEG_QUALITY = 0.82f;

    public record Variant(byte[] jpeg, int width, int height) {
    }

    public record Processed(Variant large, Variant thumb) {
    }

    public Processed process(byte[] source) {
        Dimensions dimensions = readDimensions(source);
        if ((long) dimensions.width() * dimensions.height() > MAX_PIXELS) {
            throw invalid("imageTooLarge");
        }
        try {
            BufferedImage oriented = Thumbnails.of(new ByteArrayInputStream(source))
                    .scale(1.0)
                    .useExifOrientation(true)
                    .asBufferedImage();
            return new Processed(variant(oriented, LARGE_EDGE), variant(oriented, THUMB_EDGE));
        } catch (IOException | IllegalArgumentException e) {
            throw invalid("unreadableImage");
        }
    }

    private static Variant variant(BufferedImage image, int maxEdge) throws IOException {
        BufferedImage resized = Math.max(image.getWidth(), image.getHeight()) <= maxEdge
                ? image
                : Thumbnails.of(image).size(maxEdge, maxEdge).asBufferedImage();
        BufferedImage rgb = toRgb(resized);
        return new Variant(writeJpeg(rgb), rgb.getWidth(), rgb.getHeight());
    }

    /** JPEG без альфа-каналу: прозорість PNG — на білому тлі, а не чорному. */
    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static byte[] writeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private record Dimensions(int width, int height) {
    }

    /** Розміри з заголовка — без декодування всього зображення. */
    private static Dimensions readDimensions(byte[] source) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid("unreadableImage");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new Dimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw invalid("unreadableImage");
        }
    }

    private static ValidationException invalid(String code) {
        return new ValidationException("Некоректне зображення", List.of(new FieldError("file", code)));
    }
}
