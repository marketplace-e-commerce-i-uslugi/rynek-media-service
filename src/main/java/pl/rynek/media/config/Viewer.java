package pl.rynek.media.config;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * Oglądający na endpointach publicznych: właściciel widzi swoje prywatne pliki (dokumenty, załączniki).
 * Brak, zły format lub nieważny token (także gdy identity nie odpowiada z JWKS) oznacza gościa, a nie 401.
 */
@Component
public class Viewer {

    private final JwtDecoder jwtDecoder;
    private final DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();

    public Viewer(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public Optional<UUID> of(HttpServletRequest request) {
        try {
            String token = bearer.resolve(request);
            return token == null ? Optional.empty() : Optional.of(UUID.fromString(jwtDecoder.decode(token).getSubject()));
        } catch (JwtException | OAuth2AuthenticationException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
