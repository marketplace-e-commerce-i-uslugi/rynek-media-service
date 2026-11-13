package pl.rynek.media.api.dto;

/** Adres pliku dla innego serwisu; dla plików prywatnych podpisany i ważny {@code PRIVATE_URL_VALIDITY} (10 minut). */
public record InternalUrlResponse(String url) {
}
