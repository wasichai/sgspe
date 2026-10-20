import type { CSSProperties, ReactNode } from 'react'
import { useDeck } from '../context.tsx'
import { FONT, INK } from '../design/tokens.ts'
import type { FigureSpec } from '../spec/schema.ts'

// el texto corrido del público: serif para investigación, sans para los demás; nunca más ancho que una línea cómoda
export function Body({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  const { tone } = useDeck()
  return (
    <p
      style={{
        fontFamily: tone.bodyFont,
        fontSize: tone.type.body,
        lineHeight: tone.bodyFont === FONT.serif ? 1.42 : 1.36,
        color: INK.ink,
        margin: 0,
        maxWidth: '62ch',
        ...style
      }}
    >
      {children}
    </p>
  )
}

export function Lead({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  const { tone } = useDeck()
  return (
    <p style={{ fontFamily: FONT.sans, fontSize: tone.type.lead, lineHeight: 1.28, color: INK.secondary, margin: 0, maxWidth: '44ch', ...style }}>{children}</p>
  )
}

// el rótulo de un grupo de contenido (una columna, una leyenda): sans semibold, en minúsculas de oración
export function Label({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  const { tone } = useDeck()
  return (
    <p style={{ fontFamily: FONT.sans, fontWeight: 600, fontSize: tone.type.small, lineHeight: 1.25, color: INK.secondary, margin: 0, ...style }}>{children}</p>
  )
}

export function Small({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  const { tone } = useDeck()
  return <p style={{ fontFamily: FONT.sans, fontSize: tone.type.small, lineHeight: 1.3, color: INK.muted, margin: 0, ...style }}>{children}</p>
}

// una cifra grande: su texto ya formateado; si se reservó o no hay dato, lo dice en gris y más chico, nunca un cero
export function FigureText({ figure, size, color }: { figure: FigureSpec; size?: number; color?: string }) {
  const { tone } = useDeck()
  const fontSize = size ?? tone.type.figure
  const absent = figure.reserved || figure.value === null
  return (
    <span
      style={{
        fontFamily: FONT.sans,
        fontWeight: absent ? 400 : 600,
        fontStyle: absent ? 'italic' : 'normal',
        fontSize: absent ? Math.round(fontSize * 0.42) : fontSize,
        lineHeight: absent ? 1.9 : 1,
        letterSpacing: absent ? 0 : '-0.02em',
        // sobre el acento (el cartel), lo ausente va en el color del cartel, atenuado; sobre el papel, en el gris de lo ausente
        color: absent ? (color ?? INK.absent) : (color ?? INK.ink),
        opacity: absent && color ? 0.8 : 1,
        display: 'block',
        whiteSpace: 'nowrap'
      }}
    >
      {figure.display}
    </span>
  )
}
