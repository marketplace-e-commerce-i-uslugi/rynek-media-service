package pl.rynek.media.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.media.api.dto.CreateUploadRequest;
import pl.rynek.media.api.dto.UpdateMediaRequest;

import static pl.rynek.media.common.NotImplemented.notImplemented;

@RestController
public class MediaController {

    /**
     * Wystawia podpisany URL do wysłania pliku (purpose: OFFER_PHOTO, AVATAR, BANNER, KYC_DOCUMENT). Dostęp: user.
     */
    @PostMapping("/api/v1/media/uploads")
    public void createUpload(@RequestBody CreateUploadRequest request) {
        throw notImplemented();
    }

    /**
     * Potwierdzenie wysłania, start przetwarzania (miniatury, WebP). Dostęp: user.
     */
    @PostMapping("/api/v1/media/uploads/{uploadId}/complete")
    public void completeUpload(@PathVariable String uploadId) {
        throw notImplemented();
    }

    /**
     * Metadane i adresy wariantów. Dostęp: pub.
     */
    @GetMapping("/api/v1/media/{mediaId}")
    public void getMedia(@PathVariable String mediaId) {
        throw notImplemented();
    }

    /**
     * Tekst alternatywny (altText). Dostęp: user.
     */
    @PatchMapping("/api/v1/media/{mediaId}")
    public void updateMedia(@PathVariable String mediaId, @RequestBody UpdateMediaRequest request) {
        throw notImplemented();
    }

    /**
     * Usunięcie. Dostęp: user.
     */
    @DeleteMapping("/api/v1/media/{mediaId}")
    public void deleteMedia(@PathVariable String mediaId) {
        throw notImplemented();
    }
}
