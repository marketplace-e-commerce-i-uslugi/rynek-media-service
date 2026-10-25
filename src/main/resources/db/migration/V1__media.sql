-- Metadane plików; same pliki leżą w magazynie S3 pod object_key (kubełek publiczny albo prywatny wg purpose).
CREATE TABLE media (
    id           uuid PRIMARY KEY,
    -- Identyfikator wysyłki z POST /media/uploads, używany w /uploads/{uploadId}/complete.
    upload_id    uuid         NOT NULL UNIQUE,
    owner_id     uuid         NOT NULL,
    purpose      varchar(20)  NOT NULL,
    status       varchar(10)  NOT NULL,
    object_key   varchar(200) NOT NULL,
    file_name    varchar(255) NOT NULL,
    content_type varchar(100) NOT NULL,
    size_bytes   bigint       NOT NULL,
    alt_text     varchar(250),
    created_at   timestamptz  NOT NULL,
    completed_at timestamptz
);

CREATE INDEX media_owner_id_idx ON media (owner_id);
