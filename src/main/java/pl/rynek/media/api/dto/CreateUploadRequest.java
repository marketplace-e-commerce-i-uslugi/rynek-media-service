package pl.rynek.media.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pl.rynek.media.media.MediaPurpose;

/** Deklaracja pliku przed wysyłką; typ i rozmiar trafiają do podpisu URL-a. */
public record CreateUploadRequest(
        @NotNull(message = "Wybierz przeznaczenie pliku.")
        MediaPurpose purpose,
        @NotBlank(message = "Podaj nazwę pliku.")
        @Size(max = 255, message = "Nazwa pliku może mieć najwyżej 255 znaków.")
        String fileName,
        @NotBlank(message = "Podaj typ pliku.")
        String contentType,
        @Positive(message = "Plik jest pusty.")
        long sizeBytes) {
}
