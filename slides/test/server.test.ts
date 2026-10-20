import fs from 'node:fs'
import type { AddressInfo } from 'node:net'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { SlideFailure } from '../server/render.ts'
import { semaphore, slidesServer, type Renderer } from '../server/server.ts'

const deck = fs.readFileSync('fixtures/demo-t1-direccion.json', 'utf8')
let mode: 'ok' | 'overflow' | 'timeout' = 'ok'
const render: Renderer = async () => {
  if (mode === 'overflow') throw new SlideFailure('SLIDE_OVERFLOW', 'tesis')
  if (mode === 'timeout') throw new SlideFailure('TIMEOUT', 'tesis')
  return new TextEncoder().encode('%PDF-1.7')
}
const server = slidesServer(render, { maxBytes: 1_000_000, timeoutMs: 1000, concurrency: 2 })
let base = ''

beforeAll(async () => {
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
})
afterAll(() => server.close())

const post = (body: string) => fetch(`${base}/render`, { method: 'POST', body, headers: { 'content-type': 'application/json', 'x-trace': 'job-09-prueba' } })

describe('el servicio', () => {
  it('responde la salud', async () => {
    const response = await fetch(`${base}/health`)
    expect(response.status).toBe(200)
    expect(await response.json()).toEqual({ status: 'up' })
  })

  it('compone una especificación válida y dice cuántas láminas', async () => {
    mode = 'ok'
    const response = await post(deck)
    expect(response.status).toBe(200)
    expect(response.headers.get('content-type')).toBe('application/pdf')
    expect(response.headers.get('x-slides-count')).toBe('14')
    expect(response.headers.get('x-slides-renderer')).toMatch(/^remotion@4\.0\.\d+$/)
    expect(response.headers.get('x-slides-design')).toBe('1')
  })

  it('rechaza un cuerpo vacío, uno que no es JSON y una especificación inválida, sin repetir su texto', async () => {
    expect((await post('')).status).toBe(400)
    expect(await (await post('{')).json()).toEqual({ code: 'NOT_JSON' })
    const bad = JSON.parse(deck)
    bad.slides[1].title = 'Ver https://ejemplo.test/secreto'
    const response = await post(JSON.stringify(bad))
    expect(response.status).toBe(400)
    const text = await response.text()
    expect(text).toContain('SPEC_INVALID')
    expect(text).not.toContain('secreto')
  })

  it('rechaza un cuerpo demasiado grande', async () => {
    expect((await post(' '.repeat(1_000_001))).status).toBe(413)
  })

  it('una lámina que no cabe es 422 con su código y su id; el plazo vencido, 504', async () => {
    mode = 'overflow'
    const overflow = await post(deck)
    expect(overflow.status).toBe(422)
    expect(await overflow.json()).toEqual({ code: 'SLIDE_OVERFLOW', slide: 'tesis' })
    mode = 'timeout'
    expect((await post(deck)).status).toBe(504)
  })

  it('otra ruta es 404', async () => {
    expect((await fetch(`${base}/otra`)).status).toBe(404)
  })
})

describe('el semáforo', () => {
  it('deja correr a lo más su tamaño a la vez', async () => {
    const slot = semaphore(2)
    let running = 0
    let peak = 0
    const work = () =>
      slot(async () => {
        running++
        peak = Math.max(peak, running)
        await new Promise((r) => setTimeout(r, 20))
        running--
      })
    await Promise.all([work(), work(), work(), work(), work()])
    expect(peak).toBe(2)
  })
})
