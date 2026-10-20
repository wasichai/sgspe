import { createContext, useContext } from 'react'
import type { AssetSpec, DeckSpec } from './spec/schema.ts'
import { TONES, type Tone } from './design/tokens.ts'

// lo que toda lámina sabe de su presentación: los metadatos, el tema, el tono del público, sus imágenes y su lugar
export type DeckContext = {
  meta: DeckSpec['meta']
  theme: DeckSpec['theme']
  tone: Tone
  assets: Record<string, AssetSpec>
  index: number
  total: number
}

const Context = createContext<DeckContext | null>(null)

export const DeckProvider = Context.Provider

export function useDeck(): DeckContext {
  const deck = useContext(Context)
  if (!deck) throw new Error('Lámina fuera de una presentación')
  return deck
}

export function deckContext(spec: Pick<DeckSpec, 'meta' | 'theme' | 'assets'>, index: number, total: number): DeckContext {
  return { meta: spec.meta, theme: spec.theme, tone: TONES[spec.meta.audience], assets: spec.assets, index, total }
}

// el `src` de una imagen incrustada: siempre un data URI de la propia especificación, nunca una URL
export function assetSource(asset: AssetSpec): string {
  return `data:${asset.mime};base64,${asset.data}`
}
