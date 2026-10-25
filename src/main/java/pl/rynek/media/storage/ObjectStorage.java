package pl.rynek.media.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Dostęp do magazynu plików. Kubełki (publiczny i prywatny) i ich reguły CORS serwis zakłada sam: przy starcie,
 * a jeśli magazyn wtedy nie odpowiadał, przy pierwszym użyciu. Anonimowy odczyt kubełka publicznego ustawia
 * konfiguracja magazynu (lokalnie {@code rynek-infra/local/seaweedfs/s3.json}).
 */
@Component
public class ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

    private final StorageProperties props;
    private final S3Client s3;
    private final S3Presigner presigner;
    private volatile boolean bucketsReady;

    public ObjectStorage(StorageProperties props) {
        this.props = props;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        // Adresy w stylu ścieżki (host/kubełek/klucz): lokalny magazyn nie ma DNS dla kubełków.
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(props.publicEndpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    void prepareOnStartup() {
        try {
            ensureBuckets();
        } catch (ResponseStatusException e) {
            log.warn("Magazyn plików {} nie odpowiada; kubełki zostaną założone przy pierwszym użyciu.", props.endpoint());
        }
    }

    public String bucket(boolean publicRead) {
        return publicRead ? props.publicBucket() : props.privateBucket();
    }

    /** Stały adres pliku w kubełku publicznym. */
    public String publicUrl(String key) {
        return props.publicEndpoint() + "/" + props.publicBucket() + "/" + key;
    }

    /** URL do wysłania pliku metodą PUT; podpis obejmuje typ i rozmiar, więc przeglądarka nie wyśle innego pliku. */
    public String presignPut(String bucket, String key, String contentType, long sizeBytes, Duration validFor) {
        ensureBuckets();
        return presigner.presignPutObject(request -> request
                        .signatureDuration(validFor)
                        .putObjectRequest(put -> put.bucket(bucket).key(key).contentType(contentType).contentLength(sizeBytes)))
                .url().toString();
    }

    public String presignGet(String bucket, String key, Duration validFor) {
        return presigner.presignGetObject(request -> request
                        .signatureDuration(validFor)
                        .getObjectRequest(get -> get.bucket(bucket).key(key)))
                .url().toString();
    }

    public Optional<StoredObject> head(String bucket, String key) {
        return call(() -> {
            try {
                HeadObjectResponse head = s3.headObject(request -> request.bucket(bucket).key(key));
                return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
            } catch (NoSuchKeyException e) {
                return Optional.empty();
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    return Optional.empty();
                }
                throw e;
            }
        });
    }

    /** Pierwsze bajty pliku, do sprawdzenia, czy zawartość zgadza się z deklarowanym typem. */
    public byte[] readHead(String bucket, String key, int bytes) {
        return call(() -> s3.getObjectAsBytes(request -> request.bucket(bucket).key(key).range("bytes=0-" + (bytes - 1)))
                .asByteArray());
    }

    public void delete(String bucket, String key) {
        call(() -> s3.deleteObject(request -> request.bucket(bucket).key(key)));
    }

    private void ensureBuckets() {
        if (bucketsReady) {
            return;
        }
        call(() -> {
            // Przeglądarka wysyła pliki prosto do magazynu (PUT) i czyta prywatne przez podpisany URL (GET).
            CORSRule rule = CORSRule.builder()
                    .allowedOrigins(props.allowedOrigins())
                    .allowedMethods("PUT", "GET")
                    .allowedHeaders("*")
                    .maxAgeSeconds(3600)
                    .build();
            for (String bucket : new String[]{props.publicBucket(), props.privateBucket()}) {
                try {
                    s3.headBucket(request -> request.bucket(bucket));
                } catch (NoSuchBucketException e) {
                    s3.createBucket(request -> request.bucket(bucket));
                    log.info("Założono kubełek {}", bucket);
                }
                s3.putBucketCors(request -> request.bucket(bucket).corsConfiguration(cors -> cors.corsRules(rule)));
            }
            return null;
        });
        bucketsReady = true;
    }

    private static <T> T call(StorageCall<T> action) {
        try {
            return action.run();
        } catch (SdkClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Magazyn plików jest niedostępny. Spróbuj ponownie za chwilę.");
        }
    }

    @FunctionalInterface
    private interface StorageCall<T> {
        T run();
    }

    public record StoredObject(long sizeBytes, String contentType) {
    }
}
