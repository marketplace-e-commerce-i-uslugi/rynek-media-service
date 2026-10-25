package pl.rynek.media.storage;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Magazyn S3-kompatybilny. {@code endpoint} służy serwisowi, {@code publicEndpoint} trafia do adresów dla przeglądarki
 * (podpisane URL-e i publiczne pliki), bo podpis obejmuje nazwę hosta. Lokalnie oba wskazują SeaweedFS z rynek-infra.
 */
@ConfigurationProperties("rynek.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String region,
        String accessKey,
        String secretKey,
        String publicBucket,
        String privateBucket,
        List<String> allowedOrigins) {
}
