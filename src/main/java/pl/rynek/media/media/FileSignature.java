package pl.rynek.media.media;

/**
 * Sprawdza po pierwszych bajtach, czy zawartość pliku zgadza się z deklarowanym typem. Chroni kubełek publiczny
 * przed plikiem, który tylko udaje zdjęcie.
 */
final class FileSignature {

    static final int BYTES_NEEDED = 12;

    private FileSignature() {
    }

    static boolean matches(String contentType, byte[] head) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(head, 0, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P');
            case "application/pdf" -> startsWith(head, 0, '%', 'P', 'D', 'F', '-');
            default -> false;
        };
    }

    private static boolean startsWith(byte[] head, int offset, int... expected) {
        if (head.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
