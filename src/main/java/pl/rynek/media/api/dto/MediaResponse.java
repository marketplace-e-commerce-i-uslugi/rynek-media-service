package pl.rynek.media.api.dto;

import java.util.Map;
import java.util.UUID;

import pl.rynek.media.media.Media;
import pl.rynek.media.media.MediaPurpose;

/**
 * {@code status} jest zawsze {@code READY}, bo pliki nie są jeszcze przetwarzane; z tego samego powodu
 * {@code variants} (miniatury, WebP) jest puste.
 */
public record MediaResponse(UUID id, MediaPurpose purpose, String status, String url, Map<String, String> variants,
                            String altText) {

    public static MediaResponse of(Media media, String url) {
        return new MediaResponse(media.getId(), media.getPurpose(), media.getStatus().name(), url, Map.of(), media.getAltText());
    }
}
