import { FONT } from './design/tokens.ts'

// las fuentes de las láminas, vendidas con el paquete (OFL): las mismas Source Serif 4 y Source Sans 3 del PDF del entregable.
// se cargan con FontFace desde el propio paquete, nunca de la red; el compositor y el reproductor las resuelven con su
// empaquetador (new URL(..., import.meta.url))
const FACES: { family: string; file: URL; weight: string; style: string }[] = [
  { family: FONT.display, file: new URL('../fonts/SourceSerif4Display-Semibold.ttf', import.meta.url), weight: '600', style: 'normal' },
  { family: FONT.display, file: new URL('../fonts/SourceSerif4Display-SemiboldIt.ttf', import.meta.url), weight: '600', style: 'italic' },
  { family: FONT.serif, file: new URL('../fonts/SourceSerif4-Regular.ttf', import.meta.url), weight: '400', style: 'normal' },
  { family: FONT.serif, file: new URL('../fonts/SourceSerif4-It.ttf', import.meta.url), weight: '400', style: 'italic' },
  { family: FONT.sans, file: new URL('../fonts/SourceSans3-Regular.ttf', import.meta.url), weight: '400', style: 'normal' },
  { family: FONT.sans, file: new URL('../fonts/SourceSans3-It.ttf', import.meta.url), weight: '400', style: 'italic' },
  { family: FONT.sans, file: new URL('../fonts/SourceSans3-Semibold.ttf', import.meta.url), weight: '600', style: 'normal' },
  { family: FONT.sans, file: new URL('../fonts/SourceSans3-Bold.ttf', import.meta.url), weight: '700', style: 'normal' }
]

let loading: Promise<void> | null = null

// carga una sola vez todas las caras; resuelve cuando están listas para medir
export function loadFonts(): Promise<void> {
  loading ??= Promise.all(
    FACES.map(async (face) => {
      const font = new FontFace(face.family, `url(${face.file.href})`, { weight: face.weight, style: face.style })
      document.fonts.add(await font.load())
    })
  ).then(() => undefined)
  return loading
}

// cada cara está cargada en el documento. `document.fonts.check` no basta: con una familia que no existe responde true, porque
// nada queda por cargar, y el texto saldría con una fuente de reemplazo
export function fontsReady(): boolean {
  const loaded = new Set<string>()
  document.fonts.forEach((font) => {
    if (font.status === 'loaded') loaded.add(`${font.family.replaceAll('"', '')}/${font.weight}/${font.style}`)
  })
  return FACES.every((face) => loaded.has(`${face.family}/${face.weight}/${face.style}`))
}
