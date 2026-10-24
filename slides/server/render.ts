// compone una especificación ya validada a un PDF de una página por lámina (ADR-0031, deliverables.md §16.7). cada lámina es un
// renderStill a PDF de la composición «Slide» en su último fotograma, con solo sus imágenes; las páginas se unen con pdf-lib con
// metadatos fijos (ni la fecha ni el azar entran al archivo). el MP4 (§16.2) sale de la composición «Deck» entera: Chrome
// compone solo las animaciones y el codificador deja cada lámina quieta VIDEO_HOLD_FRAMES con su estado completo. una lámina que no pasa su verificación cancela todo: nunca
// hay archivo parcial
import { RenderInternals, makeCancelSignal, renderFrames, renderStill, selectComposition, type HeadlessBrowser } from '@remotion/renderer'
import { once } from 'node:events'
import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { PDFDocument, PDFName, PDFString } from 'pdf-lib'
import { FPS as FPS_OF_VIDEO, SLIDE_FRAMES, copiesOf, videoFrames } from '../src/timing.ts'
import { assetRefs, type AssetSpec, type DeckSpec } from '../src/spec/schema.ts'

// el último fotograma de una lámina es su estado completo (src/timing.ts)
const LAST_FRAME = SLIDE_FRAMES - 1
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

export type VideoOptions = RenderOptions & {
  // las pestañas de Chrome que componen fotogramas a la vez dentro de un solo video (SLIDES_VIDEO_THREADS)
  threads: number
}

function checkAssets(deck: DeckSpec) {
  for (const [id, asset] of Object.entries(deck.assets)) {
    if (!checkAsset(asset)) throw new SlideFailure('ASSET_INVALID', id)
  }
}

export async function renderDeck(deck: DeckSpec, options: RenderOptions): Promise<Uint8Array> {
  checkAssets(deck)
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

// el MP4 de la presentación (deliverables.md §16.2): H.264 sin audio, 1920 × 1080 a FPS, con el ffmpeg que trae el compositor de
// Remotion (la imagen no necesita otro). renderFrames compone en `threads` pestañas los fotogramas de animación de «Deck» y los
// entrega en orden al codificador, que recibe el último de cada lámina VIDEO_HOLD_FRAMES veces más (copiesOf). un archivo sin la
// duración esperada es RENDER_FAILED: nunca sale un video al que le falten láminas
export async function renderDeckVideo(deck: DeckSpec, options: VideoOptions): Promise<Uint8Array> {
  checkAssets(deck)
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'slides-'))
  const { cancelSignal, cancel } = makeCancelSignal()
  let late = false
  const timer = setTimeout(() => {
    late = true
    cancel()
  }, options.timeoutMs)
  try {
    const inputProps = { deck, verifyLayout: false }
    const composition = await selectComposition({
      serveUrl: options.serveUrl,
      id: 'Deck',
      inputProps,
      puppeteerInstance: options.browser,
      logLevel: 'error'
    })
    const output = path.join(dir, 'deck.mp4')
    // RenderInternals.callFf es la misma llamada con que renderMedia corre su ffmpeg (Remotion va fijado en una versión exacta)
    const encoder = RenderInternals.callFf({
      bin: 'ffmpeg',
      args: encoderArgs(deck, output),
      indent: false,
      logLevel: 'error',
      binariesDirectory: null,
      cancelSignal
    })
    const finished = encoder.then(
      () => null,
      (error: unknown) => error
    )
    const stdin = encoder.stdin!
    stdin.on('error', () => undefined)
    const write = inOrder(async (buffer, frame) => {
      for (let copy = copiesOf(frame); copy > 0; copy--) {
        if (!stdin.write(buffer)) await Promise.race([once(stdin, 'drain'), finished])
      }
    })
    try {
      await renderFrames({
        serveUrl: options.serveUrl,
        composition,
        inputProps,
        outputDir: null,
        onStart: () => undefined,
        onFrameUpdate: () => undefined,
        onFrameBuffer: write,
        imageFormat: 'jpeg',
        jpegQuality: JPEG_QUALITY,
        muted: true,
        concurrency: options.threads,
        puppeteerInstance: options.browser,
        cancelSignal,
        timeoutInMilliseconds: options.timeoutMs,
        logLevel: 'error'
      })
      stdin.end()
    } catch (error) {
      encoder.kill()
      await finished
      throw failureOf(error, null)
    }
    if ((await finished) !== null) throw new SlideFailure(late ? 'TIMEOUT' : 'RENDER_FAILED', null)
    const bytes = new Uint8Array(await fs.readFile(output))
    if (mp4Frames(bytes) !== videoFrames(deck.slides.length)) throw new SlideFailure('RENDER_FAILED', null)
    return bytes
  } finally {
    clearTimeout(timer)
    await fs.rm(dir, { recursive: true, force: true })
  }
}

const JPEG_QUALITY = 90

// los argumentos del codificador: JPEG por la entrada estándar a FPS constantes, H.264 en yuv420p de rango limitado (el que abre
// cualquier reproductor), sin audio, con el índice al principio para que el video empiece a verse antes de bajarlo entero, y con el título
// y la derivación como metadatos, como el PDF
export function encoderArgs(deck: DeckSpec, output: string): string[] {
  return [
    ['-r', String(FPS_OF_VIDEO), '-f', 'image2pipe', '-c:v', 'mjpeg', '-i', '-'],
    ['-an', '-vf', 'scale=out_range=tv', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-tune', 'stillimage'],
    ['-pix_fmt', 'yuv420p', '-color_range', 'tv'],
    ['-movflags', '+faststart', '-map_metadata', '-1'],
    ['-metadata', `title=${deck.meta.title}`, '-metadata', `comment=${deck.meta.derivation}`],
    ['-y', output]
  ].flat()
}

// entrega los fotogramas en orden: las pestañas los terminan en cualquier orden, y cada uno espera a que se haya escrito el
// anterior (como renderMedia con su codificador en paralelo)
export function inOrder(write: (buffer: Buffer, frame: number) => Promise<void>) {
  let next = 0
  const waiting = new Map<number, () => void>()
  return async (buffer: Buffer, frame: number) => {
    if (frame !== next) await new Promise<void>((resolve) => waiting.set(frame, resolve))
    await write(buffer, frame)
    next = frame + 1
    const resume = waiting.get(next)
    waiting.delete(next)
    resume?.()
  }
}

// los fotogramas de un MP4 de una sola pista de video a FPS constantes: la duración de mvhd por los fotogramas por segundo. null
// si no es un MP4 que se pueda leer
export function mp4Frames(bytes: Uint8Array, fps = FPS_OF_VIDEO): number | null {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  const box = (from: number, to: number, type: string): number | null => {
    let at = from
    while (at + 8 <= to) {
      const size = view.getUint32(at)
      const name = String.fromCharCode(...bytes.subarray(at + 4, at + 8))
      if (size < 8 || at + size > to) return null
      if (name === type) return at
      at += size
    }
    return null
  }
  const moov = box(0, bytes.length, 'moov')
  if (moov === null) return null
  const mvhd = box(moov + 8, moov + view.getUint32(moov), 'mvhd')
  if (mvhd === null) return null
  const version = bytes[mvhd + 8]
  if (mvhd + (version === 1 ? 40 : 28) > bytes.length) return null
  const timescale = view.getUint32(mvhd + (version === 1 ? 28 : 20))
  const duration = version === 1 ? Number(view.getBigUint64(mvhd + 32)) : view.getUint32(mvhd + 24)
  return timescale > 0 ? Math.round((duration * fps) / timescale) : null
}

// el error de una lámina: «SGSPE:<código>:<lámina>:<detalle>» si lo lanzó su verificación (src/Slide.tsx); un plazo vencido
// es TIMEOUT; lo demás, RENDER_FAILED. nunca lleva texto del documento
export function failureOf(error: unknown, slide: string | null): SlideFailure {
  const message = error instanceof Error ? error.message : String(error)
  const match = /SGSPE:([A-Z_]+):([a-z0-9-]+):/.exec(message)
  if (match) return new SlideFailure(match[1]!, match[2]!)
  if (/cancel/i.test(message)) return new SlideFailure('TIMEOUT', slide)
  return new SlideFailure('RENDER_FAILED', slide)
}
