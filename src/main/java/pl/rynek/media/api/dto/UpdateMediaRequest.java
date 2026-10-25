package pl.rynek.media.api.dto;

import jakarta.validation.constraints.Size;

/** Pusty tekst usuwa opis. */
public record UpdateMediaRequest(
        @Size(max = 250, message = "Opis zdjęcia może mieć najwyżej 250 znaków.")
        String altText) {
}
