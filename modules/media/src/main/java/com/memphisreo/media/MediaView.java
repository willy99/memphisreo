package com.memphisreo.media;

import java.util.UUID;

/** Медіа для UI: посилання — тимчасові підписані URL сховища. */
public record MediaView(
        UUID id,
        PropertyMedia.Kind kind,
        int position,
        boolean cover,
        String caption,
        String thumbUrl,
        String url,
        String externalUrl,
        String mimeType,
        Long sizeBytes,
        Integer width,
        Integer height,
        String originalFilename
) {
}
