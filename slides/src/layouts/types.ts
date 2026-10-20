import type { DataOf, Layout, SlideSpec } from '../spec/schema.ts'

// lo que recibe un layout: su lámina, con los datos de su tipo
export type LayoutProps<L extends Layout> = { slide: SlideSpec & { data: DataOf<L> } }
