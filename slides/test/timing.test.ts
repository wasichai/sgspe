import { describe, expect, it } from 'vitest'
import { FPS, SLIDE_FRAMES, VIDEO_HOLD_FRAMES, copiesOf, videoFrames } from '../src/timing.ts'

// el MP4 (deliverables.md §16.2): 3 s de animación y 5 s quieta por lámina, a 30 fotogramas por segundo
describe('los tiempos del video', () => {
  it('cada lámina dura 8 s', () => {
    expect((SLIDE_FRAMES + VIDEO_HOLD_FRAMES) / FPS).toBe(8)
    expect(videoFrames(14)).toBe(14 * 240)
  })

  it('solo el último fotograma de cada lámina se repite, VIDEO_HOLD_FRAMES veces más', () => {
    const frames = Array.from({ length: 3 * SLIDE_FRAMES }, (_, frame) => copiesOf(frame))
    expect(frames.reduce((a, b) => a + b, 0)).toBe(videoFrames(3))
    expect([copiesOf(0), copiesOf(88), copiesOf(89), copiesOf(90), copiesOf(179), copiesOf(269)]).toEqual([1, 1, 151, 1, 151, 151])
  })
})
