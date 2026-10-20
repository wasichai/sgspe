import type { CSSProperties, ReactNode } from 'react'
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion'
import { useDeck } from '../context.tsx'
import { FONT, GRID, INK, PAGE, SPACE, contentWidth, type Tone } from '../design/tokens.ts'

// cuándo termina de entrar cada cosa: todo está quieto mucho antes del último fotograma, que es el que va al PDF
export const SETTLED = 48

// el avance de una entrada (0 a 1) que empieza en `delay` y dura `length` fotogramas
export function useReveal(delay = 0, length = 18): number {
  const frame = useCurrentFrame()
  return interpolate(frame, [delay, delay + length], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' })
}

// una región que no puede desbordar: su contenido se mide contra su caja al componer (RQ-REP-036)
export function Fit({ height, width, children, style, name }: { height: number; width?: number; children: ReactNode; style?: CSSProperties; name?: string }) {
  return (
    <div data-fit data-region data-name={name} style={{ height, width: width ?? '100%', flexShrink: 0, ...style }}>
      <div>{children}</div>
    </div>
  )
}

// el pie sobre el campo de acento (el cartel de cifras): el mismo contenido, en el color sobre el acento
export function PosterFooter({ chapter }: { chapter?: string }) {
  const { theme } = useDeck()
  return <Footer chapter={chapter} ink={theme.onAccent} />
}

// el pie: la parte del arco a la izquierda, la derivación en el centro y la página a la derecha (ASM-143)
function Footer({ chapter, ink }: { chapter?: string; ink?: string }) {
  const { meta, index, total, tone } = useDeck()
  const style: CSSProperties = { fontFamily: FONT.sans, fontSize: tone.type.min, lineHeight: 1.2, color: ink ?? INK.muted }
  return (
    <div
      data-region
      data-name="pie"
      style={{
        position: 'absolute',
        left: GRID.left,
        right: GRID.right,
        bottom: 0,
        height: GRID.footer,
        display: 'flex',
        alignItems: 'center',
        gap: SPACE.l,
        borderTop: `1px solid ${ink ? `${ink}55` : INK.rule}`
      }}
    >
      <span style={{ ...style, color: ink ?? INK.secondary, fontWeight: 600, minWidth: 260 }}>{chapter ?? meta.code}</span>
      <span style={{ ...style, flex: 1 }}>{meta.derivation}</span>
      <span style={{ ...style, fontVariantNumeric: 'tabular-nums' }}>
        {index + 1} de {total}
      </span>
    </div>
  )
}

export function SourceLine({ source, note }: { source?: string; note?: string }) {
  const { tone } = useDeck()
  if (!source && !note) return null
  const style: CSSProperties = { fontFamily: FONT.sans, fontSize: tone.type.min, lineHeight: 1.3, color: INK.muted, margin: 0 }
  return (
    <Fit name="fuente" height={note && source ? 72 : 36}>
      {note ? <p style={style}>Nota: {note}</p> : null}
      {source ? <p style={style}>Fuente: {source}</p> : null}
    </Fit>
  )
}

// el alto del título según su voz y sus líneas
export function titleHeight(tone: Tone, lines = 2, size?: number): number {
  const fontSize = size ?? tone.type.title
  const lineHeight = tone.titleFont === 'sans' ? 1.14 : 1.12
  return Math.ceil(fontSize * lineHeight * lines) + (tone.titleFont === 'sans' ? 28 : 0)
}

// el alto que le queda al cuerpo de una lámina de contenido: los gráficos se dimensionan con él, nunca con una constante
export function bodyHeight(tone: Tone, slide: { source?: string; note?: string }, lines = 2): number {
  const source = slide.source || slide.note ? (slide.source && slide.note ? 72 : 36) + SPACE.l : 0
  return PAGE.height - GRID.top - GRID.footer - SPACE.l - titleHeight(tone, lines) - SPACE.l - source
}

// el título de una lámina: dice el hallazgo cuando los datos lo permiten (§16.5); dos líneas como máximo
export function Title({ children, size, lines = 2 }: { children: ReactNode; size?: number; lines?: number }) {
  const { tone, theme } = useDeck()
  const fontSize = size ?? tone.type.title
  const lineHeight = tone.titleFont === 'sans' ? 1.14 : 1.12
  const height = titleHeight(tone, lines, size)
  const voice: CSSProperties =
    tone.titleFont === 'display'
      ? { fontFamily: FONT.display, fontWeight: 600, letterSpacing: '-0.01em' }
      : tone.titleFont === 'serif'
        ? { fontFamily: FONT.serif, fontWeight: 400, letterSpacing: '-0.005em' }
        : { fontFamily: FONT.sans, fontWeight: 600, letterSpacing: '-0.005em' }
  const ruled = tone.titleFont === 'sans'
  return (
    <Fit name="titulo" height={height} width={contentWidth * 0.86}>
      <h1 style={{ ...voice, fontSize, lineHeight, color: INK.ink, margin: 0, textWrap: 'balance' }}>{children}</h1>
      {ruled ? <div style={{ width: 120, height: 4, background: theme.accent, marginTop: 24 }} /> : null}
    </Fit>
  )
}

// una lámina de contenido: el título arriba, el cuerpo en la retícula, la fuente abajo y el pie
export function Page({
  title,
  chapter,
  source,
  note,
  children,
  titleLines,
  titleSize
}: {
  title: string
  chapter?: string
  source?: string
  note?: string
  children: ReactNode
  titleLines?: number
  titleSize?: number
}) {
  const { tone, theme } = useDeck()
  const enter = useReveal(0, 14)
  return (
    <AbsoluteFill style={{ background: INK.paper }}>
      {tone.band ? <div style={{ position: 'absolute', left: 0, right: 0, top: 0, height: 14, background: theme.accent }} /> : null}
      <div
        data-content
        style={{
          position: 'absolute',
          left: GRID.left,
          top: GRID.top,
          width: contentWidth,
          height: PAGE.height - GRID.top - GRID.footer - SPACE.l,
          display: 'flex',
          flexDirection: 'column',
          gap: SPACE.l
        }}
      >
        <div style={{ opacity: enter, transform: `translateY(${(1 - enter) * 12}px)` }}>
          <Title lines={titleLines} size={titleSize}>
            {title}
          </Title>
        </div>
        <div data-region data-name="cuerpo" style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          {children}
        </div>
        <SourceLine source={source} note={note} />
      </div>
      <Footer chapter={chapter} />
    </AbsoluteFill>
  )
}
