package pl.rynek.media.media;

import java.util.Set;

/**
 * Przeznaczenie pliku decyduje o dozwolonych typach, limicie rozmiaru i o tym, czy plik jest publiczny.
 * Publiczne (zdjęcia ofert, awatary, banery) mają stały adres w kubełku z anonimowym odczytem; prywatne
 * (dokumenty weryfikacji, załączniki czatu) widzi tylko właściciel, przez krótko ważny podpisany URL.
 */
public enum MediaPurpose {

    OFFER_PHOTO(true, Types.IMAGES, 10),
    AVATAR(true, Types.IMAGES, 5),
    BANNER(true, Types.IMAGES, 10),
    KYC_DOCUMENT(false, Types.IMAGES_AND_PDF, 10),
    CHAT_ATTACHMENT(false, Types.IMAGES_AND_PDF, 10);

    private final boolean publicRead;
    private final Set<String> contentTypes;
    private final long maxBytes;

    MediaPurpose(boolean publicRead, Set<String> contentTypes, int maxMegabytes) {
        this.publicRead = publicRead;
        this.contentTypes = contentTypes;
        this.maxBytes = maxMegabytes * 1024L * 1024L;
    }

    public boolean isPublic() {
        return publicRead;
    }

    public boolean allows(String contentType) {
        return contentTypes.contains(contentType);
    }

    public long maxBytes() {
        return maxBytes;
    }

    /** Stałe enuma inicjują się przed jego polami statycznymi, więc zbiory typów leżą w osobnej klasie. */
    private static final class Types {
        static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");
        static final Set<String> IMAGES_AND_PDF = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    }
}
