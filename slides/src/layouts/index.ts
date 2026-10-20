import type { FC } from 'react'
import type { Layout } from '../spec/schema.ts'
import { Comparison, Matrix, Trend } from './charts.tsx'
import { Conclusions, Glossary, KeyFigures, MapView, Method, Photos, Quote, Scenarios, Sources, Summary, Table, Thesis, Timeline } from './content.tsx'
import { Closing, Cover, Section } from './frames.tsx'
import type { LayoutProps } from './types.ts'

export type { LayoutProps } from './types.ts'

// la biblioteca de layouts (deliverables.md §16.6): un componente chico por layout, elegido por nombre. agregar uno es sumarlo
// al esquema y a este registro; ninguno conoce a los demás
export const LAYOUTS: { [L in Layout]: FC<LayoutProps<L>> } = {
  cover: Cover,
  section: Section,
  thesis: Thesis,
  summary: Summary,
  figures: KeyFigures,
  trend: Trend,
  comparison: Comparison,
  map: MapView,
  quote: Quote,
  timeline: Timeline,
  matrix: Matrix,
  table: Table,
  method: Method,
  scenarios: Scenarios,
  conclusions: Conclusions,
  sources: Sources,
  glossary: Glossary,
  photos: Photos,
  closing: Closing
}
