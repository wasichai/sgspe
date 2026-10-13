-- los fragmentos de texto de las evidencias para la recuperación con citas de AI-06 (RAG, etapa 1 de ADR-0005;
-- domain-model.md §4.10 y §6.18, database-model.md §2.3 y §20, ai-architecture.md §13.2). JOB-07 los escribe como la
-- plataforma y sgspe.ai los lee con los filtros de permisos; nadie más toca esta tabla. project_id y classification se
-- copian de la evidencia para filtrar. sin columna embedding: pgvector llega solo en la etapa 2, con una medición y un ADR
-- nuevo (CON-035). sin claves foráneas entre esquemas (database-model.md §2.3): project_id y evidence_id son referencias
-- lógicas que valida sgspe.ai, y la baja de un tenant borra sus filas por organization_id (database-model.md §24)
CREATE SCHEMA IF NOT EXISTS sgspe;

CREATE TABLE sgspe.knowledge_chunk (
    id              uuid        NOT NULL DEFAULT gen_random_uuid(),
    organization_id uuid        NOT NULL,
    -- vacío si la evidencia es del tenant
    project_id      uuid,
    evidence_id     uuid        NOT NULL,
    -- la posición del fragmento en el documento
    chunk_index     integer     NOT NULL,
    -- el valor del catálogo «Clasificación de evidencia», copiado de la evidencia
    classification  text        NOT NULL,
    content         text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT knowledge_chunk_pk PRIMARY KEY (id),
    -- la reindexación de una evidencia reemplaza sus fragmentos por posición: idempotente
    CONSTRAINT knowledge_chunk_position_key UNIQUE (organization_id, evidence_id, chunk_index)
);

-- la recuperación filtra por tenant, proyecto y clasificación antes de ordenar por relevancia
CREATE INDEX knowledge_chunk_scope_idx ON sgspe.knowledge_chunk (organization_id, project_id, classification);
-- el texto completo en español: websearch_to_tsquery('spanish', :q) y ts_rank_cd sobre esta misma expresión
CREATE INDEX knowledge_chunk_content_fts_idx ON sgspe.knowledge_chunk USING gin (to_tsvector('spanish', content));
