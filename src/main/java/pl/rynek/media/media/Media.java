package pl.rynek.media.media;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "media")
public class Media {

    @Id
    private UUID id;

    @Column(name = "upload_id")
    private UUID uploadId;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    private MediaPurpose purpose;

    @Enumerated(EnumType.STRING)
    private MediaStatus status;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private long sizeBytes;

    @Column(name = "alt_text")
    private String altText;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Media() {
    }

    public Media(UUID ownerId, MediaPurpose purpose, String fileName, String contentType, long sizeBytes, Instant now) {
        this.id = UUID.randomUUID();
        this.uploadId = UUID.randomUUID();
        this.ownerId = ownerId;
        this.purpose = purpose;
        this.status = MediaStatus.PENDING;
        this.objectKey = purpose.name().toLowerCase() + "/" + id;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = now;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public boolean isReady() {
        return status == MediaStatus.READY;
    }

    public void markReady(Instant now) {
        this.status = MediaStatus.READY;
        this.completedAt = now;
    }

    public void describe(String altText) {
        this.altText = altText;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUploadId() {
        return uploadId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public MediaPurpose getPurpose() {
        return purpose;
    }

    public MediaStatus getStatus() {
        return status;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getAltText() {
        return altText;
    }
}
