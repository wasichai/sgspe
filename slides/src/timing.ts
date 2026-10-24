// los tiempos de las láminas (deliverables.md §16.2), en un .ts para que el servidor los importe sin compilar JSX: cada lámina
// anima SLIDE_FRAMES fotogramas a FPS por segundo y su último fotograma es el estado completo, el que va al PDF. en el MP4,
// cada lámina queda quieta VIDEO_HOLD_FRAMES más en ese estado para que se alcance a leer (3 s + 5 s); SGSPE comprueba la
// duración del video con los mismos números (DeckVideoChecks)
export const SLIDE_FRAMES = 90
export const FPS = 30
export const VIDEO_HOLD_FRAMES = 150

// los fotogramas del MP4 de una presentación de `slides` láminas
export const videoFrames = (slides: number) => slides * (SLIDE_FRAMES + VIDEO_HOLD_FRAMES)

// cuántas veces entra al video el fotograma `frame` de la composición «Deck»: Chrome compone solo las animaciones (SLIDE_FRAMES
// por lámina) y el último fotograma de cada lámina se repite VIDEO_HOLD_FRAMES veces más al codificar, en vez de componer la
// misma imagen 150 veces (el ffmpeg de Remotion no trae los filtros loop ni tpad)
export const copiesOf = (frame: number) => (frame % SLIDE_FRAMES === SLIDE_FRAMES - 1 ? 1 + VIDEO_HOLD_FRAMES : 1)
