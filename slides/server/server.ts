// el servicio sgspe-slides (ADR-0031, deliverables.md §16.7): POST /render recibe una especificación (JSON) y devuelve el PDF
// de láminas; GET /health dice si está arriba. solo lo llama JOB-09. los errores van como {code, slide?}, nunca con texto del
// documento; el log lleva el id de correlación, el estado, la duración y el número de láminas, nunca contenido
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http'
import { createRequire } from 'node:module'
import { DESIGN_VERSION } from '../src/design/tokens.ts'
import { Deck, type DeckSpec } from '../src/spec/schema.ts'
import { SlideFailure } from './render.ts'

export type Settings = {
  maxBytes: number
  timeoutMs: number
  concurrency: number
}

export type Renderer = (deck: DeckSpec, timeoutMs: number) => Promise<Uint8Array>

export const SETTINGS_FROM_ENV = (): Settings => ({
  maxBytes: Number(process.env.SLIDES_MAX_BYTES ?? 67_108_864),
  timeoutMs: Number(process.env.SLIDES_TIMEOUT ?? 170) * 1000,
  concurrency: Math.max(1, Number(process.env.SLIDES_CONCURRENCY ?? 2))
})

const TRACE = /^[A-Za-z0-9._-]{1,64}$/

// la versión del compositor que se informa con cada PDF (deliverable_presentation.renderer_version): la de Remotion instalada
export const RENDERER = `remotion@${(createRequire(import.meta.url)('remotion/package.json') as { version: string }).version}`

function log(fields: Record<string, unknown>) {
  process.stderr.write(JSON.stringify(fields) + '\n')
}

// un semáforo: a lo más `size` composiciones a la vez; las demás esperan su turno
export function semaphore(size: number) {
  let free = size
  const waiting: (() => void)[] = []
  return async <T>(work: () => Promise<T>): Promise<T> => {
    if (free > 0) free--
    else await new Promise<void>((resolve) => waiting.push(resolve))
    try {
      return await work()
    } finally {
      const next = waiting.shift()
      if (next) next()
      else free++
    }
  }
}

class TooLarge extends Error {}

async function body(request: IncomingMessage, max: number): Promise<Buffer> {
  const declared = Number(request.headers['content-length'] ?? 0)
  if (declared > max) throw new TooLarge()
  const chunks: Buffer[] = []
  let size = 0
  for await (const chunk of request) {
    size += (chunk as Buffer).length
    if (size > max) throw new TooLarge()
    chunks.push(chunk as Buffer)
  }
  return Buffer.concat(chunks)
}

function answer(response: ServerResponse, status: number, payload: object) {
  const text = JSON.stringify(payload)
  response.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(text) })
  response.end(text)
}

export function slidesServer(render: Renderer, settings: Settings): Server {
  const slot = semaphore(settings.concurrency)
  return createServer(async (request, response) => {
    const started = performance.now()
    const header = request.headers['x-trace']
    const trace = typeof header === 'string' && TRACE.test(header) ? header : '-'
    let status = 500
    let slides = 0
    try {
      if (request.method === 'GET' && request.url === '/health') {
        status = 200
        return answer(response, 200, { status: 'up' })
      }
      if (request.method !== 'POST' || request.url !== '/render') {
        status = 404
        return answer(response, 404, { code: 'NOT_FOUND' })
      }
      let raw: Buffer
      try {
        raw = await body(request, settings.maxBytes)
      } catch (error) {
        if (error instanceof TooLarge) {
          status = 413
          return answer(response, 413, { code: 'TOO_LARGE' })
        }
        throw error
      }
      if (raw.length === 0) {
        status = 400
        return answer(response, 400, { code: 'EMPTY' })
      }
      let json: unknown
      try {
        json = JSON.parse(raw.toString('utf8'))
      } catch {
        status = 400
        return answer(response, 400, { code: 'NOT_JSON' })
      }
      const parsed = Deck.safeParse(json)
      if (!parsed.success) {
        status = 400
        // la ruta del primer problema y su mensaje, que es un código (URL_NOT_ALLOWED...) o el de zod; nunca el valor
        const issue = parsed.error.issues[0]!
        return answer(response, 400, { code: 'SPEC_INVALID', path: issue.path.join('.'), reason: issue.message.slice(0, 80) })
      }
      slides = parsed.data.slides.length
      try {
        const pdf = await slot(() => render(parsed.data, settings.timeoutMs))
        status = 200
        response.writeHead(200, {
          'Content-Type': 'application/pdf',
          'Content-Length': pdf.length,
          'X-Slides-Count': String(slides),
          'X-Slides-Renderer': RENDERER,
          'X-Slides-Design': DESIGN_VERSION
        })
        response.end(Buffer.from(pdf))
      } catch (error) {
        if (error instanceof SlideFailure) {
          status = error.code === 'TIMEOUT' ? 504 : error.code === 'RENDER_FAILED' ? 500 : 422
          return answer(response, status, { code: error.code, slide: error.slide })
        }
        throw error
      }
    } catch (error) {
      status = 500
      log({ trace, event: 'error', kind: error instanceof Error ? error.name : 'unknown' })
      if (!response.headersSent) answer(response, 500, { code: 'INTERNAL' })
    } finally {
      log({ trace, status, ms: Math.round(performance.now() - started), slides })
    }
  })
}
