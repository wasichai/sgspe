// arranca sgspe-slides: un solo navegador (se vuelve a abrir si se cae), el bundle preparado al construir la imagen y el
// servidor HTTP. sin licenseKey: en la v4 intentaría enviar telemetría, y el servicio no tiene red de salida (ADR-0031)
import { openBrowser, type HeadlessBrowser } from '@remotion/renderer'
import { SlideFailure, renderDeck } from './render.ts'
import { SETTINGS_FROM_ENV, slidesServer } from './server.ts'

const serveUrl = process.env.SLIDES_BUNDLE ?? '/app/bundle'
const port = Number(process.env.SLIDES_PORT ?? 8080)

let browser: Promise<HeadlessBrowser> | null = null
const current = () => (browser ??= openBrowser('chrome', { logLevel: 'error' }))

const server = slidesServer(async (deck, timeoutMs) => {
  try {
    return await renderDeck(deck, { serveUrl, browser: await current(), timeoutMs })
  } catch (error) {
    // tras una falla de composición (no de una lámina) el navegador puede haber quedado inservible: se cierra y el pedido
    // siguiente abre otro
    if (error instanceof Error && !(error instanceof SlideFailure && error.code !== 'RENDER_FAILED' && error.code !== 'TIMEOUT')) {
      const open = await browser?.catch(() => null)
      browser = null
      await open?.close({ silent: true }).catch(() => undefined)
    }
    throw error
  }
}, SETTINGS_FROM_ENV())

await current()
server.listen(port, () => process.stderr.write(JSON.stringify({ event: 'listening', port }) + '\n'))
