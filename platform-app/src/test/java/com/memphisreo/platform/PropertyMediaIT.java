package com.memphisreo.platform;

import com.memphisreo.media.MediaView;
import com.memphisreo.media.PropertyMedia;
import com.memphisreo.platform.api.PropertyMediaController;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyCard;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Медіатека: завантаження з перевіркою сигнатури, перекодування, порядок, обкладинка, ізоляція. */
class PropertyMediaIT extends AbstractIntegrationTest {

    private String token;
    private PropertyDetails property;

    @BeforeEach
    void agencyWithProperty() {
        String slug = "media-" + UUID.randomUUID().toString().substring(0, 8);
        register(slug, "admin@" + slug + ".ua", "Password123!", "UA");
        token = login("admin@" + slug + ".ua", "Password123!");
        property = createProperty(token);
    }

    @Test
    void photoUpload_isReencodedAndResized_firstPhotoIsCover() throws IOException {
        MediaView first = upload("PHOTO", "big.png", png(3000, 2000, true)).getBody();
        MediaView second = upload("PHOTO", "small.jpg", jpeg(800, 600)).getBody();

        assertThat(first.cover()).isTrue();
        assertThat(second.cover()).isFalse();
        assertThat(first.mimeType()).isEqualTo("image/jpeg");
        assertThat(first.width()).isEqualTo(1920);
        assertThat(first.height()).isEqualTo(1280);
        assertThat(second.width()).isEqualTo(800);

        BufferedImage storedThumb = ImageIO.read(new ByteArrayInputStream(objectStorage.objects.get(keyOf(first.thumbUrl()))));
        assertThat(Math.max(storedThumb.getWidth(), storedThumb.getHeight())).isEqualTo(480);
        // PNG з прозорістю — на білому тлі, а не чорному.
        assertThat(new Color(storedThumb.getRGB(5, 5)).getRed()).isGreaterThan(240);

        PropertyCard card = restTemplate.exchange("/api/properties", HttpMethod.GET, authed(token),
                PropertyCard[].class).getBody()[0];
        assertThat(card.coverThumbUrl()).isEqualTo(first.thumbUrl());
        assertThat(card.photoCount()).isEqualTo(2);
    }

    @Test
    void reorderAndCover_andDeletingCoverPromotesNextPhoto() throws IOException {
        MediaView a = upload("PHOTO", "a.jpg", jpeg(400, 300)).getBody();
        MediaView b = upload("PHOTO", "b.jpg", jpeg(400, 300)).getBody();
        MediaView c = upload("PHOTO", "c.jpg", jpeg(400, 300)).getBody();

        MediaView[] reordered = restTemplate.exchange(mediaPath() + "/order", HttpMethod.PUT,
                authed(token, new PropertyMediaController.OrderRequest(PropertyMedia.Kind.PHOTO,
                        List.of(c.id(), a.id(), b.id()))), MediaView[].class).getBody();
        assertThat(reordered).extracting(MediaView::id).containsExactly(c.id(), a.id(), b.id());

        MediaView[] afterCover = restTemplate.exchange(mediaPath() + "/" + b.id() + "/cover", HttpMethod.PUT,
                authed(token), MediaView[].class).getBody();
        assertThat(afterCover).filteredOn(MediaView::cover).extracting(MediaView::id).containsExactly(b.id());

        restTemplate.exchange(mediaPath() + "/" + b.id(), HttpMethod.DELETE, authed(token), Void.class);
        MediaView[] remaining = restTemplate.exchange(mediaPath(), HttpMethod.GET, authed(token), MediaView[].class).getBody();
        assertThat(remaining).extracting(MediaView::id).containsExactly(c.id(), a.id());
        assertThat(remaining[0].cover()).isTrue();
        assertThat(objectStorage.objects.keySet()).noneMatch(k -> k.contains(b.id().toString()));
    }

    @Test
    void parallelUploads_allSucceed_withOneCoverAndDistinctPositions() throws Exception {
        byte[] photo = jpeg(600, 400);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<ResponseEntity<String>>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                int n = i;
                results.add(pool.submit(() -> upload("PHOTO", "p" + n + ".jpg", photo, String.class)));
            }
            for (var result : results) {
                assertThat(result.get().getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        } finally {
            pool.shutdown();
        }
        MediaView[] media = restTemplate.exchange(mediaPath(), HttpMethod.GET, authed(token), MediaView[].class).getBody();
        assertThat(media).hasSize(6);
        assertThat(media).filteredOn(MediaView::cover).hasSize(1);
        assertThat(java.util.Arrays.stream(media).map(MediaView::position).distinct()).hasSize(6);
    }

    @Test
    void fileWithImageExtensionButOtherContent_isRejected() {
        ResponseEntity<String> response = upload("PHOTO", "fake.jpg", "<html>not an image</html>".getBytes(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("unsupportedType");
    }

    @Test
    void heicPhoto_isRejectedWithClearCode() {
        byte[] heicHeader = new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0, 0, 0};
        ResponseEntity<String> response = upload("PHOTO", "IMG_0001.HEIC", heicHeader, String.class);
        assertThat(response.getBody()).contains("heicNotSupported");
    }

    @Test
    void videoFile_isStoredAsIs_andVideoLinksAreAllowListed() {
        byte[] mp4 = new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0, 1, 2, 3};
        MediaView video = upload("VIDEO", "tour.mp4", mp4).getBody();
        assertThat(video.mimeType()).isEqualTo("video/mp4");
        assertThat(objectStorage.objects.get(keyOf(video.url()))).isEqualTo(mp4);

        ResponseEntity<MediaView> youtube = restTemplate.exchange(mediaPath() + "/links", HttpMethod.POST,
                authed(token, new PropertyMediaController.LinkRequest("https://www.youtube.com/watch?v=dQw4w9WgXcQ")),
                MediaView.class);
        assertThat(youtube.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> evil = restTemplate.exchange(mediaPath() + "/links", HttpMethod.POST,
                authed(token, new PropertyMediaController.LinkRequest("javascript:alert(1)")), String.class);
        assertThat(evil.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void otherAgency_cannotListOrUploadMedia() throws IOException {
        upload("PHOTO", "a.jpg", jpeg(400, 300));
        String slugB = "media-b-" + UUID.randomUUID().toString().substring(0, 8);
        register(slugB, "admin@" + slugB + ".ua", "Password123!", "UA");
        String tokenB = login("admin@" + slugB + ".ua", "Password123!");

        assertThat(restTemplate.exchange(mediaPath(), HttpMethod.GET, authed(tokenB), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(upload(tokenB, "PHOTO", "x.jpg", jpeg(400, 300), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String mediaPath() {
        return "/api/properties/" + property.id() + "/media";
    }

    private ResponseEntity<MediaView> upload(String kind, String filename, byte[] content) {
        return upload(kind, filename, content, MediaView.class);
    }

    private <T> ResponseEntity<T> upload(String kind, String filename, byte[] content, Class<T> type) {
        return upload(token, kind, filename, content, type);
    }

    private <T> ResponseEntity<T> upload(String bearer, String kind, String filename, byte[] content, Class<T> type) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("kind", kind);
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange(mediaPath(), HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    private static String keyOf(String fakeUrl) {
        return fakeUrl.replace("http://storage.test/", "");
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(40, 120, 100));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] png(int width, int height, boolean transparentCorner) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(40, 120, 100));
        g.fillRect(transparentCorner ? 200 : 0, transparentCorner ? 200 : 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
