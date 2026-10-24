# rynek-media-service

`media-service` — zdjęcia i inne pliki

> Status: **szkielet**. Wszystkie endpointy z tabeli istnieją w kontrolerach (`src/main/java/.../api/`) i zwracają `501 Not Implemented` (RFC 7807). Ciała żądań to puste rekordy w `api/dto/`.

## Uruchomienie

```bash
./mvnw spring-boot:run   # port 8084, zmienna PORT
./mvnw verify            # testy
```

- Swagger UI: http://localhost:8084/swagger-ui.html
- Zdrowie: http://localhost:8084/actuator/health

## Zakres i endpointy

_Skopiowane z `rynek-docs/architektura/rynek-endpointy-mikroserwisy.md`. Od teraz to repozytorium jest właścicielem tej specyfikacji._

Wideo obsługuje TikTok, ale zdjęcia ofert, awatary i banery trzeba przechowywać u siebie (S3-kompatybilne, URL-e podpisane).

| Metoda | Ścieżka | Opis | Dostęp |
|---|---|---|---|
| POST | `/api/v1/media/uploads` | Wystawia podpisany URL do wysłania pliku (`purpose`: OFFER_PHOTO, AVATAR, BANNER, KYC_DOCUMENT) | user |
| POST | `/api/v1/media/uploads/{uploadId}/complete` | Potwierdzenie wysłania, start przetwarzania (miniatury, WebP) | user |
| GET | `/api/v1/media/{mediaId}` | Metadane i adresy wariantów | pub |
| PATCH | `/api/v1/media/{mediaId}` | Tekst alternatywny (`altText`) | user |
| DELETE | `/api/v1/media/{mediaId}` | Usunięcie | user |

## Dane i zależności

- **Magazyn danych:** PostgreSQL (metadane) + magazyn S3-kompatybilny
- **Zależności:** —
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 2. `media-service`, `catalog-service` — oferty ze zdjęciami (bez wideo).
