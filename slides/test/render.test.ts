import { bundle } from '@remotion/bundler'
import { openBrowser, type HeadlessBrowser } from '@remotion/renderer'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { PDFDocument } from 'pdf-lib'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { SlideFailure, renderDeck } from '../server/render.ts'
import { Deck, type DeckSpec } from '../src/spec/schema.ts'

// composición real con Chrome headless (deliverables.md §16.7). corre con SLIDES_RENDER=1: en la CI, en el job de las láminas
const enabled = process.env.SLIDES_RENDER === '1'
const fixture = (name: string): DeckSpec => Deck.parse(JSON.parse(fs.readFileSync(`fixtures/${name}.json`, 'utf8')))

describe.runIf(enabled)('la composición a PDF', () => {
  let serveUrl = ''
  let browser: HeadlessBrowser

  beforeAll(async () => {
    serveUrl =
      process.env.SLIDES_BUNDLE ??
      (await bundle({ entryPoint: path.resolve('src/remotion/index.ts'), outDir: fs.mkdtempSync(path.join(os.tmpdir(), 'bundle-')) }))
    browser = await openBrowser('chrome', { logLevel: 'error' })
  })
  afterAll(async () => browser?.close({ silent: true }))

  const render = (deck: DeckSpec) => renderDeck(deck, { serveUrl, browser, timeoutMs: 120_000 })

  it('una página 16:9 por lámina, con los metadatos fijos', async () => {
    for (const name of ['demo-t1-direccion', 'demo-t1-comunidad', 'demo-t1-instituciones']) {
      const deck = fixture(name)
      const pdf = await PDFDocument.load(await render(deck), { updateMetadata: false })
      expect(pdf.getPageCount()).toBe(deck.slides.length)
      for (const page of pdf.getPages()) {
        expect(page.getWidth()).toBeCloseTo(1440, 0)
        expect(page.getHeight()).toBeCloseTo(810, 0)
      }
      expect(pdf.getProducer()).toBe('sgspe-slides')
      expect(pdf.getCreationDate()?.toISOString()).toBe('2000-01-01T00:00:00.000Z')
    }
  })

  it('un título que no cabe cancela la presentación con SLIDE_OVERFLOW y el id de la lámina', async () => {
    const deck = fixture('demo-t1-direccion')
    deck.slides = [deck.slides[0]!, { ...deck.slides[3]!, title: 'Un título larguísimo '.repeat(7).trim().slice(0, 160) }]
    const failure = await render(deck).catch((e: unknown) => e)
    expect(failure).toBeInstanceOf(SlideFailure)
    expect(failure).toMatchObject({ code: 'SLIDE_OVERFLOW', slide: 'cifras' })
  })

  it('una imagen que no es lo que dice ser es ASSET_INVALID', async () => {
    const deck = fixture('demo-t1-direccion')
    deck.assets = { foto: { mime: 'image/png', width: 10, height: 10, alt: 'x', data: Buffer.from([0xff, 0xd8, 0xff, 0xe0]).toString('base64') } }
    deck.slides = [{ id: 'fotos', purpose: 'x', title: 'Fotos', data: { layout: 'photos', images: ['foto'], caption: 'x' } }]
    await expect(render(deck)).rejects.toMatchObject({ code: 'ASSET_INVALID' })
  })
})
