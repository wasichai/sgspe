import { Page, bodyHeight, useReveal } from '../components/Page.tsx'
import { Body, Small } from '../components/Text.tsx'
import { useDeck } from '../context.tsx'
import { FONT, INK, SPACE, TONE, span } from '../design/tokens.ts'
import type { LayoutProps } from './types.ts'

// las marcas de los gráficos (dataviz): líneas finas, extremos redondeados apoyados en la base, un anillo del color del papel
// alrededor de los puntos, rejilla tenue y rótulos en tinta, nunca en el color de la serie. el tamaño va a la escala de una
// lámina de 1920 px, que se proyecta: el doble de lo que se usa en pantalla
const LINE = 5
const DOT = 8
const RING = 3

// las series de un gráfico de líneas, en orden fijo: el acento, el cobre y la pizarra; nunca más de tres
function seriesColors(accent: string): string[] {
  return [accent, INK.focus, INK.secondary]
}

// una escala de marcas «redondas» entre el mínimo y el máximo (1, 2, 2.5 o 5 por potencia de 10)
export function ticks(min: number, max: number, count = 4): number[] {
  const raw = (max - min) / count
  const power = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * power).find((s) => s >= raw) ?? raw
  const first = Math.ceil(min / step) * step
  const out: number[] = []
  for (let v = first; v <= max + step * 1e-9; v += step) out.push(Number(v.toFixed(10)))
  return out
}

// el texto de una marca del eje, con el formato de ReportFormat (coma de miles, punto decimal), sin depender del ICU del
// navegador: el Chrome del compositor no trae es-PE (pico del 2026-10-20)
export function tickText(value: number): string {
  const decimals = Number.isInteger(value) ? 0 : Math.min(2, (String(value).split('.')[1] ?? '').length)
  const [whole, fraction] = Math.abs(value).toFixed(decimals).split('.')
  const grouped = whole!.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return `${value < 0 ? '-' : ''}${grouped}${fraction ? `.${fraction}` : ''}`
}

// la evolución en el tiempo: una línea por serie, huecos donde no hay dato, el número de casos bajo cada mes
export function Trend({ slide }: LayoutProps<'trend'>) {
  const { tone } = useDeck()
  // sin ejes (comunidad), una sola serie se lee mejor como columnas con su valor encima
  return !tone.axes && slide.data.series.length === 1 ? <Columns slide={slide} /> : <Lines slide={slide} />
}

function Columns({ slide }: LayoutProps<'trend'>) {
  const { theme, tone } = useDeck()
  const data = slide.data
  const series = data.series[0]!
  const grow = useReveal(10, 22)
  const width = span(12)
  const height = bodyHeight(tone, slide) - Math.ceil(tone.type.small * 1.3) - 8
  const top = 64
  const bottom = 64
  const plotH = height - top - bottom
  const slot = width / data.categories.length
  const thick = Math.min(120, slot * 0.56)
  const min = Number(data.min)
  const max = Number(data.max)
  // una escala que empieza en el mínimo del rango (la confianza va de 1 a 5): la columna mide lo que sube desde ahí
  const h = (v: number) => (plotH * (v - min)) / (max - min)
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <Small style={{ color: INK.secondary }}>{data.unit}</Small>
      <svg data-region width={width} height={height} role="img" aria-label={slide.title}>
        <line x1={0} x2={width} y1={top + plotH} y2={top + plotH} stroke={INK.rule} strokeWidth={2} />
        {data.categories.map((category, i) => {
          const value = series.values[i]
          const cx = slot * i + slot / 2
          const label = series.labels[i]
          return (
            <g key={i}>
              {value === null || value === undefined ? (
                <text x={cx} y={top + plotH - 20} textAnchor="middle" fontFamily={FONT.sans} fontStyle="italic" fontSize={tone.type.min} fill={INK.absent}>
                  {label ?? 'sin dato'}
                </text>
              ) : (
                <>
                  <path
                    d={`M${cx - thick / 2},${top + plotH} V${top + plotH - h(Number(value)) * grow + 6} q0,-6 6,-6 H${cx + thick / 2 - 6} q6,0 6,6 V${top + plotH} Z`}
                    fill={theme.accent}
                  />
                  <text
                    x={cx}
                    y={top + plotH - h(Number(value)) * grow - 18}
                    textAnchor="middle"
                    fontFamily={FONT.sans}
                    fontWeight={700}
                    fontSize={tone.type.lead}
                    fill={INK.ink}
                  >
                    {label}
                  </text>
                </>
              )}
              <text x={cx} y={height - 16} textAnchor="middle" fontFamily={FONT.sans} fontSize={tone.type.small} fill={INK.secondary}>
                {category}
              </text>
            </g>
          )
        })}
      </svg>
    </Page>
  )
}

function Lines({ slide }: LayoutProps<'trend'>) {
  const { theme, tone } = useDeck()
  const data = slide.data
  const draw = useReveal(10, 26)
  const width = span(data.reading ? 9 : 12) - (data.reading ? 0 : span(3) + 32)
  const height = Math.min(600, bodyHeight(tone, slide))
  const pad = { left: tone.axes ? 96 : 24, right: 140, top: 36, bottom: data.series.some((s) => s.n) ? 104 : 64 }
  const min = Number(data.min)
  const max = Number(data.max)
  const plotW = width - pad.left - pad.right
  const plotH = height - pad.top - pad.bottom
  const x = (i: number) => pad.left + (data.categories.length === 1 ? plotW / 2 : (plotW * i) / (data.categories.length - 1))
  const y = (v: number) => pad.top + plotH * (1 - (v - min) / (max - min))
  const colors = seriesColors(theme.accent)
  const n = data.series[0]?.n
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', gap: 32, flex: 1 }}>
        <svg data-region width={width} height={height} role="img" aria-label={slide.title} style={{ overflow: 'visible' }}>
          <defs>
            <clipPath id={`draw-${slide.id}`}>
              <rect x={0} y={0} width={pad.left + plotW * draw + DOT * 2} height={height} />
            </clipPath>
          </defs>
          {tone.axes
            ? ticks(min, max).map((t) => (
                <g key={t}>
                  <line x1={pad.left} x2={width - pad.right + 24} y1={y(t)} y2={y(t)} stroke={INK.faint} strokeWidth={1.5} />
                  <text
                    x={pad.left - 20}
                    y={y(t)}
                    dy="0.35em"
                    textAnchor="end"
                    fontFamily={FONT.sans}
                    fontSize={tone.type.min}
                    fill={INK.muted}
                    style={{ fontVariantNumeric: 'tabular-nums' }}
                  >
                    {tickText(t)}
                  </text>
                </g>
              ))
            : null}
          <line x1={pad.left} x2={width - pad.right + 24} y1={pad.top + plotH} y2={pad.top + plotH} stroke={INK.rule} strokeWidth={2} />
          {data.categories.map((category, i) => {
            const missing = data.series.every((s) => s.values[i] === null)
            return (
              <g key={i}>
                <text
                  x={x(i)}
                  y={pad.top + plotH + 40}
                  textAnchor="middle"
                  fontFamily={FONT.sans}
                  fontSize={tone.type.min}
                  fill={missing ? INK.absent : INK.secondary}
                >
                  {category}
                </text>
                {n ? (
                  <text x={x(i)} y={pad.top + plotH + 76} textAnchor="middle" fontFamily={FONT.sans} fontSize={tone.type.min} fill={INK.muted}>
                    {n[i] === null || n[i] === undefined ? 'sin dato' : `n = ${n[i]}`}
                  </text>
                ) : missing ? (
                  <text
                    x={x(i)}
                    y={pad.top + plotH - 16}
                    textAnchor="middle"
                    fontFamily={FONT.sans}
                    fontStyle="italic"
                    fontSize={tone.type.min}
                    fill={INK.absent}
                  >
                    sin dato
                  </text>
                ) : null}
              </g>
            )
          })}
          <g clipPath={`url(#draw-${slide.id})`}>
            {data.series.map((series, s) => {
              const runs: string[] = []
              let current = ''
              series.values.forEach((v, i) => {
                if (v === null) {
                  if (current) runs.push(current)
                  current = ''
                } else current += `${current ? 'L' : 'M'}${x(i).toFixed(1)},${y(Number(v)).toFixed(1)}`
              })
              if (current) runs.push(current)
              return (
                <g key={s}>
                  {runs.map((d, r) => (
                    <path key={r} d={d} fill="none" stroke={colors[s]} strokeWidth={LINE} strokeLinejoin="round" strokeLinecap="round" />
                  ))}
                  {series.values.map((v, i) =>
                    v === null ? null : <circle key={i} cx={x(i)} cy={y(Number(v))} r={DOT} fill={colors[s]} stroke={INK.paper} strokeWidth={RING} />
                  )}
                </g>
              )
            })}
          </g>
          {data.series.map((series, s) => {
            // el rótulo directo: el último valor de la serie o, sin ejes, cada valor
            const shown = series.values.map((v, i) => ({ v, i, label: series.labels[i] })).filter((p) => p.v !== null && p.label)
            const labelled = tone.axes ? shown.slice(-1) : shown
            return labelled.map((p) => (
              <text
                key={`${s}-${p.i}`}
                x={x(p.i) + (tone.axes ? 22 : 0)}
                y={y(Number(p.v)) + (tone.axes ? 0 : -24)}
                dy={tone.axes ? '0.35em' : 0}
                textAnchor={tone.axes ? 'start' : 'middle'}
                fontFamily={FONT.sans}
                fontWeight={600}
                fontSize={tone.type.small}
                fill={INK.ink}
                opacity={draw}
              >
                {p.label}
              </text>
            ))
          })}
        </svg>
        <div data-region style={{ width: span(3), display: 'flex', flexDirection: 'column', gap: SPACE.l }}>
          {data.series.length > 1 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: SPACE.s }}>
              {data.series.map((series, s) => (
                <div key={s} style={{ display: 'flex', alignItems: 'center', gap: SPACE.s }}>
                  <div style={{ width: 36, height: LINE, background: colors[s], borderRadius: 3, flexShrink: 0 }} />
                  <Small style={{ color: INK.ink }}>{series.name}</Small>
                </div>
              ))}
            </div>
          ) : null}
          <Small style={{ color: INK.secondary }}>{data.unit}</Small>
          {data.reading ? <Body>{data.reading}</Body> : null}
        </div>
      </div>
    </Page>
  )
}

// la comparación entre localidades o grupos: barras horizontales desde una sola base, el valor en la punta y una sola barra
// en foco; lo reservado o sin dato se escribe, no se dibuja
export function Comparison({ slide }: LayoutProps<'comparison'>) {
  const { theme, tone } = useDeck()
  const data = slide.data
  const grow = useReveal(10, 22)
  const labelW = span(3)
  const barsW = span(data.reading ? 5 : 7)
  const unitH = data.reading ? 0 : Math.ceil(tone.type.small * 1.3) + SPACE.s
  const rowH = Math.min(84, Math.floor((bodyHeight(tone, slide) - unitH) / data.items.length))
  const thick = Math.min(40, Math.round(rowH * 0.5))
  const max = Number(data.max)
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', gap: 32, flex: 1 }}>
        <div data-region style={{ display: 'flex', flexDirection: 'column', width: labelW + 32 + barsW + 140 }}>
          {data.reading ? null : <Small style={{ color: INK.secondary, marginLeft: labelW + 32, marginBottom: SPACE.s }}>{data.unit}</Small>}
          {data.items.map((item, i) => {
            const absent = item.figure.reserved || item.figure.value === null
            const length = absent ? 0 : (barsW * Number(item.figure.value)) / max
            return (
              <div key={i} style={{ display: 'flex', alignItems: 'center', height: rowH, gap: 32 }}>
                <p style={{ width: labelW, fontFamily: FONT.sans, fontSize: tone.type.small, lineHeight: 1.15, color: INK.ink, margin: 0, textAlign: 'right' }}>
                  {item.label}
                </p>
                <div style={{ display: 'flex', alignItems: 'center', gap: SPACE.s, borderLeft: `2px solid ${INK.rule}`, height: rowH }}>
                  {absent ? (
                    <span style={{ fontFamily: FONT.sans, fontStyle: 'italic', fontSize: tone.type.min, color: INK.absent, paddingLeft: SPACE.s }}>
                      {item.figure.display}
                    </span>
                  ) : (
                    <>
                      <div
                        style={{
                          width: Math.max(2, length * grow),
                          height: thick,
                          background: item.focus ? INK.focus : theme.accent,
                          borderRadius: '0 4px 4px 0'
                        }}
                      />
                      <span
                        style={{
                          fontFamily: FONT.sans,
                          fontWeight: 600,
                          fontSize: tone.type.small,
                          color: INK.ink,
                          whiteSpace: 'nowrap',
                          opacity: grow,
                          fontVariantNumeric: 'tabular-nums'
                        }}
                      >
                        {item.figure.display}
                      </span>
                    </>
                  )}
                </div>
              </div>
            )
          })}
        </div>
        {data.reading ? (
          <div data-region style={{ width: span(3), display: 'flex', flexDirection: 'column', gap: SPACE.m, paddingTop: SPACE.s }}>
            <Small style={{ color: INK.secondary }}>{data.unit}</Small>
            <Body>{data.reading}</Body>
          </div>
        ) : null}
      </div>
    </Page>
  )
}

// la matriz de probabilidad por impacto (o de compromisos): celdas con su nivel y su conteo, la leyenda con rótulos
export function Matrix({ slide }: LayoutProps<'matrix'>) {
  const { tone } = useDeck()
  const data = slide.data
  const appear = useReveal(8, 20)
  // la fila de impactos y el rótulo del eje ocupan unos 110 px bajo la matriz
  const cell = Math.min(104, Math.floor((bodyHeight(tone, slide) - 110) / data.rows.length) - 4)
  const gap = 4
  return (
    <Page title={slide.title} chapter={slide.chapter} source={slide.source} note={slide.note}>
      <div style={{ display: 'flex', gap: span(1), flex: 1 }}>
        <div data-region style={{ display: 'flex', gap: SPACE.m, alignItems: 'center' }}>
          <div style={{ width: 40, display: 'flex', justifyContent: 'center' }}>
            <p
              style={{
                transform: 'rotate(-90deg)',
                whiteSpace: 'nowrap',
                fontFamily: FONT.sans,
                fontWeight: 600,
                fontSize: tone.type.min,
                color: INK.secondary,
                margin: 0
              }}
            >
              {data.rowAxis}
            </p>
          </div>
          <div>
            {data.rows.map((row, r) => (
              <div key={r} style={{ display: 'flex', alignItems: 'center', gap }}>
                <p
                  style={{ width: 140, textAlign: 'right', paddingRight: SPACE.s, fontFamily: FONT.sans, fontSize: tone.type.min, color: INK.muted, margin: 0 }}
                >
                  {row}
                </p>
                {data.cells[r]!.map((c, k) => {
                  // una celda vacía se atenúa y no lleva texto: el ojo va a las que tienen casos
                  const empty = !c.figure.reserved && (c.figure.value === null || Number(c.figure.value) === 0)
                  return (
                    <div
                      key={k}
                      style={{
                        width: cell,
                        height: cell,
                        marginBottom: gap,
                        background: TONE[c.tone].fill,
                        borderRadius: 4,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        opacity: empty ? 0.32 : 0.25 + 0.75 * appear
                      }}
                    >
                      {empty ? null : (
                        <span
                          style={{
                            fontFamily: FONT.sans,
                            fontWeight: 700,
                            fontSize: c.figure.reserved ? tone.type.min : tone.type.lead,
                            color: TONE[c.tone].ink,
                            fontVariantNumeric: 'tabular-nums'
                          }}
                        >
                          {c.figure.display}
                        </span>
                      )}
                    </div>
                  )
                })}
              </div>
            ))}
            <div style={{ display: 'flex', gap, marginLeft: 140 + gap }}>
              {data.columns.map((column, k) => (
                <p key={k} style={{ width: cell, textAlign: 'center', fontFamily: FONT.sans, fontSize: tone.type.min, color: INK.muted, margin: 0 }}>
                  {column}
                </p>
              ))}
            </div>
            <p
              style={{
                marginLeft: 140,
                marginTop: SPACE.s,
                textAlign: 'center',
                width: data.columns.length * (cell + gap),
                fontFamily: FONT.sans,
                fontWeight: 600,
                fontSize: tone.type.min,
                color: INK.secondary
              }}
            >
              {data.columnAxis}
            </p>
          </div>
        </div>
        <div data-region style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: SPACE.m }}>
          {data.legend.map((item) => (
            <div key={item.tone} style={{ display: 'flex', alignItems: 'center', gap: SPACE.s }}>
              <div style={{ width: 32, height: 32, borderRadius: 4, background: TONE[item.tone].fill, flexShrink: 0 }} />
              <Small style={{ color: INK.ink }}>{item.label}</Small>
            </div>
          ))}
          {data.reading ? <Body style={{ marginTop: SPACE.m }}>{data.reading}</Body> : null}
        </div>
      </div>
    </Page>
  )
}
