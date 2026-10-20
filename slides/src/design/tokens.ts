import type { Audience } from '../spec/schema.ts'

// el sistema visual de las presentaciones (deliverables.md §16.6). una lámina mide 1920 × 1080 px: el PDF la imprime a
// 1440 × 810 pt. los tokens se versionan: cambiar uno cambia `DESIGN_VERSION` y pasa por la revisión visual (§16.9)
export const DESIGN_VERSION = '1'

export const PAGE = { width: 1920, height: 1080 } as const

// la retícula: 12 columnas entre los márgenes, con su medianil; el pie ocupa una banda fija abajo
export const GRID = {
  left: 120,
  right: 120,
  top: 104,
  footer: 76,
  columns: 12,
  gutter: 32
} as const

export const contentWidth = PAGE.width - GRID.left - GRID.right
export const column = (contentWidth - GRID.gutter * (GRID.columns - 1)) / GRID.columns
// el ancho de n columnas con sus medianiles
export const span = (n: number) => column * n + GRID.gutter * (n - 1)

// la escala de espacios, en múltiplos de 8
export const SPACE = { xs: 8, s: 16, m: 24, l: 40, xl: 64, xxl: 96 } as const

// las familias: un solo nombre de una palabra cada una (con espacios, Chrome no las aplicó en el pico del 2026-10-20)
export const FONT = {
  display: 'SgspeDisplay',
  serif: 'SgspeSerif',
  sans: 'SgspeSans'
} as const

// la tinta y el papel. el papel es un gris frío casi blanco: un proyector lava los tonos cálidos y el blanco puro deslumbra
export const INK = {
  paper: '#F4F5F3',
  surface: '#FFFFFF',
  ink: '#17202B',
  secondary: '#465261',
  muted: '#5E6875',
  rule: '#D3D7DC',
  faint: '#E6E9EC',
  // lo reservado o ausente: un gris en cursiva que no se confunde con un dato y aún se lee (4.5:1)
  absent: '#636D79',
  // el realce de un dato en foco: cobre, el mineral del país; se usa en una sola marca por gráfico
  focus: '#9C5A25'
} as const

// los niveles de la matriz de riesgos, siempre con su rótulo (nunca el color solo)
export const TONE = {
  NONE: { fill: '#ECEEF0', ink: INK.muted },
  LOW: { fill: '#CFE3D3', ink: '#1F4A2A' },
  MEDIUM: { fill: '#F3DFA2', ink: '#5A4300' },
  HIGH: { fill: '#EDB48E', ink: '#5E2A08' },
  CRITICAL: { fill: '#B8322E', ink: '#FFFFFF' }
} as const

// la escala tipográfica (px a 1920 × 1080). ningún texto baja de `min` (RQ-REP-036)
export type TypeScale = {
  display: number
  title: number
  lead: number
  body: number
  small: number
  figure: number
  min: number
}

const BASE: TypeScale = { display: 104, title: 60, lead: 40, body: 32, small: 26, figure: 148, min: 24 }

// cómo cambia cada público (deliverables.md §16.5): la escala, la densidad y el tono, sobre la misma retícula y paleta
export type Tone = {
  type: TypeScale
  // la letra del texto corrido: serif para la lectura atenta (investigación), sans para lo demás
  bodyFont: string
  // cuántas palabras admite un bloque de texto antes de dividir la lámina
  wordsPerBlock: number
  // el gráfico lleva ejes y rejilla, o solo rótulos directos (comunidad)
  axes: boolean
  // una banda de acento arriba, la marca de lo formal (instituciones)
  band: boolean
  // la voz del título: la serif de titulares (hallazgo), la serif de texto (monografía) o la sans con filete (oficio)
  titleFont: 'display' | 'serif' | 'sans'
  // las cifras clave como cartel sobre el campo de acento, para leerse de lejos en una asamblea
  poster: boolean
}

export const TONES: Record<Audience, Tone> = {
  DIRECCION: { type: BASE, bodyFont: FONT.sans, wordsPerBlock: 40, axes: true, band: false, titleFont: 'display', poster: false },
  CAMPO: { type: { ...BASE, title: 56 }, bodyFont: FONT.sans, wordsPerBlock: 60, axes: true, band: false, titleFont: 'display', poster: false },
  INVESTIGACION: {
    type: { ...BASE, title: 54, body: 31 },
    bodyFont: FONT.serif,
    wordsPerBlock: 80,
    axes: true,
    band: false,
    titleFont: 'serif',
    poster: false
  },
  INSTITUCIONES: { type: { ...BASE, title: 54 }, bodyFont: FONT.sans, wordsPerBlock: 55, axes: true, band: true, titleFont: 'sans', poster: false },
  COMUNIDAD: {
    type: { display: 112, title: 68, lead: 46, body: 38, small: 30, figure: 176, min: 28 },
    bodyFont: FONT.sans,
    wordsPerBlock: 28,
    axes: false,
    band: false,
    titleFont: 'display',
    poster: true
  }
}
