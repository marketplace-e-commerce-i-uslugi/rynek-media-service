# rynek-media-service

`media-service` — zdjęcia i inne pliki

> Status: **wszystkie endpointy działają**: wysyłka pod podpisany URL, potwierdzenie ze sprawdzeniem pliku, metadane, tekst alternatywny, usunięcie i `GET /internal/media/{id}` dla innych serwisów. Przetwarzanie (miniatury, WebP) jeszcze nie istnieje, więc `variants` jest puste.

## Uruchomienie

Wymaga PostgreSQL (baza `media`) i SeaweedFS z `rynek-infra/local` oraz identity-service (JWKS):

```bash
docker compose -f ../rynek-infra/local/docker-compose.yml up -d
./mvnw spring-boot:run   # port 8084, zmienna PORT
./mvnw verify            # testy; Testcontainers uruchamia własny PostgreSQL i SeaweedFS, wymaga Dockera
```

| Zmienna | Domyślnie | Znaczenie |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/media`, `media`, `media` | Baza serwisu (metadane) |
| `S3_ENDPOINT` | `http://localhost:8333` | Magazyn S3 widziany przez serwis |
| `S3_PUBLIC_ENDPOINT` | `http://localhost:8333` | Magazyn S3 widziany przez przeglądarkę (podpisane URL-e, adresy plików) |
| `S3_REGION` | `us-east-1` | Region w podpisie |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | `rynek-media`, `rynek-media-local` | Klucze z `rynek-infra/local/seaweedfs/s3.json` |
| `S3_PUBLIC_BUCKET`, `S3_PRIVATE_BUCKET` | `rynek-media-public`, `rynek-media-private` | Kubełki |
| `S3_ALLOWED_ORIGINS` | `http://localhost:4200` | Origin frontendu w regułach CORS kubełków |
| `IDENTITY_SERVICE_URL` | `http://localhost:8081` | identity-service: JWKS |
| `JWT_ISSUER` | `rynek-identity` | Oczekiwany claim `iss` |

Schemat zakłada Flyway (`src/main/resources/db/migration`). Hibernate tylko sprawdza zgodność encji (`ddl-auto: validate`).

- Swagger UI: http://localhost:8084/swagger-ui.html
- Zdrowie: http://localhost:8084/actuator/health

## Zakres i endpointy

_Skopiowane z `rynek-docs/architektura/rynek-endpointy-mikroserwisy.md`. Od teraz to repozytorium jest właścicielem tej specyfikacji._

Wideo obsługuje TikTok, ale zdjęcia ofert, awatary i banery trzeba przechowywać u siebie (S3-kompatybilne, URL-e podpisane).

| Metoda | Ścieżka | Opis | Dostęp |
|---|---|---|---|
| POST | `/api/v1/media/uploads` | Wystawia podpisany URL do wysłania pliku (`purpose`: OFFER_PHOTO, AVATAR, BANNER, KYC_DOCUMENT, CHAT_ATTACHMENT) | user |
| POST | `/api/v1/media/uploads/{uploadId}/complete` | Potwierdzenie wysłania, start przetwarzania (miniatury, WebP) | user |
| GET | `/api/v1/media/{mediaId}` | Metadane i adresy wariantów | pub |
| PATCH | `/api/v1/media/{mediaId}` | Tekst alternatywny (`altText`) | user |
| DELETE | `/api/v1/media/{mediaId}` | Usunięcie | user |
| GET | `/internal/media/{mediaId}` | Właściciel, przeznaczenie i adres pliku (channel, catalog) | int |

## Zachowanie

- **Wysyłka w trzech krokach:**
  1. `POST /uploads` z `{purpose, fileName, contentType, sizeBytes}` zwraca `{uploadId, mediaId, uploadUrl, expiresAt}`. URL jest ważny 15 minut, a jego podpis obejmuje typ i rozmiar pliku.
  2. Przeglądarka wysyła plik metodą `PUT` pod `uploadUrl` z nagłówkiem `Content-Type` z deklaracji, bez nagłówka `Authorization`.
  3. `POST /uploads/{uploadId}/complete` sprawdza, czy plik dotarł (inaczej 409), czy rozmiar się zgadza i czy pierwsze bajty pasują do typu. Plik niezgodny z deklaracją jest usuwany (400). Ponowne potwierdzenie gotowego pliku zwraca go bez zmian.
- **Przeznaczenia:**

  | `purpose` | Typy | Limit | Dostęp |
  |---|---|---|---|
  | `OFFER_PHOTO`, `BANNER` | JPG, PNG, WebP | 10 MB | publiczny, stały URL |
  | `AVATAR` | JPG, PNG, WebP | 5 MB | publiczny, stały URL |
  | `KYC_DOCUMENT`, `CHAT_ATTACHMENT` | JPG, PNG, WebP, PDF | 10 MB | tylko właściciel, podpisany URL ważny 10 minut |

  `CHAT_ATTACHMENT` dodano, bo czat we frontendzie wysyła załączniki. Gdy powstanie chat-service, załącznik będą mogli oglądać także uczestnicy rozmowy.
- **Widoczność:** niepotwierdzony plik nie istnieje dla nikogo (404). Prywatny plik oglądany przez kogoś innego niż właściciel też daje 404. Zmiana opisu i usunięcie cudzego pliku publicznego dają 403.
- **Usunięcie** kasuje plik z magazynu i metadane. Serwis nie sprawdza, czy plik jest jeszcze używany (awatar, oferta), bo wymaga to zdarzeń z innych serwisów. Nie ma też jeszcze sprzątania wysyłek, których nikt nie potwierdził.
- **`/internal/media/{id}`:** `{id, ownerId, purpose, contentType, url}` gotowego pliku. `url` jest pusty dla plików prywatnych. channel i catalog sprawdzają nim właściciela i przeznaczenie, zanim przypną plik do kanału lub oferty.

## Dane i zależności

- **Magazyn danych:** PostgreSQL (metadane) + magazyn S3-kompatybilny (lokalnie SeaweedFS). Kubełek publiczny ma anonimowy odczyt ustawiony w konfiguracji magazynu. Kubełki i ich CORS serwis zakłada sam.
- **Zależności:** identity-service (JWKS)
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 2. `media-service`, `catalog-service` — oferty ze zdjęciami (bez wideo).
