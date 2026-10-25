package pl.rynek.media.media;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.media.storage.ObjectStorage;
import pl.rynek.media.storage.ObjectStorage.StoredObject;

/**
 * Wysyłka w dwóch krokach: {@link #createUpload} zapisuje metadane i wystawia podpisany URL, przeglądarka wysyła
 * plik prosto do magazynu, a {@link #complete} sprawdza, czy plik dotarł i zgadza się z deklaracją.
 */
@Service
public class MediaService {

    static final Duration UPLOAD_URL_VALIDITY = Duration.ofMinutes(15);
    static final Duration PRIVATE_URL_VALIDITY = Duration.ofMinutes(10);

    private final MediaRepository media;
    private final ObjectStorage storage;
    private final Clock clock;

    public MediaService(MediaRepository media, ObjectStorage storage, Clock clock) {
        this.media = media;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public Upload createUpload(UUID ownerId, MediaPurpose purpose, String fileName, String contentType, long sizeBytes) {
        if (!purpose.allows(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, unsupportedType(purpose));
        }
        if (sizeBytes > purpose.maxBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Plik jest za duży. Maksymalny rozmiar to " + purpose.maxBytes() / (1024 * 1024) + " MB.");
        }
        Instant now = clock.instant();
        Media file = media.save(new Media(ownerId, purpose, fileName.trim(), contentType, sizeBytes, now));
        String url = storage.presignPut(bucketOf(file), file.getObjectKey(), contentType, sizeBytes, UPLOAD_URL_VALIDITY);
        return new Upload(file, url, now.plus(UPLOAD_URL_VALIDITY));
    }

    /**
     * Ponowne potwierdzenie gotowego pliku zwraca go bez zmian. Plik niezgodny z deklaracją jest usuwany razem
     * z metadanymi, więc 400 nie wycofuje transakcji.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Media complete(UUID ownerId, String uploadId) {
        Media file = parse(uploadId).flatMap(media::findByUploadId)
                .filter(m -> m.isOwnedBy(ownerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy tej wysyłki."));
        if (file.isReady()) {
            return file;
        }
        String bucket = bucketOf(file);
        StoredObject stored = storage.head(bucket, file.getObjectKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Plik nie dotarł na serwer. Wyślij go ponownie."));
        if (stored.sizeBytes() != file.getSizeBytes()
                || !FileSignature.matches(file.getContentType(), storage.readHead(bucket, file.getObjectKey(), FileSignature.BYTES_NEEDED))) {
            storage.delete(bucket, file.getObjectKey());
            media.delete(file);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Zawartość pliku nie zgadza się z jego typem. Wybierz inny plik.");
        }
        file.markReady(clock.instant());
        return file;
    }

    /** Gotowy plik publiczny widzą wszyscy, prywatny tylko właściciel; pozostałym 404, żeby nie zdradzać, że istnieje. */
    @Transactional(readOnly = true)
    public Media visible(String mediaId, Optional<UUID> viewer) {
        return ready(mediaId)
                .filter(m -> m.getPurpose().isPublic() || viewer.map(m::isOwnedBy).orElse(false))
                .orElseThrow(MediaService::notFound);
    }

    @Transactional
    public Media describe(UUID userId, String mediaId, String altText) {
        Media file = owned(userId, mediaId);
        file.describe(altText == null || altText.isBlank() ? null : altText.trim());
        return file;
    }

    /**
     * Usuwa plik z magazynu i metadane. Nie sprawdza, czy plik jest jeszcze używany (awatar, oferta): to wymaga
     * zdarzeń z innych serwisów.
     */
    @Transactional
    public void delete(UUID userId, String mediaId) {
        Media file = owned(userId, mediaId);
        storage.delete(bucketOf(file), file.getObjectKey());
        media.delete(file);
    }

    /** Dla innych serwisów: sprawdzają właściciela i przeznaczenie, zanim przypną plik do kanału lub oferty. */
    @Transactional(readOnly = true)
    public Media internal(String mediaId) {
        return ready(mediaId).orElseThrow(MediaService::notFound);
    }

    /** Adres pliku: stały dla publicznych, krótko ważny podpisany dla prywatnych. */
    public String urlOf(Media file) {
        return file.getPurpose().isPublic()
                ? storage.publicUrl(file.getObjectKey())
                : storage.presignGet(bucketOf(file), file.getObjectKey(), PRIVATE_URL_VALIDITY);
    }

    public Optional<String> publicUrlOf(Media file) {
        return file.getPurpose().isPublic() ? Optional.of(storage.publicUrl(file.getObjectKey())) : Optional.empty();
    }

    private Media owned(UUID userId, String mediaId) {
        Media file = parse(mediaId).flatMap(media::findById).orElseThrow(MediaService::notFound);
        if (!file.isOwnedBy(userId)) {
            // Cudzy plik prywatny ma wyglądać tak samo jak nieistniejący.
            if (!file.getPurpose().isPublic()) {
                throw notFound();
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Możesz zmieniać tylko własne pliki.");
        }
        return file;
    }

    private Optional<Media> ready(String mediaId) {
        return parse(mediaId).flatMap(media::findById).filter(Media::isReady);
    }

    private String bucketOf(Media file) {
        return storage.bucket(file.getPurpose().isPublic());
    }

    private static Optional<UUID> parse(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String unsupportedType(MediaPurpose purpose) {
        return purpose.allows("application/pdf")
                ? "Ten typ pliku nie jest obsługiwany. Wybierz zdjęcie (JPG, PNG, WebP) albo PDF."
                : "Ten typ pliku nie jest obsługiwany. Wybierz zdjęcie JPG, PNG albo WebP.";
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy tego pliku.");
    }

    public record Upload(Media media, String uploadUrl, Instant expiresAt) {
    }
}
