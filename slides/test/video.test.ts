import { describe, expect, it } from 'vitest'
import { encoderArgs, inOrder, mp4Frames } from '../server/render.ts'
import { Deck } from '../src/spec/schema.ts'
import fs from 'node:fs'

const box = (type: string, body: Uint8Array) => {
  const out = new Uint8Array(8 + body.length)
  new DataView(out.buffer).setUint32(0, out.length)
  out.set(new TextEncoder().encode(type), 4)
  out.set(body, 8)
  return out
}
const concat = (...parts: Uint8Array[]) => {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0))
  let at = 0
  for (const part of parts) {
    out.set(part, at)
    at += part.length
  }
  return out
}
// un mvhd de la versión 0 (tiempos de 32 bits) o 1 (de 64)
const mvhd = (version: 0 | 1, timescale: number, duration: number) => {
  const body = new Uint8Array(version === 1 ? 108 : 96)
  const view = new DataView(body.buffer)
  body[0] = version
  if (version === 1) {
    view.setUint32(20, timescale)
    view.setBigUint64(24, BigInt(duration))
  } else {
    view.setUint32(12, timescale)
    view.setUint32(16, duration)
  }
  return box('mvhd', body)
}
const ftyp = box('ftyp', new TextEncoder().encode('isom\0\0\x02\0isomiso2avc1mp41'))

describe('la duración de un MP4', () => {
  it('lee los fotogramas de mvhd en las versiones 0 y 1', () => {
    expect(mp4Frames(concat(ftyp, box('moov', mvhd(0, 15360, 15360 * 112))))).toBe(3360)
    expect(mp4Frames(concat(ftyp, box('free', new Uint8Array(4)), box('moov', concat(box('trak', new Uint8Array(0)), mvhd(1, 1000, 8000)))))).toBe(240)
  })

  it('un archivo sin moov, cortado o que no es MP4 no tiene duración', () => {
    expect(mp4Frames(ftyp)).toBeNull()
    expect(mp4Frames(concat(ftyp, box('moov', mvhd(0, 1000, 8000))).subarray(0, 40))).toBeNull()
    expect(mp4Frames(new TextEncoder().encode('%PDF-1.7 no es un video'))).toBeNull()
  })
})

describe('el codificador', () => {
  const deck = Deck.parse(JSON.parse(fs.readFileSync('fixtures/demo-t1-direccion.json', 'utf8')))

  it('recibe JPEG por la entrada estándar y escribe H.264 sin audio en yuv420p, con el índice al principio', () => {
    const args = encoderArgs(deck, '/tmp/x/deck.mp4')
    const after = (flag: string) => args[args.indexOf(flag) + 1]
    expect([after('-r'), after('-f'), after('-i')]).toEqual(['30', 'image2pipe', '-'])
    expect(args.slice(args.indexOf('-i') + 2)).toContain('-an')
    expect([after('-c:v'), args[args.lastIndexOf('-c:v') + 1], after('-pix_fmt'), after('-movflags')]).toEqual(['mjpeg', 'libx264', 'yuv420p', '+faststart'])
    expect(args).toContain(`title=${deck.meta.title}`)
    expect(args.slice(-2)).toEqual(['-y', '/tmp/x/deck.mp4'])
  })

  it('no usa filtros que el ffmpeg de Remotion no trae', () => {
    const args = encoderArgs(deck, '/tmp/x/deck.mp4')
    expect(args[args.indexOf('-vf') + 1]).toBe('scale=out_range=tv')
    expect(args.filter((a) => a === '-vf' || a === '-filter_complex')).toHaveLength(1)
  })
})

describe('los fotogramas en orden', () => {
  it('escribe cada fotograma después del anterior aunque lleguen desordenados', async () => {
    const written: number[] = []
    const write = inOrder(async (_buffer, frame) => {
      await new Promise((r) => setTimeout(r, 1))
      written.push(frame)
    })
    await Promise.all([3, 1, 4, 0, 2].map((frame) => write(Buffer.alloc(0), frame)))
    expect(written).toEqual([0, 1, 2, 3, 4])
  })
})
