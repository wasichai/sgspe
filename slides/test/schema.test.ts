import fs from 'node:fs'
import { describe, expect, it } from 'vitest'
import { Deck, assetRefs } from '../src/spec/schema.ts'

const fixture = (name: string) => JSON.parse(fs.readFileSync(`fixtures/${name}.json`, 'utf8'))

describe('la especificación', () => {
  it('las presentaciones de demostración son válidas', () => {
    for (const name of ['demo-t1-direccion', 'demo-t1-comunidad', 'demo-t1-instituciones']) expect(Deck.safeParse(fixture(name)).success).toBe(true)
  })

  it('rechaza una URL en cualquier texto', () => {
    const deck = fixture('demo-t1-direccion')
    deck.slides[1].title = 'Ver https://ejemplo.test/x'
    const parsed = Deck.safeParse(deck)
    expect(parsed.success).toBe(false)
    expect(parsed.error!.issues[0]!.message).toBe('URL_NOT_ALLOWED')
  })

  it('rechaza caracteres de control y un layout desconocido', () => {
    const deck = fixture('demo-t1-direccion')
    deck.slides[1].title = 'a\u0007b'
    expect(Deck.safeParse(deck).success).toBe(false)
    const other = fixture('demo-t1-direccion')
    other.slides[1].data = { layout: 'html', html: '<script></script>' }
    expect(Deck.safeParse(other).success).toBe(false)
  })

  it('rechaza una imagen que no está en la especificación y una lámina repetida', () => {
    const deck = fixture('demo-t1-direccion')
    deck.slides.push({ id: 'mapa', purpose: 'x', title: 'Mapa', data: { layout: 'map', image: 'no-existe' } })
    deck.slides.push({ ...deck.slides[1] })
    const messages = Deck.safeParse(deck).error!.issues.map((i) => i.message)
    expect(messages).toContain('ASSET_MISSING')
    expect(messages).toContain('DUPLICATE_SLIDE')
  })

  it('no admite una versión de esquema distinta', () => {
    expect(Deck.safeParse({ ...fixture('demo-t1-direccion'), schema: 2 }).success).toBe(false)
  })

  it('sabe qué imágenes usa cada lámina', () => {
    expect(assetRefs({ layout: 'photos', images: ['a', 'b'], caption: 'x' })).toEqual(['a', 'b'])
    expect(assetRefs({ layout: 'summary', points: ['x'] })).toEqual([])
  })
})
