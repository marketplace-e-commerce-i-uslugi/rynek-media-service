package pl.rynek.media.api;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.media.api.dto.CreateUploadRequest;
import pl.rynek.media.api.dto.MediaResponse;
import pl.rynek.media.api.dto.UpdateMediaRequest;
import pl.rynek.media.api.dto.UploadTicketResponse;
import pl.rynek.media.config.Viewer;
import pl.rynek.media.media.Media;
import pl.rynek.media.media.MediaService;

@RestController
public class MediaController {

    private final MediaService media;
    private final Viewer viewer;

    public MediaController(MediaService media, Viewer viewer) {
        this.media = media;
        this.viewer = viewer;
    }

    /**
     * Wystawia podpisany URL do wysłania pliku (purpose: OFFER_PHOTO, AVATAR, BANNER, KYC_DOCUMENT). Dostęp: user.
     */
    @PostMapping("/api/v1/media/uploads")
    public ResponseEntity<UploadTicketResponse> createUpload(@AuthenticationPrincipal Jwt token,
                                                             @Valid @RequestBody CreateUploadRequest request) {
        MediaService.Upload upload = media.createUpload(userId(token), request.purpose(), request.fileName(),
                request.contentType(), request.sizeBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(UploadTicketResponse.of(upload));
    }

    /**
     * Potwierdzenie wysłania, start przetwarzania (miniatury, WebP). Dostęp: user.
     */
    @PostMapping("/api/v1/media/uploads/{uploadId}/complete")
    public MediaResponse completeUpload(@AuthenticationPrincipal Jwt token, @PathVariable String uploadId) {
        return view(media.complete(userId(token), uploadId));
    }

    /**
     * Metadane i adresy wariantów. Dostęp: pub.
     */
    @GetMapping("/api/v1/media/{mediaId}")
    public MediaResponse getMedia(@PathVariable String mediaId, HttpServletRequest http) {
        return view(media.visible(mediaId, viewer.of(http)));
    }

    /**
     * Tekst alternatywny (altText). Dostęp: user.
     */
    @PatchMapping("/api/v1/media/{mediaId}")
    public MediaResponse updateMedia(@AuthenticationPrincipal Jwt token, @PathVariable String mediaId,
                                     @Valid @RequestBody UpdateMediaRequest request) {
        return view(media.describe(userId(token), mediaId, request.altText()));
    }

    /**
     * Usunięcie. Dostęp: user.
     */
    @DeleteMapping("/api/v1/media/{mediaId}")
    public ResponseEntity<Void> deleteMedia(@AuthenticationPrincipal Jwt token, @PathVariable String mediaId) {
        media.delete(userId(token), mediaId);
        return ResponseEntity.noContent().build();
    }

    private MediaResponse view(Media file) {
        return MediaResponse.of(file, media.urlOf(file));
    }

    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
