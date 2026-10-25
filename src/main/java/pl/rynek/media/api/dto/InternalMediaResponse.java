package pl.rynek.media.api.dto;

import java.util.UUID;

import pl.rynek.media.media.Media;
import pl.rynek.media.media.MediaPurpose;

/** Dla channel i catalog: właściciel i przeznaczenie do sprawdzenia; {@code url} tylko dla plików publicznych. */
public record InternalMediaResponse(UUID id, UUID ownerId, MediaPurpose purpose, String contentType, String url) {

    public static InternalMediaResponse of(Media media, String publicUrl) {
        return new InternalMediaResponse(media.getId(), media.getOwnerId(), media.getPurpose(), media.getContentType(), publicUrl);
    }
}
