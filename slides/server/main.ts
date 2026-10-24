// arranca sgspe-slides: un solo navegador (se vuelve a abrir si se cae), el bundle preparado al construir la imagen y el
// servidor HTTP. sin licenseKey: en la v4 intentaría enviar telemetría, y el servicio no tiene red de salida (ADR-0031)
import { openBrowser, type HeadlessBrowser } from '@remotion/renderer'
import { SlideFailure, renderDeck, renderDeckVideo } from './render.ts'
import { SETTINGS_FROM_ENV, slidesServer } from './server.ts'

const serveUrl = process.env.SLIDES_BUNDLE ?? '/app/bundle'
const port = Number(process.env.SLIDES_PORT ?? 8080)

let browser: Promise<HeadlessBrowser> | null = null
const current = () => (browser ??= openBrowser('chrome', { logLevel: 'error' }))

// las pestañas con que se compone un MP4 (las del PDF son una por lámina, una tras otra)
const threads = Math.max(1, Number(process.env.SLIDES_VIDEO_THREADS ?? 2))

// compone con el navegador abierto; tras una falla de composición (no de una lámina) el navegador puede haber quedado
// inservible: se cierra y el pedido siguiente abre otro
const withBrowser = async (work: (chrome: HeadlessBrowser) => Promise<Uint8Array>) => {
  try {
    return await work(await current())
  } catch (error) {
    if (error instanceof Error && !(error instanceof SlideFailure && error.code !== 'RENDER_FAILED' && error.code !== 'TIMEOUT')) {
      const open = await browser?.catch(() => null)
      browser = null
      await open?.close({ silent: true }).catch(() => undefined)
    }
    throw error
  }
}

const server = slidesServer(
  {
    pdf: (deck, timeoutMs) => withBrowser((chrome) => renderDeck(deck, { serveUrl, browser: chrome, timeoutMs })),
    video: (deck, timeoutMs) => withBrowser((chrome) => renderDeckVideo(deck, { serveUrl, browser: chrome, timeoutMs, threads }))
  },
  SETTINGS_FROM_ENV()
)

await current()
server.listen(port, () => process.stderr.write(JSON.stringify({ event: 'listening', port }) + '\n'))
