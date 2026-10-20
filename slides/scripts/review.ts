// la revisión visual (deliverables.md §16.9): compone cada lámina de una especificación a PNG y su hoja de contacto.
// uso: yarn review <deck.json> <carpeta> [--scale 0.5]
import { bundle } from '@remotion/bundler'
import { openBrowser, renderStill, selectComposition } from '@remotion/renderer'
import fs from 'node:fs'
import path from 'node:path'

const [specPath, outDir] = process.argv.slice(2)
if (!specPath || !outDir) {
  console.error('uso: yarn review <deck.json> <carpeta>')
  process.exit(2)
}
const scale = Number(process.argv[process.argv.indexOf('--scale') + 1] ?? '0.5') || 0.5
const deck = JSON.parse(fs.readFileSync(specPath, 'utf8'))
fs.mkdirSync(outDir, { recursive: true })
const serveUrl = process.env.SLIDES_BUNDLE ?? (await bundle({ entryPoint: path.resolve('src/remotion/index.ts') }))
const browser = await openBrowser('chrome')
let failed = 0
try {
  for (const [index, slide] of deck.slides.entries()) {
    const inputProps = { deck: { meta: deck.meta, theme: deck.theme, assets: deck.assets }, slide, index, total: deck.slides.length }
    const composition = await selectComposition({ serveUrl, id: 'Slide', inputProps, puppeteerInstance: browser })
    const file = path.join(outDir, `${String(index + 1).padStart(2, '0')}-${slide.id}.png`)
    try {
      await renderStill({ serveUrl, composition, inputProps, frame: composition.durationInFrames - 1, output: file, scale, puppeteerInstance: browser })
      console.log('ok', file)
    } catch (error) {
      failed++
      console.log('FALLA', slide.id, String((error as Error).message).split('\n')[0])
    }
  }
  const sheet = await selectComposition({ serveUrl, id: 'Sheet', inputProps: { deck }, puppeteerInstance: browser })
  await renderStill({
    serveUrl,
    composition: sheet,
    inputProps: { deck },
    frame: sheet.durationInFrames - 1,
    output: path.join(outDir, 'hoja.png'),
    puppeteerInstance: browser
  })
  console.log('ok', path.join(outDir, 'hoja.png'))
} finally {
  await browser.close({ silent: true })
}
process.exit(failed ? 1 : 0)
