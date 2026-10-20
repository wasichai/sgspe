import { AbsoluteFill } from 'remotion'
import { Field } from '../components/Field.tsx'
import { Fit, useReveal } from '../components/Page.tsx'
import { assetSource, useDeck } from '../context.tsx'
import { FONT, GRID, INK, PAGE, SPACE } from '../design/tokens.ts'
import type { LayoutProps } from './types.ts'

const LEVEL_TEXT = {
  INTERNO: 'Uso interno de la organización',
  EXTERNO: 'Para terceros: personas citadas por cargo o código',
  PUBLICO: 'Público: solo datos agregados'
} as const

// la portada: el campo de acento con el relieve a la izquierda y los datos del informe en el papel, a la derecha
export function Cover({ slide }: LayoutProps<'cover'>) {
  const { meta, theme, tone, assets } = useDeck()
  const data = slide.data
  const fieldWidth = 1240
  const enter = useReveal(6, 20)
  const logo = theme.logo ? assets[theme.logo] : undefined
  const meta2 = [
    data.preparedFor ? { label: 'Preparado para', text: data.preparedFor } : null,
    data.preparedBy ? { label: 'Elaborado por', text: data.preparedBy } : null,
    { label: 'Fecha', text: meta.dateLabel },
    { label: 'Nivel de divulgación', text: LEVEL_TEXT[meta.level] }
  ].filter((x) => x !== null)
  return (
    <AbsoluteFill style={{ background: INK.paper }}>
      <Field
        width={fieldWidth}
        height={PAGE.height}
        variant="portada"
        hills={[
          { cx: 980, cy: 230, rings: 22, step: 34 },
          { cx: 260, cy: 1010, rings: 14, step: 30 }
        ]}
      />
      <div
        data-content
        style={{
          position: 'absolute',
          left: GRID.left,
          top: 0,
          width: fieldWidth - GRID.left - SPACE.xxl,
          height: PAGE.height,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'flex-end',
          paddingBottom: 132,
          gap: SPACE.l,
          opacity: enter,
          transform: `translateY(${(1 - enter) * 16}px)`
        }}
      >
        <Fit name="proyecto" height={44}>
          <p style={{ fontFamily: FONT.sans, fontWeight: 600, fontSize: 36, lineHeight: 1.2, color: theme.onAccent, margin: 0, opacity: 0.86 }}>
            {meta.project}
          </p>
        </Fit>
        <Fit name="titulo" height={Math.ceil(tone.type.display * 1.06 * 3)}>
          <h1
            style={{
              fontFamily: FONT.display,
              fontWeight: 600,
              fontSize: tone.type.display,
              lineHeight: 1.06,
              letterSpacing: '-0.015em',
              color: theme.onAccent,
              margin: 0,
              textWrap: 'balance'
            }}
          >
            {slide.title}
          </h1>
        </Fit>
        {data.subtitle ? (
          <Fit name="subtitulo" height={Math.ceil(tone.type.lead * 1.25 * 3)}>
            <p style={{ fontFamily: FONT.sans, fontSize: tone.type.lead, lineHeight: 1.25, color: theme.onAccent, margin: 0, maxWidth: '40ch' }}>
              {data.subtitle}
            </p>
          </Fit>
        ) : null}
      </div>
      <div
        style={{
          position: 'absolute',
          left: fieldWidth + SPACE.xl,
          right: GRID.right - 24,
          top: GRID.top,
          bottom: 132,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between'
        }}
      >
        <div data-region data-name="logo" style={{ height: 140 }}>
          {logo ? <img src={assetSource(logo)} alt={logo.alt} style={{ maxHeight: 140, maxWidth: '100%', objectFit: 'contain', display: 'block' }} /> : null}
        </div>
        <div data-region data-name="datos" style={{ display: 'flex', flexDirection: 'column', gap: SPACE.l }}>
          {meta2.map((item) => (
            <div key={item.label}>
              <p style={{ fontFamily: FONT.sans, fontSize: tone.type.min, color: INK.muted, margin: 0, lineHeight: 1.3 }}>{item.label}</p>
              <p style={{ fontFamily: FONT.sans, fontWeight: 600, fontSize: 30, color: INK.ink, margin: 0, lineHeight: 1.25 }}>{item.text}</p>
            </div>
          ))}
        </div>
      </div>
      <p
        data-region
        style={{
          position: 'absolute',
          left: GRID.left,
          bottom: 48,
          width: fieldWidth - GRID.left - SPACE.xl,
          fontFamily: FONT.sans,
          fontSize: tone.type.min,
          color: theme.onAccent,
          opacity: 0.8,
          margin: 0
        }}
      >
        {meta.derivation}
      </p>
    </AbsoluteFill>
  )
}

// el separador de sección: el campo de acento entero, el número de la parte y su idea guía
export function Section({ slide }: LayoutProps<'section'>) {
  const { theme, tone } = useDeck()
  const enter = useReveal(4, 18)
  return (
    <AbsoluteFill>
      <Field width={PAGE.width} height={PAGE.height} variant={`seccion-${slide.id}`} hills={[{ cx: 1500, cy: 560, rings: 26, step: 30 }]} />
      <div
        data-content
        style={{
          position: 'absolute',
          left: GRID.left,
          top: 0,
          bottom: 0,
          width: 1180,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: SPACE.l,
          opacity: enter
        }}
      >
        {slide.data.number ? (
          <Fit height={150}>
            <p style={{ fontFamily: FONT.display, fontWeight: 600, fontSize: 140, lineHeight: 1, color: theme.onAccent, opacity: 0.55, margin: 0 }}>
              {slide.data.number}
            </p>
          </Fit>
        ) : null}
        <Fit height={Math.ceil(96 * 1.08 * 2)}>
          <h1 style={{ fontFamily: FONT.display, fontWeight: 600, fontSize: 96, lineHeight: 1.08, color: theme.onAccent, margin: 0, textWrap: 'balance' }}>
            {slide.title}
          </h1>
        </Fit>
        {slide.data.lead ? (
          <Fit height={Math.ceil(tone.type.lead * 1.28 * 3)}>
            <p style={{ fontFamily: FONT.sans, fontSize: tone.type.lead, lineHeight: 1.28, color: theme.onAccent, opacity: 0.92, margin: 0, maxWidth: '42ch' }}>
              {slide.data.lead}
            </p>
          </Fit>
        ) : null}
      </div>
    </AbsoluteFill>
  )
}

// el cierre: el mensaje final en el papel, con el relieve de la portada en una franja
export function Closing({ slide }: LayoutProps<'closing'>) {
  const { tone, meta } = useDeck()
  return (
    <AbsoluteFill style={{ background: INK.paper }}>
      <Field width={PAGE.width} height={300} variant="cierre" hills={[{ cx: 1540, cy: 120, rings: 20, step: 30 }]} />
      <div data-content style={{ position: 'absolute', left: GRID.left, top: 400, width: 1300, display: 'flex', flexDirection: 'column', gap: SPACE.xl }}>
        <Fit height={Math.ceil(tone.type.title * 1.14 * 3)}>
          <h1
            style={{ fontFamily: FONT.display, fontWeight: 600, fontSize: tone.type.title, lineHeight: 1.14, color: INK.ink, margin: 0, textWrap: 'balance' }}
          >
            {slide.title}
          </h1>
        </Fit>
        <Fit height={Math.ceil(tone.type.lead * 1.3 * 3)}>
          <p style={{ fontFamily: FONT.sans, fontSize: tone.type.lead, lineHeight: 1.3, color: INK.secondary, margin: 0, maxWidth: '48ch' }}>
            {slide.data.message}
          </p>
        </Fit>
        {slide.data.contact ? (
          <Fit height={40}>
            <p style={{ fontFamily: FONT.sans, fontWeight: 600, fontSize: 30, color: INK.ink, margin: 0 }}>{slide.data.contact}</p>
          </Fit>
        ) : null}
      </div>
      <p data-region style={{ position: 'absolute', left: GRID.left, bottom: 48, fontFamily: FONT.sans, fontSize: tone.type.min, color: INK.muted, margin: 0 }}>
        {meta.derivation}
      </p>
    </AbsoluteFill>
  )
}
