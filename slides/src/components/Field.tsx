import type { CSSProperties, ReactNode } from 'react'
import { useDeck } from '../context.tsx'
import { rings, seedOf, type Hill } from '../design/contours.ts'
import { useReveal } from './Page.tsx'

// el campo de acento con curvas de nivel: la firma de la portada, los separadores y el cierre. es lo único llamativo de la
// presentación; el resto es quieto. las curvas salen del código del entregable y de `variant`, así que cada sección tiene su
// propio relieve y la misma presentación siempre el mismo
export function Field({
  width,
  height,
  variant,
  hills,
  children,
  style
}: {
  width: number
  height: number
  variant: string
  hills: Hill[]
  children?: ReactNode
  style?: CSSProperties
}) {
  const { meta, theme } = useDeck()
  const appear = useReveal(0, 30)
  const seed = seedOf(`${meta.code}/${variant}`)
  return (
    <div style={{ position: 'absolute', left: 0, top: 0, width, height, background: theme.accent, overflow: 'hidden', ...style }}>
      <svg width={width} height={height} style={{ position: 'absolute', inset: 0 }} aria-hidden>
        {hills.map((hill, h) =>
          rings(hill, seed + h * 7919).map((d, i) => (
            <path
              key={`${h}-${i}`}
              d={d}
              fill="none"
              stroke={theme.onAccent}
              strokeOpacity={(0.1 + (i % 5 === 0 ? 0.08 : 0)) * appear}
              strokeWidth={i % 5 === 0 ? 2.25 : 1.25}
            />
          ))
        )}
      </svg>
      {children}
    </div>
  )
}
