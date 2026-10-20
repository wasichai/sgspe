import { AbsoluteFill } from 'remotion'
import { Field } from '../components/Field.tsx'
import { Fit, Page, PosterFooter, useReveal } from '../components/Page.tsx'
import { Body, FigureText, Label, Lead, Small } from '../components/Text.tsx'
import { assetSource, useDeck } from '../context.tsx'
import { FONT, GRID, INK, PAGE, SPACE, span } from '../design/tokens.ts'
import type { LayoutProps } from './types.ts'

const rule = (color: string = INK.rule, width = 1) => ({ borderTop: `${width}px solid ${color}` })

// la tesis: el hallazgo principal como título, su sustento debajo y, en una fila al pie, hasta tres cifras que lo prueban
export function Thesis({ slide }: LayoutProps<'thesis'>) {
  const { theme, tone } = useDeck()
  const figures = slide.data.figures
  const later = useReveal(14, 16)
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note} titleLines={3} titleSize={Math.round(tone.type.title * 1.2)}>
      {slide.data.support ? <Lead style={{ maxWidth: '52ch', opacity: later }}>{slide.data.support}</Lead> : null}
      {figures.length ? (
        <div style={{ display: 'flex', gap: 32, marginTop: 'auto', opacity: later }}>
          {figures.map((item, i) => (
            <div key={i} style={{ width: span(4), ...rule(i === 0 ? theme.accent : INK.rule, i === 0 ? 3 : 1), paddingTop: SPACE.s }}>
              <FigureText figure={item.figure} size={Math.round(tone.type.figure * 0.72)} color={i === 0 ? theme.accent : INK.ink} />
              <Body style={{ color: INK.secondary, marginTop: SPACE.s, fontFamily: FONT.sans, fontSize: tone.type.small * 1.1 }}>{item.label}</Body>
            </div>
          ))}
        </div>
      ) : null}
    </Page>
  )
}

// el resumen: pocas ideas, una por renglón, separadas por aire y un filete
export function Summary({ slide }: LayoutProps<'summary'>) {
  const { tone } = useDeck()
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', flexDirection: 'column', width: span(10) }}>
        {slide.data.points.map((point, i) => (
          <div key={i} style={{ ...rule(), padding: `${SPACE.m}px 0`, opacity: useReveal(8 + i * 5, 14) }}>
            <p style={{ fontFamily: tone.bodyFont, fontSize: tone.type.lead * 0.9, lineHeight: 1.3, color: INK.ink, margin: 0, maxWidth: '52ch' }}>{point}</p>
          </div>
        ))}
      </div>
    </Page>
  )
}

// las cifras clave: de una a cuatro, grandes, con su rótulo y su detalle; lo reservado o ausente se dice, no se pinta como cero.
// para la comunidad salen como cartel, en blanco sobre el campo de acento, para leerse desde el fondo de un local comunal
export function KeyFigures({ slide }: LayoutProps<'figures'>) {
  const { theme, tone } = useDeck()
  const items = slide.data.items
  const width = span(Math.floor(12 / items.length) - (items.length > 1 ? 0 : 6))
  if (tone.poster) return <PosterFigures slide={slide} />
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', gap: 32, flex: 1, alignItems: 'center' }}>
        {items.map((item, i) => (
          <div key={i} style={{ width, ...rule(i === 0 ? theme.accent : INK.ink, 3), paddingTop: SPACE.m, opacity: useReveal(8 + i * 6, 16) }}>
            <FigureText figure={item.figure} />
            <Body style={{ marginTop: SPACE.m, color: INK.ink, fontFamily: FONT.sans, fontWeight: 600 }}>{item.label}</Body>
            {item.detail ? <Small style={{ marginTop: SPACE.xs }}>{item.detail}</Small> : null}
          </div>
        ))}
      </div>
    </Page>
  )
}

function PosterFigures({ slide }: LayoutProps<'figures'>) {
  const { theme, tone } = useDeck()
  const items = slide.data.items
  return (
    <AbsoluteFill>
      <Field width={PAGE.width} height={PAGE.height} variant={`cifras-${slide.id}`} hills={[{ cx: 1600, cy: 980, rings: 18, step: 34 }]} />
      <div
        data-content
        style={{
          position: 'absolute',
          left: GRID.left,
          right: GRID.right,
          top: GRID.top,
          bottom: 120,
          display: 'flex',
          flexDirection: 'column',
          gap: SPACE.xl
        }}
      >
        <Fit name="titulo" height={Math.ceil(tone.type.title * 1.12 * 2)}>
          <h1 style={{ fontFamily: FONT.display, fontWeight: 600, fontSize: tone.type.title, lineHeight: 1.12, color: theme.onAccent, margin: 0 }}>
            {slide.title}
          </h1>
        </Fit>
        <div data-region style={{ display: 'flex', gap: SPACE.xxl, flex: 1, alignItems: 'center' }}>
          {items.map((item, i) => (
            <div key={i} style={{ flex: 1, opacity: useReveal(8 + i * 6, 16) }}>
              <FigureText figure={item.figure} color={theme.onAccent} size={Math.round(tone.type.figure * 1.3)} />
              <p
                style={{ fontFamily: FONT.sans, fontWeight: 600, fontSize: tone.type.lead, lineHeight: 1.2, color: theme.onAccent, margin: `${SPACE.m}px 0 0` }}
              >
                {item.label}
              </p>
              {item.detail ? (
                <p style={{ fontFamily: FONT.sans, fontSize: tone.type.body, color: theme.onAccent, opacity: 0.85, margin: `${SPACE.xs}px 0 0` }}>
                  {item.detail}
                </p>
              ) : null}
            </div>
          ))}
        </div>
        {slide.source ? (
          <Fit name="fuente" height={40}>
            <p style={{ fontFamily: FONT.sans, fontSize: tone.type.min, color: theme.onAccent, opacity: 0.85, margin: 0 }}>Fuente: {slide.source}</p>
          </Fit>
        ) : null}
      </div>
      <PosterFooter chapter={slide.chapter} />
    </AbsoluteFill>
  )
}

const KIND = { TESTIMONIO: 'Testimonio', PERCEPCION: 'Percepción', OBSERVACION: 'Observación de campo' } as const

// un hallazgo cualitativo: la cita, quién la dijo por su cargo y qué alcance tiene; una percepción no es un consenso (§16.5)
export function Quote({ slide }: LayoutProps<'quote'>) {
  const { theme, tone } = useDeck()
  const enter = useReveal(6, 20)
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', gap: span(1), flex: 1 }}>
        <blockquote style={{ margin: 0, width: span(8), alignSelf: 'center', borderLeft: `6px solid ${theme.accent}`, paddingLeft: SPACE.xl, opacity: enter }}>
          <p
            style={{
              fontFamily: FONT.display,
              fontStyle: 'italic',
              fontWeight: 600,
              fontSize: Math.round(tone.type.lead * 1.45),
              lineHeight: 1.24,
              color: INK.ink,
              margin: 0,
              textWrap: 'pretty'
            }}
          >
            «{slide.data.quote}»
          </p>
          <p style={{ fontFamily: FONT.sans, fontSize: tone.type.body, color: INK.secondary, margin: `${SPACE.l}px 0 0` }}>{slide.data.attribution}</p>
        </blockquote>
        <div style={{ width: span(3), alignSelf: 'center', display: 'flex', flexDirection: 'column', gap: SPACE.s, ...rule(INK.ink, 3), paddingTop: SPACE.s }}>
          <Label style={{ color: INK.ink, fontSize: tone.type.body }}>{KIND[slide.data.kind]}</Label>
          <Small style={{ color: INK.secondary }}>{slide.data.scope}</Small>
        </div>
      </div>
    </Page>
  )
}

function Column({ label, items, accent }: { label: string; items: string[]; accent?: string }) {
  const { tone } = useDeck()
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
      <Label style={{ ...rule(accent ?? INK.ink, 3), paddingTop: SPACE.s, color: INK.ink, fontSize: tone.type.body }}>{label}</Label>
      {items.map((item, i) => (
        <div key={i} style={{ ...rule(), padding: `${SPACE.m}px 0`, marginTop: i === 0 ? SPACE.m : 0 }}>
          <p style={{ fontFamily: tone.bodyFont, fontSize: tone.type.body, lineHeight: 1.34, color: INK.ink, margin: 0 }}>{item}</p>
        </div>
      ))}
    </div>
  )
}

// las conclusiones y lo que sigue, en dos columnas
export function Conclusions({ slide }: LayoutProps<'conclusions'>) {
  const { theme } = useDeck()
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', gap: span(1), flex: 1 }}>
        <Column label="Lo que muestran los datos" items={slide.data.conclusions} accent={theme.accent} />
        {slide.data.nextSteps.length ? <Column label="Próximos pasos" items={slide.data.nextSteps} /> : null}
      </div>
    </Page>
  )
}

// las fuentes, el método y los límites: lo que hay detrás de las cifras y lo que estos datos no dicen
export function Sources({ slide }: LayoutProps<'sources'>) {
  return (
    <Page title={slide.title} chapter={slide.chapter} note={slide.note}>
      <div style={{ display: 'flex', gap: span(1), flex: 1 }}>
        <Column label="Fuentes" items={slide.data.sources} />
        {slide.data.limitations.length ? <Column label="Límites" items={slide.data.limitations} /> : null}
      </div>
    </Page>
  )
}

// el glosario para la comunidad: los términos técnicos que aparecieron, en palabras simples
export function Glossary({ slide }: LayoutProps<'glossary'>) {
  const { tone, theme } = useDeck()
  return (
    <Page title={slide.title} chapter={slide.chapter} note={slide.note}>
      <div style={{ display: 'flex', flexDirection: 'column', width: span(11) }}>
        {slide.data.terms.map((term, i) => (
          <div key={i} style={{ display: 'flex', gap: span(1), ...rule(), padding: `${SPACE.m}px 0` }}>
            <p style={{ width: span(3), fontFamily: FONT.sans, fontWeight: 700, fontSize: tone.type.body, color: theme.accent, margin: 0 }}>{term.term}</p>
            <p style={{ flex: 1, fontFamily: FONT.sans, fontSize: tone.type.body, lineHeight: 1.34, color: INK.ink, margin: 0 }}>{term.meaning}</p>
          </div>
        ))}
      </div>
    </Page>
  )
}

// escenarios documentados lado a lado, sin jerarquía entre ellos
export function Scenarios({ slide }: LayoutProps<'scenarios'>) {
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', gap: 64, flex: 1 }}>
        {slide.data.columns.map((column, i) => (
          <Column key={i} label={column.title} items={column.points} />
        ))}
      </div>
    </Page>
  )
}

// la metodología: los pasos en orden y las cifras de cobertura que la respaldan
export function Method({ slide }: LayoutProps<'method'>) {
  const { theme, tone } = useDeck()
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', gap: 32 }}>
        {slide.data.steps.map((step, i) => (
          <div key={i} style={{ flex: 1, ...rule(theme.accent, 3), paddingTop: SPACE.s }}>
            <p style={{ fontFamily: FONT.display, fontWeight: 600, fontSize: 56, lineHeight: 1, color: theme.accent, margin: 0 }}>{i + 1}</p>
            <Label style={{ color: INK.ink, marginTop: SPACE.s, fontSize: tone.type.body * 0.9 }}>{step.label}</Label>
            <Small style={{ marginTop: SPACE.xs, color: INK.secondary }}>{step.detail}</Small>
          </div>
        ))}
      </div>
      {slide.data.facts.length ? (
        <div style={{ display: 'flex', gap: 32, marginTop: 'auto' }}>
          {slide.data.facts.map((fact, i) => (
            <div key={i} style={{ flex: 1, ...rule(), paddingTop: SPACE.s }}>
              <FigureText figure={fact.figure} size={72} />
              <Small style={{ marginTop: SPACE.xs }}>{fact.label}</Small>
            </div>
          ))}
        </div>
      ) : null}
    </Page>
  )
}

// la cronología del trabajo de campo: una línea vertical con la fecha a la izquierda y lo que pasó a la derecha
export function Timeline({ slide }: LayoutProps<'timeline'>) {
  const { theme, tone } = useDeck()
  const events = slide.data.events
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', flexDirection: 'column', width: span(10), position: 'relative' }}>
        {events.map((event, i) => (
          <div key={i} style={{ display: 'flex', gap: SPACE.l, alignItems: 'baseline', minHeight: 112, opacity: useReveal(6 + i * 4, 12) }}>
            <p
              style={{
                width: span(2),
                textAlign: 'right',
                fontFamily: FONT.sans,
                fontWeight: 600,
                fontSize: tone.type.body,
                color: INK.secondary,
                margin: 0,
                fontVariantNumeric: 'tabular-nums'
              }}
            >
              {event.date}
            </p>
            <div style={{ width: 20, alignSelf: 'stretch', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              <div style={{ width: 20, height: 20, borderRadius: 10, background: theme.accent, marginTop: 12, flexShrink: 0 }} />
              {i < events.length - 1 ? <div style={{ width: 2, flex: 1, background: INK.rule }} /> : null}
            </div>
            <div style={{ flex: 1, paddingBottom: SPACE.s }}>
              <p style={{ fontFamily: FONT.sans, fontWeight: 600, fontSize: tone.type.lead * 0.9, lineHeight: 1.25, color: INK.ink, margin: 0 }}>
                {event.label}
              </p>
              {event.detail ? <Body style={{ color: INK.secondary, fontFamily: FONT.sans, marginTop: SPACE.xs }}>{event.detail}</Body> : null}
            </div>
          </div>
        ))}
      </div>
    </Page>
  )
}

// una tabla corta: sin filetes verticales, cifras a la derecha con dígitos tabulares
export function Table({ slide }: LayoutProps<'table'>) {
  const { tone } = useDeck()
  const { header, rows, align } = slide.data
  const cell = (i: number) => ({
    textAlign: align[i] === 'RIGHT' ? ('right' as const) : ('left' as const),
    fontVariantNumeric: align[i] === 'RIGHT' ? 'tabular-nums' : undefined,
    padding: `${SPACE.m - 4}px ${SPACE.m}px`
  })
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <table style={{ borderCollapse: 'collapse', width: span(header.length <= 3 ? 7 : 10), fontFamily: FONT.sans }}>
        <thead>
          <tr style={{ borderBottom: `2px solid ${INK.ink}` }}>
            {header.map((h, i) => (
              <th key={i} style={{ ...cell(i), fontWeight: 600, fontSize: tone.type.small * 1.1, color: INK.secondary }}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, r) => (
            <tr key={r} style={{ borderBottom: `1px solid ${INK.rule}` }}>
              {row.map((value, i) => (
                <td key={i} style={{ ...cell(i), fontSize: tone.type.body, fontWeight: i === 0 ? 600 : 400, color: INK.ink, lineHeight: 1.3 }}>
                  {value}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </Page>
  )
}

// una o dos fotos sin deformar (se recortan con object-fit), con su leyenda
export function Photos({ slide }: LayoutProps<'photos'>) {
  const { assets } = useDeck()
  const images = slide.data.images.map((ref) => assets[ref]!)
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', gap: 32, flex: 1, minHeight: 0 }}>
        {images.map((image, i) => (
          <img key={i} src={assetSource(image)} alt={image.alt} style={{ flex: 1, minWidth: 0, height: '100%', objectFit: 'cover', display: 'block' }} />
        ))}
      </div>
      <Small style={{ marginTop: SPACE.s, color: INK.secondary }}>{slide.data.caption}</Small>
    </Page>
  )
}

// un mapa congelado del informe, a la izquierda, con su lectura a la derecha
export function MapView({ slide }: LayoutProps<'map'>) {
  const { assets } = useDeck()
  const image = assets[slide.data.image]!
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', gap: span(1), flex: 1, minHeight: 0 }}>
        <img
          src={assetSource(image)}
          alt={image.alt}
          style={{ width: span(8), height: '100%', objectFit: 'contain', objectPosition: 'left top', display: 'block' }}
        />
        {slide.data.reading ? <Body style={{ width: span(3) }}>{slide.data.reading}</Body> : null}
      </div>
    </Page>
  )
}
