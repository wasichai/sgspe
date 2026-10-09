# ADR-0027: `If-Match` con saltos exactos de derivados y la comparación de wasichai

## Estado

Aceptado · 2026-10-09 · corrige una actualización perdida intermitente de la API genérica (fallo E del diagnóstico de la
CI 1 de la fase 3.2) y completa ADR-0025 en lo que dice de la concurrencia; la idempotencia no cambia

## Contexto

- El portal manda en `If-Match` el `updatedAt` que leyó (ADR-0025, ASM-035, [api.md §3.6](../api.md)). SGSPE tolera que,
  después de esa versión, solo se hayan escrito campos derivados: el área oficial que recalcula `AreaListener`, los
  conteos de asistentes, los que restaura `DerivedFieldsKeeper` y los de JOB-02 y JOB-03. Sin esa tolerancia, el
  `updatedAt` de la respuesta de un alta ya no valdría, porque el listener vuelve a escribir el registro en la misma
  petición.
- Desde wasichai 0.5.0 [WAS-ADR-0051] cada escritura toma `updated_at = clock_timestamp()` y la plataforma compara
  `If-Match` en el propio `UPDATE`, `DELETE` o transición. Acepta una **lista** de etiquetas: la escritura pasa si
  `updated_at` es una de ellas. No hay contador de versión (`version` sigue siendo un nombre reservado sin columna), y
  la fila de `audit_log` no guarda el `updated_at` anterior ni el nuevo: solo `occurred_at`, que es el `now()` del
  inicio de la transacción, y el diff de atributos, que no ve geometrías.
- Al pasar a 0.6.0, `IfMatchFilter` quitó la cabecera antes de la plataforma, porque con igualdad estricta esa
  comparación respondería `412` en el caso de los derivados. `Versions` decidía con el historial: tomaba como versión
  leída la entrada de auditoría **más cercana en el tiempo** a lo que mandó el cliente y toleraba lo posterior si eran
  solo derivados o vínculos.
- Con carga, la escritura de otra persona (B) puede caer más cerca que la del listener. `ConcurrencyApiTest` («un cambio
  de solo la geometría de otro es 412») lo mostró en dos runs: la entrada de B pasó por la versión leída, lo posterior
  eran solo derivados y el `PUT` de A respondió `200` y revirtió la geometría de B. El defecto existe desde 1c0f719
  (F2).
- Además, la guarda comparaba y la escritura venía después, sin transacción: quedaba la ventana de ADR-0025 entre las
  dos.

## Opciones consideradas

### Ajustar la ventana de tiempo

- A favor: un cambio de pocas líneas.
- En contra: entre la entrada de una escritura y la siguiente hay unos 50 a 60 ms con carga, del mismo orden que lo que
  tarda el listener. Cualquier umbral falla hacia un lado: deja pasar actualizaciones perdidas o da `412` sin conflicto.

### La comparación estricta de wasichai, sin tolerancia

- A favor: nada propio.
- En contra: el portal tendría que volver a leer después de cada escritura de un listener, y un trabajo de fondo
  (JOB-02) invalidaría el formulario de quien está editando sin que haya un conflicto real.

### El motivo de la auditoría con el `updatedAt` anterior

- A favor: sin tabla nueva.
- En contra: sgspe no escribe `audit_log` (ADR-0011) y no puede poner el `updatedAt` que produce la escritura en su
  propia fila, porque lo conoce después; habría que leer y analizar textos.

### Saltos exactos en una tabla fija, y la lista a la comparación de wasichai (elegida)

- A favor: exacto, sin horas; reusa la comparación atómica de la plataforma, así que además cierra la ventana entre la
  guarda y la escritura de la API genérica.
- En contra: una tabla fija más (`sgspe.derived_version`), con purga, y una consulta indexada por escritura con
  `If-Match`.

## Decisión

- **Una escritura de solo derivados se condiciona y deja su salto.** `Records.patch` reconoce la escritura cuyos campos
  son todos `"derived"` en `model.json` y no trae geometrías (`DerivedWrite`). La escribe con la mezcla de wasichai,
  `RecordService.patch(…, RecordRequest(<los derivados>), motivo, expectedUpdatedAt = <updatedAt leído>)`, que escribe
  solo esas columnas, con las mismas guardas que un `PUT`, y compara la versión en el propio `UPDATE`:
  - Si gana, guarda en `sgspe.derived_version` el salto (`from_updated_at` = lo leído, `to_updated_at` = lo escrito).
    Entre los dos no se escribió nada más.
  - Si otra escritura ganó entre la lectura y el `UPDATE`, wasichai no escribe nada y responde su `412` de `If-Match`
    para ese registro; solo ese `412` se reintenta, no el de un listener de la propia escritura. `Records.patch` vuelve
    a leer y lo intenta otra vez, sin pisar a la otra.
  - Al reintentar aplica los mismos valores, que quien llama calculó antes: la API de `Records.patch` recibe valores, no
    cómo calcularlos. Si la otra escritura cambió aquello de lo que dependen (una geometría, un participante), su propio
    listener los recalcula después, porque el del área y el de los conteos toman el mismo candado `<objeto>:<id>`, y si
    no, JOB-02 los corrige; es el mismo «gana el último» de antes (riesgo 6 de `architecture.md`).
  - Tras 3 pérdidas escribe sin condición y no deja salto, así quien tenga una versión anterior recibe `412` (falla
    cerrado). Esa caída es la misma mezcla sin `expectedUpdatedAt`, que escribe solo los derivados: lo que otra
    persona escriba entre su lectura y su `UPDATE` en los demás campos se queda.
  - Todas las escrituras de derivados de F2 y F3 pasan por ahí: los listeners, JOB-02, JOB-03 y los comandos.
    `DerivedFieldsGuard` rechaza un derivado que llegue por otra puerta (la API genérica, `/admin`, una automatización).
  - El salto se inserta en la transacción de quien escribe, si la hay, y se revierte con ella. Fuera de una, se inserta
    justo después del `UPDATE` confirmado, y en ese intervalo una comparación no lo ve y responde `412`: nunca deja
    pasar una actualización perdida. Un salto repetido, que solo daría un reloj que retrocede, se descarta (`ON CONFLICT
    DO NOTHING`) y se registra en el log: no aborta la transacción, y el `to` nuevo queda sin camino.
- **Las versiones aceptadas son una cadena.** Desde la versión leída se siguen los saltos: de una versión sale a lo sumo
  uno, porque la clave primaria y el `UPDATE` condicionado lo aseguran. La cadena llega a 500 saltos como mucho.
  - Recorrerla hacia adelante desde lo leído equivale a recorrerla hacia atrás desde lo vigente.
  - Una escritura que no es un salto la corta, aunque caiga a un milisegundo: la de otra persona, un cambio de
    geometría, una transición.
  - Un vínculo no mueve `updated_at` [WAS-ADR-0051], así que no hace falta tolerarlo.
- **API genérica.** `IfMatchFilter` corre después de la cadena de seguridad. Lee la cadena con la organización de quien
  llama, la deja en el contexto para `IfMatchGuard` y cambia `If-Match` por esa lista de etiquetas fuertes.
  - La guarda responde antes que las demás el `412` de SGSPE, con el valor actual en `errors[]`.
  - wasichai vuelve a comparar en el propio `UPDATE`, `DELETE`, transición o subida de archivo: una escritura que cae
    entre la guarda y ese `UPDATE` es el `412` de la plataforma (`errors[0].field = "If-Match"`), no una actualización
    perdida.
  - Un valor que no es un instante, o `*`, sale de la petición como antes: `400` de la guarda, o sin comparación.
  - Si no puede leer la cadena (la base no responde), el filtro responde el `500` de wasichai en problem+json
    (`GlobalExceptionHandler.handleUnexpected`), con los codificadores de la aplicación, y la petición no sigue.
- **Comandos con `IM`.** `Versions.check` acepta lo vigente si está en la cadena; si no, `412` como siempre. Siguen
  comparando dentro de su transacción y con su candado.
- **Retención.** Un salto solo sirve a quien leyó la versión de la que sale. Se guarda 7 días
  (`sgspe.concurrency.derived-versions-retention`), muy por encima de una sesión (el JWT dura 8 horas) y de la clave de
  idempotencia (24 horas). Un salto vencido no cuenta aunque la purga no haya pasado, y JOB-08 lo borra cada día
  (ASM-091).

## Consecuencias

### Positivas

- El fallo E desaparece: la tolerancia de los derivados ya no depende de la carga ni del orden de las entradas de
  auditoría.
- La API genérica gana la comparación atómica de UPS-04 sin perder la tolerancia: dos personas que escriben a la vez ya
  no se pisan, tampoco entre la guarda y el `UPDATE`.
- Una escritura de derivados ya no revierte los campos que otra escribió entre su lectura y su `UPDATE`, que era la
  ventana de `Records.patch`: con condición pierde y reintenta, y sin condición (la caída) escribe solo los derivados.
- `Versions` deja de leer `audit_log` y desaparece el tope de 500 entradas de historial de la API §3.6.

### Negativas y riesgos

- Una versión leída hace más de 7 días, seguida de derivados, responde `412` y el portal recarga (falla cerrado).
- Una escritura de derivados que pierde tres veces seguidas se escribe sin salto, y quien leyó antes recibe `412`.
- Un reintento reaplica un valor calculado antes de la escritura que ganó; el listener de esa escritura o JOB-02 lo
  recalculan (gana el último, como antes).
- En la carrera entre la guarda y el `UPDATE`, el `412` lleva la forma de wasichai (`If-Match`), no el valor actual.
- Un comando con `IM` sigue sin pasar su versión a la escritura: entre su comparación y su `Records.patch` puede caer
  una escritura de la API genérica, que no toma su candado. Es la ventana de ADR-0025 que queda abierta para los
  comandos.
- La tabla crece con cada escritura de derivados: una fila por salto, purgada a los 7 días.

### Seguimiento

- Que los comandos pasen a `Records.patch` la cadena aceptada para cerrar también su ventana.
- La baja de un tenant borra sus filas de `derived_version` por `organization_id` (database-model.md §24).
- Pruebas:
  - Unitarias: `DerivedVersionsTest` (la cadena y la escritura condicionada, con su caída) e `IfMatchTest` (`Versions`
    y el filtro, también su `500`).
  - De integración: `DerivedVersionsApiTest`, que fuerza en orden:
    - el intercalado del fallo E;
    - la escritura de otro entre la guarda y el `UPDATE`;
    - la caída sin condición con otro que escribe antes de cada `UPDATE`;
    - una transición y un borrado con la lista;
    - una transacción que se revierte sin dejar salto;
    - la retención.
  - También `ConcurrencyApiTest` sin cambios, `AreaApiTest`, `AttendanceCountsApiTest` y los comandos con `IM`.

## Referencias

- Requisitos: RNF-INT-003; CON-047; UPS-04; ASM-035, ASM-091.
- Documentos: [api.md §3.6](../api.md), [database-model.md §22 y §26](../database-model.md) (`derived_version`),
  [platform-fit.md §4.3](../platform-fit.md), [workflows.md](../workflows.md) (JOB-08).
- ADR de SGSPE: ADR-0011, ADR-0025.
- WAS-ADR-0038 (RecordService joins the caller's transaction), WAS-ADR-0040 (Append-only objects and a pre-write guard
  SPI), WAS-ADR-0051 (Records answer an ETag, honour If-Match in the write itself, and take a partial PATCH).
