package com.memphisreo.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileSignatureTest {

    @Test
    void detectsByMagicBytes_notByName() {
        assertThat(FileSignature.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).contains(FileSignature.JPEG);
        assertThat(FileSignature.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D})).contains(FileSignature.PNG);
        assertThat(FileSignature.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes())).contains(FileSignature.WEBP);
        assertThat(FileSignature.detect("<html>".getBytes())).isEmpty();
        assertThat(FileSignature.detect(new byte[0])).isEmpty();
    }

    @Test
    void isoBmffBrands_distinguishHeicMovAndMp4() {
        assertThat(FileSignature.detect(ftyp("heic"))).contains(FileSignature.HEIC);
        assertThat(FileSignature.detect(ftyp("mif1"))).contains(FileSignature.HEIC);
        assertThat(FileSignature.detect(ftyp("qt  "))).contains(FileSignature.QUICKTIME);
        assertThat(FileSignature.detect(ftyp("isom"))).contains(FileSignature.MP4);
        assertThat(FileSignature.detect(ftyp("mp42"))).contains(FileSignature.MP4);
    }

    private static byte[] ftyp(String brand) {
        byte[] header = new byte[16];
        System.arraycopy("ftyp".getBytes(), 0, header, 4, 4);
        System.arraycopy(brand.getBytes(), 0, header, 8, 4);
        return header;
    }
}
