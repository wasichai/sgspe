import { describe, expect, it } from 'vitest'
import { rings, seedOf } from '../src/design/contours.ts'
import { INK, TONE, TONES, span, contentWidth } from '../src/design/tokens.ts'
import { ticks, tickText } from '../src/layouts/charts.tsx'
import { contrast } from './contrast.ts'

describe('el sistema visual', () => {
  it('doce columnas con sus medianiles llenan el ancho útil', () => {
    expect(span(12)).toBeCloseTo(contentWidth, 6)
  })

  it('todo texto alcanza 4.5:1 sobre el papel', () => {
    for (const ink of [INK.ink, INK.secondary, INK.muted, INK.absent, INK.focus]) expect(contrast(ink, INK.paper)).toBeGreaterThanOrEqual(4.5)
  })

  it('cada nivel de la matriz rotula su celda con 4.5:1', () => {
    for (const tone of Object.values(TONE)) expect(contrast(tone.ink, tone.fill)).toBeGreaterThanOrEqual(4.5)
  })

  it('ningún público baja de 24 px', () => {
    for (const tone of Object.values(TONES)) expect(tone.type.min).toBeGreaterThanOrEqual(24)
  })

  it('las curvas de nivel son las mismas para el mismo código y distintas para otro', () => {
    const hill = { cx: 500, cy: 300, rings: 6, step: 30 }
    expect(rings(hill, seedOf('DEMO-T1-001/portada'))).toEqual(rings(hill, seedOf('DEMO-T1-001/portada')))
    expect(rings(hill, seedOf('DEMO-T1-001/portada'))).not.toEqual(rings(hill, seedOf('DEMO-T2-001/portada')))
  })

  it('las marcas del eje son redondas y van con el formato del informe', () => {
    expect(ticks(1, 5)).toEqual([1, 2, 3, 4, 5])
    expect(ticks(0, 1200)).toEqual([0, 500, 1000])
    expect(tickText(1250.5)).toBe('1,250.5')
    expect(tickText(-3)).toBe('-3')
  })
})
