package pl.rynek.media.api.dto;

import java.time.Instant;
import java.util.UUID;

import pl.rynek.media.media.MediaService.Upload;

/** Plik idzie metodą PUT pod {@code uploadUrl} z nagłówkiem {@code Content-Type} z deklaracji. */
public record UploadTicketResponse(UUID uploadId, UUID mediaId, String uploadUrl, Instant expiresAt) {

    public static UploadTicketResponse of(Upload upload) {
        return new UploadTicketResponse(upload.media().getUploadId(), upload.media().getId(), upload.uploadUrl(), upload.expiresAt());
    }
}
