package pl.rynek.media.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import pl.rynek.media.common.ProblemJson;

/**
 * Serwis sam sprawdza JWT z identity-service (klucze z JWKS), więc działa poprawnie także przy {@code JWT_ENFORCE=false}
 * w gateway. Publiczne są endpointy {@code pub} z README i {@code /internal/**} (gateway ich nie wystawia).
 * Na publicznych token nie jest wymagany; jeśli jest ważny, {@link Viewer} odczytuje z niego oglądającego.
 */
@Configuration
public class SecurityConfig {

    /** Endpointy {@code pub} z README (metadane pliku) i wewnętrzne. */
    static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/media/{mediaId}"),
            PathPatternRequestMatcher.withDefaults().matcher("/internal/**"));

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Bezstanowe API z tokenem Bearer: bez sesji i ciasteczek, więc bez CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(ignoreTokenOnPublicEndpoints())
                        .jwt(jwt -> {
                        })
                        .authenticationEntryPoint(unauthorized()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorized())
                        .accessDeniedHandler(forbidden()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${rynek.jwt.jwk-set-uri}") String jwkSetUri, @Value("${rynek.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Wygasły token, który frontend nadal dołącza, nie może dawać 401 przy podglądzie publicznego pliku. */
    private static BearerTokenResolver ignoreTokenOnPublicEndpoints() {
        DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
        return request -> PUBLIC_ENDPOINTS.matches(request) ? null : bearer.resolve(request);
    }

    private static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            ProblemJson.write(request, response, HttpStatus.UNAUTHORIZED, "Zaloguj się, aby kontynuować.");
        };
    }

    private static AccessDeniedHandler forbidden() {
        return (request, response, ex) ->
                ProblemJson.write(request, response, HttpStatus.FORBIDDEN, "Nie masz uprawnień do tej operacji.");
    }
}
