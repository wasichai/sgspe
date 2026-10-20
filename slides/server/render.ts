// compone una especificación ya validada a un PDF de una página por lámina (ADR-0031, deliverables.md §16.7). cada lámina es un
// renderStill a PDF de la composición «Slide» en su último fotograma, con solo sus imágenes; las páginas se unen con pdf-lib con
// metadatos fijos (ni la fecha ni el azar entran al archivo). una lámina que no pasa su verificación cancela todo: nunca hay
// archivo parcial
import { makeCancelSignal, renderStill, selectComposition, type HeadlessBrowser } from '@remotion/renderer'
import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { PDFDocument, PDFName, PDFString } from 'pdf-lib'
import { assetRefs, type AssetSpec, type DeckSpec } from '../src/spec/schema.ts'

// las láminas duran 90 fotogramas (src/Slide.tsx): el último es el estado completo
const LAST_FRAME = 89
// la fecha fija del archivo: el PDF no lleva el instante en que se compuso (RQ-REP-037)
const FIXED_DATE = new Date(Date.UTC(2000, 0, 1))

export class SlideFailure extends Error {
  readonly code: string
  readonly slide: string | null

  constructor(code: string, slide: string | null) {
    super(code)
    this.code = code
    this.slide = slide
  }
}

const PNG = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
const JPEG = [0xff, 0xd8, 0xff]

// una imagen incrustada es lo que dice ser: sus bytes mágicos y, en un PNG, sus dimensiones
export function checkAsset(asset: AssetSpec): boolean {
  const bytes = Buffer.from(asset.data, 'base64')
  const starts = (magic: number[]) => magic.every((b, i) => bytes[i] === b)
  if (asset.mime === 'image/png') {
    if (!starts(PNG) || bytes.length < 24) return false
    return bytes.readUInt32BE(16) === asset.width && bytes.readUInt32BE(20) === asset.height
  }
  return starts(JPEG)
}

export type RenderOptions = {
  serveUrl: string
  browser: HeadlessBrowser
  timeoutMs: number
}

export async function renderDeck(deck: DeckSpec, options: RenderOptions): Promise<Uint8Array> {
  for (const [id, asset] of Object.entries(deck.assets)) {
    if (!checkAsset(asset)) throw new SlideFailure('ASSET_INVALID', id)
  }
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'slides-'))
  const { cancelSignal, cancel } = makeCancelSignal()
  const timer = setTimeout(cancel, options.timeoutMs)
  try {
    const merged = await PDFDocument.create({ updateMetadata: false })
    const total = deck.slides.length
    for (const [index, slide] of deck.slides.entries()) {
      const refs = [...assetRefs(slide.data), ...(slide.data.layout === 'cover' && deck.theme.logo ? [deck.theme.logo] : [])]
      const assets = Object.fromEntries(refs.map((ref) => [ref, deck.assets[ref]!]))
      const inputProps = { deck: { meta: deck.meta, theme: deck.theme, assets }, slide, index, total }
      // la composición se elige por lámina: lleva sus props resueltas, y reusar la de otra lámina compondría esa otra
      const composition = await selectComposition({
        serveUrl: options.serveUrl,
        id: 'Slide',
        inputProps,
        puppeteerInstance: options.browser,
        logLevel: 'error'
      })
      const output = path.join(dir, `${index}.pdf`)
      try {
        await renderStill({
          serveUrl: options.serveUrl,
          composition,
          inputProps,
          frame: LAST_FRAME,
          imageFormat: 'pdf',
          output,
          puppeteerInstance: options.browser,
          cancelSignal,
          timeoutInMilliseconds: options.timeoutMs,
          logLevel: 'error'
        })
      } catch (error) {
        throw failureOf(error, slide.id)
      }
      const page = await PDFDocument.load(await fs.readFile(output))
      const [copy] = await merged.copyPages(page, [0])
      merged.addPage(copy!)
    }
    merged.setTitle(deck.meta.title)
    merged.setSubject(deck.meta.derivation)
    merged.setProducer('sgspe-slides')
    merged.setCreator('SGSPE')
    merged.setCreationDate(FIXED_DATE)
    merged.setModificationDate(FIXED_DATE)
    merged.catalog.set(PDFName.of('Lang'), PDFString.of('es-PE'))
    return await merged.save({ useObjectStreams: true })
  } finally {
    clearTimeout(timer)
    await fs.rm(dir, { recursive: true, force: true })
  }
}

// el error de una lámina: «SGSPE:<código>:<lámina>:<detalle>» si lo lanzó su verificación (src/Slide.tsx); un plazo vencido
// es TIMEOUT; lo demás, RENDER_FAILED. nunca lleva texto del documento
export function failureOf(error: unknown, slide: string): SlideFailure {
  const message = error instanceof Error ? error.message : String(error)
  const match = /SGSPE:([A-Z_]+):([a-z0-9-]+):/.exec(message)
  if (match) return new SlideFailure(match[1]!, match[2]!)
  if (/cancel/i.test(message)) return new SlideFailure('TIMEOUT', slide)
  return new SlideFailure('RENDER_FAILED', slide)
}
