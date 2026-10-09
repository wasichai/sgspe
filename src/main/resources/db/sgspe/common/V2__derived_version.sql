-- los saltos de versión de las escrituras de solo derivados (ADR-0027, ASM-091, api.md §3.6): cada una se condiciona al
-- updated_at que leyó y deja aquí de cuál a cuál pasó el registro. el If-Match de la API genérica y de los comandos acepta
-- la versión leída y las que la siguieron solo por estos saltos, sin mirar la hora de la auditoría. JOB-08 borra los
-- vencidos (sgspe.concurrency.derived-versions-retention). sin claves foráneas entre esquemas (database-model.md §2.3): la
-- baja de un tenant los borra por organization_id
CREATE TABLE sgspe.derived_version (
    organization_id uuid        NOT NULL,
    object_name     text        NOT NULL,
    record_id       uuid        NOT NULL,
    -- el updated_at que leyó la escritura de derivados, al que se condicionó su UPDATE
    from_updated_at timestamptz NOT NULL,
    -- el updated_at que produjo (clock_timestamp() de su sentencia, WAS-ADR-0051)
    to_updated_at   timestamptz NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    -- de una versión sale a lo sumo un salto: solo una escritura condicionada a ella puede ganar
    CONSTRAINT derived_version_pk PRIMARY KEY (organization_id, object_name, record_id, from_updated_at)
);

-- la purga de JOB-08 recorre por antigüedad
CREATE INDEX derived_version_created_idx ON sgspe.derived_version (created_at);
