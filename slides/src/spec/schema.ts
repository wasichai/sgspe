import { z } from 'zod'

// la especificación de una presentación (ADR-0031, deliverables.md §16.3): lo que SGSPE arma desde el modelo ya divulgado y
// lo único que las láminas pintan. es declarativa: cada lámina dice qué comunica (`purpose`), con qué layout y con qué datos;
// nunca trae código, HTML ni una URL. las cifras llegan formateadas en es-PE (`display`) y, para dibujar, como texto decimal
// con punto (`value`); un valor ausente es null, nunca cero. la versión de esquema sube con todo cambio incompatible
export const SCHEMA_VERSION = 1

// un texto de la presentación: sin caracteres de control y sin una URL, que el compositor nunca seguiría
const URL_LIKE = /\b(?:https?|ftp|file|data|javascript):|\bwww\./i
const CONTROL = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/
export const text = (max: number) =>
  z
    .string()
    .max(max)
    .refine((s) => !URL_LIKE.test(s), 'URL_NOT_ALLOWED')
    .refine((s) => !CONTROL.test(s), 'CONTROL_CHARACTER')
const line = text(400)
const decimal = z.string().regex(/^-?\d+(?:\.\d+)?$/)
const hex = z.string().regex(/^#[0-9A-F]{6}$/)
const id = z.string().regex(/^[a-z0-9][a-z0-9-]{0,63}$/)

export const LEVELS = ['INTERNO', 'EXTERNO', 'PUBLICO'] as const
export const AUDIENCES = ['DIRECCION', 'CAMPO', 'INVESTIGACION', 'INSTITUCIONES', 'COMUNIDAD'] as const

// una imagen incrustada: PNG o JPEG en base64, con sus dimensiones (el compositor las verifica) y su texto alternativo
export const Asset = z.object({
  mime: z.enum(['image/png', 'image/jpeg']),
  width: z.number().int().min(1).max(8000),
  height: z.number().int().min(1).max(8000),
  alt: line,
  data: z
    .string()
    .regex(/^[A-Za-z0-9+/]+={0,2}$/)
    .max(8_000_000)
})
const assetRef = id

// una cifra que se muestra: su texto (es-PE), su valor para dibujar y si se reservó (PUBLICO, celdas pequeñas)
// SGSPE escribe la especificación en JSON canónico, que omite los nulos: un `value` que falta es null, nunca cero
const Figure = z.object({
  display: text(40),
  value: decimal.nullish().transform((v) => v ?? null),
  reserved: z.boolean().default(false)
})

const Cover = z.object({
  layout: z.literal('cover'),
  subtitle: line.optional(),
  place: line.optional(),
  preparedFor: line.optional(),
  preparedBy: line.optional(),
  image: assetRef.optional()
})
const Section = z.object({ layout: z.literal('section'), number: text(4).optional(), lead: line.optional() })
const Thesis = z.object({
  layout: z.literal('thesis'),
  support: text(600).optional(),
  figures: z
    .array(z.object({ figure: Figure, label: line }))
    .max(3)
    .default([])
})
const Summary = z.object({ layout: z.literal('summary'), points: z.array(text(300)).min(1).max(5) })
const KeyFigures = z.object({
  layout: z.literal('figures'),
  items: z
    .array(z.object({ figure: Figure, label: line, detail: line.optional() }))
    .min(1)
    .max(4)
})
const Trend = z.object({
  layout: z.literal('trend'),
  unit: line,
  categories: z.array(text(40)).min(2).max(24),
  series: z
    .array(
      z.object({
        name: line,
        values: z.array(decimal.nullable()),
        labels: z.array(text(40).nullable()),
        n: z.array(z.number().int().nullable()).optional()
      })
    )
    .min(1)
    .max(3),
  min: decimal,
  max: decimal,
  reading: line.optional()
})
const Comparison = z.object({
  layout: z.literal('comparison'),
  unit: line,
  items: z
    .array(z.object({ label: text(80), figure: Figure, focus: z.boolean().default(false) }))
    .min(2)
    .max(10),
  max: decimal,
  reading: line.optional()
})
const MapSlide = z.object({ layout: z.literal('map'), image: assetRef, reading: line.optional() })
const Quote = z.object({
  layout: z.literal('quote'),
  quote: text(500),
  attribution: line,
  kind: z.enum(['TESTIMONIO', 'PERCEPCION', 'OBSERVACION']),
  scope: line
})
const Timeline = z.object({
  layout: z.literal('timeline'),
  events: z
    .array(z.object({ date: text(40), label: text(120), detail: line.optional() }))
    .min(2)
    .max(8)
})
const Matrix = z.object({
  layout: z.literal('matrix'),
  rowAxis: line,
  columnAxis: line,
  rows: z.array(text(40)).min(1).max(6),
  columns: z.array(text(40)).min(1).max(6),
  cells: z.array(z.array(z.object({ figure: Figure, tone: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'NONE']) }))),
  legend: z
    .array(z.object({ tone: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']), label: text(60) }))
    .max(4)
    .default([]),
  reading: line.optional()
})
const Table = z.object({
  layout: z.literal('table'),
  header: z.array(text(60)).min(2).max(6),
  align: z.array(z.enum(['LEFT', 'RIGHT'])),
  rows: z
    .array(z.array(text(120)))
    .min(1)
    .max(8)
})
const Method = z.object({
  layout: z.literal('method'),
  steps: z
    .array(z.object({ label: text(80), detail: text(240) }))
    .min(1)
    .max(4),
  facts: z
    .array(z.object({ figure: Figure, label: line }))
    .max(4)
    .default([])
})
const Scenarios = z.object({
  layout: z.literal('scenarios'),
  columns: z
    .array(z.object({ title: text(80), points: z.array(text(200)).min(1).max(4) }))
    .min(2)
    .max(3)
})
const Conclusions = z.object({
  layout: z.literal('conclusions'),
  conclusions: z.array(text(260)).min(1).max(4),
  nextSteps: z.array(text(200)).max(4).default([])
})
const Sources = z.object({
  layout: z.literal('sources'),
  sources: z.array(text(240)).min(1).max(6),
  limitations: z.array(text(260)).max(5).default([])
})
const Glossary = z.object({
  layout: z.literal('glossary'),
  terms: z
    .array(z.object({ term: text(60), meaning: text(220) }))
    .min(1)
    .max(5)
})
const Photos = z.object({ layout: z.literal('photos'), images: z.array(assetRef).min(1).max(2), caption: line })
const Closing = z.object({ layout: z.literal('closing'), message: text(300), contact: line.optional() })

export const SlideData = z.discriminatedUnion('layout', [
  Cover,
  Section,
  Thesis,
  Summary,
  KeyFigures,
  Trend,
  Comparison,
  MapSlide,
  Quote,
  Timeline,
  Matrix,
  Table,
  Method,
  Scenarios,
  Conclusions,
  Sources,
  Glossary,
  Photos,
  Closing
])

export const Slide = z.object({
  id,
  // el objetivo comunicativo de la lámina: no se pinta, guía la revisión y las notas del orador
  purpose: line,
  title: text(160),
  // la parte del arco a la que pertenece (el pie la muestra)
  chapter: text(60).optional(),
  source: text(300).optional(),
  note: text(400).optional(),
  speakerNotes: text(1200).optional(),
  data: SlideData
})

export const Theme = z.object({
  accent: hex,
  // el texto sobre el acento, ya elegido por contraste (blanco o tinta)
  onAccent: hex,
  logo: assetRef.optional()
})

export const Meta = z.object({
  code: text(40),
  title: text(200),
  project: text(200),
  revision: text(10),
  level: z.enum(LEVELS),
  audience: z.enum(AUDIENCES),
  dateLabel: text(60),
  period: text(80).optional(),
  organization: text(120).optional(),
  // el pie de derivación (ASM-143): «Derivada de <código> rev. <r>, nivel X; no es un documento emitido»
  derivation: text(200)
})

export const Deck = z
  .object({
    schema: z.literal(SCHEMA_VERSION),
    meta: Meta,
    theme: Theme,
    assets: z.record(id, Asset),
    slides: z.array(Slide).min(1).max(60)
  })
  .superRefine((deck, ctx) => {
    const ids = new Set<string>()
    deck.slides.forEach((slide, i) => {
      if (ids.has(slide.id)) ctx.addIssue({ code: 'custom', message: 'DUPLICATE_SLIDE', path: ['slides', i, 'id'] })
      ids.add(slide.id)
      for (const ref of assetRefs(slide.data)) {
        if (!(ref in deck.assets)) ctx.addIssue({ code: 'custom', message: 'ASSET_MISSING', path: ['slides', i] })
      }
    })
    if (deck.theme.logo && !(deck.theme.logo in deck.assets)) {
      ctx.addIssue({ code: 'custom', message: 'ASSET_MISSING', path: ['theme', 'logo'] })
    }
  })

export type DeckSpec = z.infer<typeof Deck>
export type SlideSpec = z.infer<typeof Slide>
export type SlideDataSpec = z.infer<typeof SlideData>
export type AssetSpec = z.infer<typeof Asset>
export type FigureSpec = z.infer<typeof Figure>
export type Audience = (typeof AUDIENCES)[number]
export type Level = (typeof LEVELS)[number]
export type Layout = SlideDataSpec['layout']
export type DataOf<L extends Layout> = Extract<SlideDataSpec, { layout: L }>

// las imágenes que usa una lámina: el renderer manda a cada composición solo las suyas
export function assetRefs(data: SlideDataSpec): string[] {
  switch (data.layout) {
    case 'cover':
      return data.image ? [data.image] : []
    case 'map':
      return [data.image]
    case 'photos':
      return data.images
    default:
      return []
  }
}
