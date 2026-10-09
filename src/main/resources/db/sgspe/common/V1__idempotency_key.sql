-- Idempotency-Key de los comandos de /api/sgspe/** (decisión D3, ASM-065, api.md §3.5): el mismo contrato que la cabecera
-- nativa de wasichai en el alta genérica (WAS-ADR-0058). una fila por organización, usuario y clave con la respuesta que
-- recibió la primera petición, escrita en la transacción del comando: sin comando no hay clave, y sin clave no hay comando.
-- lo que está en curso lo dice un candado de transacción (pg_try_advisory_xact_lock), no una columna. JOB-08 borra las filas
-- vencidas. sin claves foráneas entre esquemas (database-model.md §2.3): la baja de un tenant las borra por organization_id
CREATE SCHEMA IF NOT EXISTS sgspe;

CREATE TABLE sgspe.idempotency_key (
    organization_id   uuid        NOT NULL,
    user_id           uuid        NOT NULL,
    request_key       text        NOT NULL,
    -- SHA-256 del método, la ruta y el cuerpo con sus claves ordenadas: con la misma clave, otra petición es un 422
    request_sha256    text        NOT NULL,
    response_status   integer     NOT NULL,
    -- la respuesta tal como salió la primera vez: un reenvío recibe los mismos bytes. acotada a 2 MiB en UTF-8
    -- (IdempotencyKey.MAX_RESPONSE_BYTES): un comando responde uno o dos registros
    response_body     text        NOT NULL,
    response_etag     text,
    response_location text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_key_pk PRIMARY KEY (organization_id, user_id, request_key),
    CONSTRAINT idempotency_key_length CHECK (length(request_key) BETWEEN 1 AND 128),
    CONSTRAINT idempotency_key_sha256 CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT idempotency_key_response_size CHECK (octet_length(response_body) <= 2097152)
);

-- la purga de JOB-08 recorre por antigüedad
CREATE INDEX idempotency_key_created_idx ON sgspe.idempotency_key (created_at);
