package pl.rynek.media.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.media.api.dto.InternalMediaResponse;
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
}
