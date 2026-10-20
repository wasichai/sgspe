// la verificación de una lámina compuesta (RQ-REP-036, deliverables.md §16.7): corre en el navegador, sobre el último
// fotograma, con las fuentes ya cargadas. devuelve el primer problema con su código; el compositor cancela la lámina con él y
// el servicio responde 422 sin archivo parcial ni texto del documento. mide cajas de diseño, no la tinta de los glifos: un
// título con poco interlineado «desborda» por sus descendentes sin salirse de su caja (pico del 2026-10-20)
export type Problem = { code: 'SLIDE_OVERFLOW' | 'SLIDE_OVERLAP' | 'TEXT_TOO_SMALL' | 'ASSET_INVALID' | 'SLIDE_EMPTY'; detail: string }

const TOLERANCE = 1

function box(el: Element): DOMRect {
  return el.getBoundingClientRect()
}

function describe(el: Element): string {
  return el.getAttribute('data-name') ?? el.tagName.toLowerCase()
}

// cada región contiene a todos sus descendientes
function overflow(root: Element): Problem | null {
  for (const region of root.querySelectorAll('[data-region]')) {
    const outer = box(region)
    for (const child of region.querySelectorAll('*')) {
      const inner = box(child)
      if (inner.width === 0 && inner.height === 0) continue
      if (
        inner.left < outer.left - TOLERANCE ||
        inner.top < outer.top - TOLERANCE ||
        inner.right > outer.right + TOLERANCE ||
        inner.bottom > outer.bottom + TOLERANCE
      ) {
        return { code: 'SLIDE_OVERFLOW', detail: describe(region) }
      }
    }
  }
  return null
}

// dos regiones que no se contienen una a la otra no se tocan
function overlap(root: Element): Problem | null {
  const regions = [...root.querySelectorAll('[data-region]')]
  for (let i = 0; i < regions.length; i++) {
    for (let j = i + 1; j < regions.length; j++) {
      const a = regions[i]!
      const b = regions[j]!
      if (a.contains(b) || b.contains(a)) continue
      const p = box(a)
      const q = box(b)
      const width = Math.min(p.right, q.right) - Math.max(p.left, q.left)
      const height = Math.min(p.bottom, q.bottom) - Math.max(p.top, q.top)
      if (width > TOLERANCE && height > TOLERANCE) return { code: 'SLIDE_OVERLAP', detail: `${describe(a)}/${describe(b)}` }
    }
  }
  return null
}

// ningún texto visible baja del mínimo del público
function tooSmall(root: Element, min: number): Problem | null {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (!node.textContent?.trim()) continue
    const parent = node.parentElement
    if (!parent) continue
    const size = parseFloat(getComputedStyle(parent).fontSize)
    if (size < min - 0.01) return { code: 'TEXT_TOO_SMALL', detail: `${size}px` }
  }
  return null
}

// cada imagen cargó y se pinta sin deformarse: con `object-fit: cover` se recorta, nunca se estira
function images(root: Element): Problem | null {
  for (const img of root.querySelectorAll('img')) {
    if (!img.complete || img.naturalWidth === 0) return { code: 'ASSET_INVALID', detail: 'img' }
    const fit = getComputedStyle(img).objectFit
    const rendered = box(img)
    const natural = img.naturalWidth / img.naturalHeight
    const shown = rendered.width / rendered.height
    if (fit !== 'cover' && fit !== 'contain' && Math.abs(natural - shown) / natural > 0.01) return { code: 'ASSET_INVALID', detail: 'aspect' }
  }
  return null
}

// fuera del pie, la lámina dice algo
function empty(root: Element): Problem | null {
  const content = root.querySelector('[data-content]')
  const textLength = content?.textContent?.trim().length ?? 0
  const marks = content?.querySelectorAll('img, svg path, svg rect, svg circle').length ?? 0
  return textLength === 0 && marks === 0 ? { code: 'SLIDE_EMPTY', detail: 'content' } : null
}

export function verify(root: Element, minFont: number): Problem | null {
  return empty(root) ?? images(root) ?? tooSmall(root, minFont) ?? overflow(root) ?? overlap(root)
}
