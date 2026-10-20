// las curvas de nivel de la portada y los separadores: el territorio como firma visual. son deterministas (una función del
// código del entregable): ni azar ni fecha (RQ-REP-037). cada cerro es una familia de anillos anidados alrededor de un centro,
// con un contorno ondulado por una suma de senos cuya amplitud crece con el radio, así los anillos no se cruzan

// FNV-1a de 32 bits: la semilla sale del texto
export function seedOf(text: string): number {
  let hash = 0x811c9dc5
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193) >>> 0
  }
  return hash
}

// un generador pseudoaleatorio con semilla (mulberry32)
export function generator(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export type Hill = { cx: number; cy: number; rings: number; step: number }

const SAMPLES = 144

// los anillos de un cerro como trazos SVG cerrados, del más chico al más grande
export function rings(hill: Hill, seed: number): string[] {
  const next = generator(seed)
  const waves = [2, 3, 5, 7].map((k) => ({ k, phase: next() * Math.PI * 2, amplitude: 0.05 + next() * 0.07 }))
  const squash = 0.62 + next() * 0.2
  const tilt = (next() - 0.5) * 0.8
  const paths: string[] = []
  for (let ring = 1; ring <= hill.rings; ring++) {
    const radius = hill.step * ring
    // el relieve se suaviza hacia afuera: la amplitud relativa baja un poco con cada anillo
    const relief = 1 - ring / (hill.rings * 2.4)
    const points: [number, number][] = []
    for (let i = 0; i < SAMPLES; i++) {
      const angle = (i / SAMPLES) * Math.PI * 2
      const wobble = waves.reduce((sum, w) => sum + w.amplitude * relief * Math.sin(w.k * angle + w.phase + ring * 0.11), 0)
      const r = radius * (1 + wobble)
      const x = r * Math.cos(angle)
      const y = r * Math.sin(angle) * squash
      points.push([hill.cx + x * Math.cos(tilt) - y * Math.sin(tilt), hill.cy + x * Math.sin(tilt) + y * Math.cos(tilt)])
    }
    paths.push(smooth(points))
  }
  return paths
}

// un trazo cerrado y suave por los puntos (Catmull-Rom convertido a Bézier), con dos decimales
function smooth(points: [number, number][]): string {
  const n = points.length
  const at = (i: number) => points[((i % n) + n) % n]!
  const f = (v: number) => v.toFixed(2)
  let d = `M${f(at(0)[0])},${f(at(0)[1])}`
  for (let i = 0; i < n; i++) {
    const [x0, y0] = at(i - 1)
    const [x1, y1] = at(i)
    const [x2, y2] = at(i + 1)
    const [x3, y3] = at(i + 2)
    d += `C${f(x1 + (x2 - x0) / 6)},${f(y1 + (y2 - y0) / 6)} ${f(x2 - (x3 - x1) / 6)},${f(y2 - (y3 - y1) / 6)} ${f(x2)},${f(y2)}`
  }
  return d + 'Z'
}
