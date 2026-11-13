package pl.rynek.media.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.media.api.dto.InternalMediaResponse;
import pl.rynek.media.api.dto.InternalUrlResponse;
import pl.rynek.media.media.Media;
import pl.rynek.media.media.MediaService;

/** Ścieżki dla innych serwisów; gateway ich nie wystawia. */
@RestController
public class InternalMediaController {

    private final MediaService media;

    public InternalMediaController(MediaService media) {
        this.media = media;
    }

    /** Gotowy plik z właścicielem i przeznaczeniem (channel: awatar, baner, dokumenty; catalog: zdjęcia ofert). Dostęp: int. */
    @GetMapping("/internal/media/{mediaId}")
    public InternalMediaResponse getMedia(@PathVariable String mediaId) {
        Media file = media.internal(mediaId);
        return InternalMediaResponse.of(file, media.publicUrlOf(file).orElse(null));
    }

    /**
     * Adres do obejrzenia gotowego pliku (dla prywatnego krótko ważny, podpisany). Wołają serwisy, które same sprawdziły,
     * że oglądający ma do pliku prawo (np. chat-service: uczestnik rozmowy z załącznikiem). Dostęp: int.
     */
    @GetMapping("/internal/media/{mediaId}/url")
    public InternalUrlResponse getUrl(@PathVariable String mediaId) {
        return new InternalUrlResponse(media.urlOf(media.internal(mediaId)));
    }
}
