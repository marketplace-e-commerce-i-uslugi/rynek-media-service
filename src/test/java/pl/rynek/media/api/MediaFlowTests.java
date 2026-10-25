package pl.rynek.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.rynek.media.TestcontainersConfig;

/**
 * Wysyłka plików na prawdziwym PostgreSQL i SeaweedFS: plik idzie pod podpisany URL tak jak z przeglądarki.
 * Tokeny w testach to {@code user-<uuid>} (dekoder zastępuje JWKS identity).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class MediaFlowTests {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    static final byte[] PDF = "%PDF-1.7\n%test\n".getBytes();

    final HttpClient browser = HttpClient.newHttpClient();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void tokensAreUserIds() {
        given(jwtDecoder.decode(anyString())).willAnswer(call -> {
            String token = call.getArgument(0);
            if (!token.startsWith("user-")) {
                throw new BadJwtException("Nieważny token");
            }
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(token.substring(5))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        });
    }

    @Test
    void publicPhotoIsUploadedAndReadableByAnyone() throws Exception {
        UUID owner = UUID.randomUUID();
        Map<String, String> ticket = createUpload(owner, "AVATAR", "awatar.png", "image/png", PNG.length);
        assertThat(putFile(ticket.get("uploadUrl"), "image/png", PNG)).isEqualTo(200);

        String url = JsonPath.read(complete(owner, ticket.get("uploadId"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket.get("mediaId")))
                .andExpect(jsonPath("$.purpose").value("AVATAR"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.variants").value(anEmptyMap()))
                .andReturn().getResponse().getContentAsString(), "$.url");
        assertThat(url).endsWith("/rynek-media-public/avatar/" + ticket.get("mediaId"));
        HttpResponse<byte[]> file = fetch(url);
        assertThat(file.statusCode()).isEqualTo(200);
        assertThat(file.body()).isEqualTo(PNG);

        // Ponowne potwierdzenie nic nie psuje.
        complete(owner, ticket.get("uploadId")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/media/" + ticket.get("mediaId")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(url));
        mvc.perform(get("/internal/media/" + ticket.get("mediaId")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(owner.toString()))
                .andExpect(jsonPath("$.purpose").value("AVATAR"))
                .andExpect(jsonPath("$.url").value(url));
    }

    @Test
    void privateDocumentIsVisibleOnlyToOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        Map<String, String> ticket = createUpload(owner, "KYC_DOCUMENT", "krs.pdf", "application/pdf", PDF.length);
        assertThat(putFile(ticket.get("uploadUrl"), "application/pdf", PDF)).isEqualTo(200);
        complete(owner, ticket.get("uploadId")).andExpect(status().isOk());
        String mediaId = ticket.get("mediaId");

        mvc.perform(get("/api/v1/media/" + mediaId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/media/" + mediaId).header(HttpHeaders.AUTHORIZATION, "Bearer user-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        String url = JsonPath.read(mvc.perform(get("/api/v1/media/" + mediaId).header(HttpHeaders.AUTHORIZATION, "Bearer user-" + owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.url");
        assertThat(url).contains("/rynek-media-private/kyc_document/").contains("X-Amz-Signature=");
        assertThat(fetch(url).body()).isEqualTo(PDF);
        // Bez podpisu kubełek prywatny nie oddaje pliku.
        assertThat(fetch(url.substring(0, url.indexOf('?'))).statusCode()).isEqualTo(403);
        mvc.perform(get("/internal/media/" + mediaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(nullValue()));
    }

    @Test
    void declarationIsValidated() throws Exception {
        UUID owner = UUID.randomUUID();
        createUploadRequest(owner, "AVATAR", "film.gif", "image/gif", 100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Ten typ pliku nie jest obsługiwany. Wybierz zdjęcie JPG, PNG albo WebP."));
        createUploadRequest(owner, "AVATAR", "dokument.pdf", "application/pdf", 100).andExpect(status().isBadRequest());
        createUploadRequest(owner, "AVATAR", "duzy.png", "image/png", 6L * 1024 * 1024)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Plik jest za duży. Maksymalny rozmiar to 5 MB."));
        createUploadRequest(owner, "AVATAR", "pusty.png", "image/png", 0).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/media/uploads").header(HttpHeaders.AUTHORIZATION, "Bearer user-" + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"FILM\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/media/uploads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"AVATAR\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void completeChecksThatTheFileArrivedAndMatches() throws Exception {
        UUID owner = UUID.randomUUID();
        Map<String, String> ticket = createUpload(owner, "OFFER_PHOTO", "zdjecie.png", "image/png", PNG.length);
        complete(owner, ticket.get("uploadId"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Plik nie dotarł na serwer. Wyślij go ponownie."));
        complete(UUID.randomUUID(), ticket.get("uploadId")).andExpect(status().isNotFound());
        complete(owner, "nie-uuid").andExpect(status().isNotFound());
        // Niepotwierdzony plik nie jest jeszcze widoczny.
        mvc.perform(get("/api/v1/media/" + ticket.get("mediaId"))).andExpect(status().isNotFound());

        // Podpis obejmuje typ: inny Content-Type niż w deklaracji nie przejdzie.
        assertThat(putFile(ticket.get("uploadUrl"), "image/jpeg", PNG)).isEqualTo(403);

        byte[] notAnImage = "<html>hej</html>".getBytes();
        Map<String, String> fake = createUpload(owner, "OFFER_PHOTO", "zdjecie.png", "image/png", notAnImage.length);
        assertThat(putFile(fake.get("uploadUrl"), "image/png", notAnImage)).isEqualTo(200);
        complete(owner, fake.get("uploadId"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Zawartość pliku nie zgadza się z jego typem. Wybierz inny plik."));
        complete(owner, fake.get("uploadId")).andExpect(status().isNotFound());
    }

    @Test
    void ownerDescribesAndDeletesFile() throws Exception {
        UUID owner = UUID.randomUUID();
        Map<String, String> ticket = createUpload(owner, "OFFER_PHOTO", "rower.png", "image/png", PNG.length);
        putFile(ticket.get("uploadUrl"), "image/png", PNG);
        String url = JsonPath.read(complete(owner, ticket.get("uploadId")).andReturn().getResponse().getContentAsString(), "$.url");
        String mediaId = ticket.get("mediaId");

        updateAlt(UUID.randomUUID(), mediaId, "Cudzy opis")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Możesz zmieniać tylko własne pliki."));
        updateAlt(owner, mediaId, "  Rower górski z przodu  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.altText").value("Rower górski z przodu"));
        updateAlt(owner, mediaId, "x".repeat(251)).andExpect(status().isBadRequest());
        updateAlt(owner, mediaId, "").andExpect(jsonPath("$.altText").value(nullValue()));

        mvc.perform(delete("/api/v1/media/" + mediaId).header(HttpHeaders.AUTHORIZATION, "Bearer user-" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/media/" + mediaId).header(HttpHeaders.AUTHORIZATION, "Bearer user-" + owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/media/" + mediaId)).andExpect(status().isNotFound());
        assertThat(fetch(url).statusCode()).isEqualTo(404);
    }

    private Map<String, String> createUpload(UUID owner, String purpose, String fileName, String contentType, long size)
            throws Exception {
        String body = createUploadRequest(owner, purpose, fileName, contentType, size)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").value(startsWith("http")))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return Map.of("uploadId", JsonPath.read(body, "$.uploadId"), "mediaId", JsonPath.read(body, "$.mediaId"),
                "uploadUrl", JsonPath.read(body, "$.uploadUrl"));
    }

    private ResultActions createUploadRequest(UUID owner, String purpose, String fileName, String contentType, long size)
            throws Exception {
        return mvc.perform(post("/api/v1/media/uploads").header(HttpHeaders.AUTHORIZATION, "Bearer user-" + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"" + purpose + "\",\"fileName\":\"" + fileName + "\",\"contentType\":\"" + contentType
                        + "\",\"sizeBytes\":" + size + "}"));
    }

    private ResultActions complete(UUID owner, String uploadId) throws Exception {
        return mvc.perform(post("/api/v1/media/uploads/" + uploadId + "/complete").header(HttpHeaders.AUTHORIZATION, "Bearer user-" + owner));
    }

    private ResultActions updateAlt(UUID owner, String mediaId, String altText) throws Exception {
        return mvc.perform(patch("/api/v1/media/" + mediaId).header(HttpHeaders.AUTHORIZATION, "Bearer user-" + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"altText\":\"" + altText + "\"}"));
    }

    /** Tak jak przeglądarka: PUT pod podpisany URL z typem pliku w nagłówku. */
    private int putFile(String uploadUrl, String contentType, byte[] content) throws IOException, InterruptedException {
        return browser.send(HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpResponse<byte[]> fetch(String url) throws IOException, InterruptedException {
        return browser.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
