package com.memphisreo.media;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Тип файлу за "магічними байтами", а не за заявленим Content-Type чи
 * розширенням — їх контролює клієнт і їх легко підробити.
 */
public enum FileSignature {

    JPEG("image/jpeg", true),
    PNG("image/png", true),
    WEBP("image/webp", true),
    HEIC("image/heic", false),
    MP4("video/mp4", false),
    QUICKTIME("video/quicktime", false);

    /** Скільки перших байтів достатньо для розпізнавання. */
    public static final int HEADER_LENGTH = 16;

    private final String mimeType;
    private final boolean image;

    FileSignature(String mimeType, boolean image) {
        this.mimeType = mimeType;
        this.image = image;
    }

    public String mimeType() {
        return mimeType;
    }

    /** Чи можемо ми декодувати й перекодувати це зображення. */
    public boolean isProcessableImage() {
        return image;
    }

    public boolean isVideo() {
        return this == MP4 || this == QUICKTIME;
    }

    public static Optional<FileSignature> detect(byte[] header) {
        if (startsWith(header, 0, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Optional.of(JPEG);
        }
        if (startsWith(header, 0, new byte[]{(byte) 0x89, 'P', 'N', 'G'})) {
            return Optional.of(PNG);
        }
        if (ascii(header, 0, "RIFF") && ascii(header, 8, "WEBP")) {
            return Optional.of(WEBP);
        }
        if (ascii(header, 4, "ftyp") && header.length >= 12) {
            String brand = new String(Arrays.copyOfRange(header, 8, 12), StandardCharsets.US_ASCII);
            return switch (brand) {
                case "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1" -> Optional.of(HEIC);
                case "qt  " -> Optional.of(QUICKTIME);
                default -> Optional.of(MP4);
            };
        }
        return Optional.empty();
    }

    private static boolean ascii(byte[] data, int offset, String expected) {
        return startsWith(data, offset, expected.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
