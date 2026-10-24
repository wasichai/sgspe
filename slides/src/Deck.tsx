import type { FC } from 'react'
import { Series } from 'remotion'
import { SLIDE_FRAMES, SlideView } from './Slide.tsx'
import type { DeckSpec } from './spec/schema.ts'

// la presentación entera: una lámina tras otra, cada una SLIDE_FRAMES fotogramas. el reproductor de sgspe-ui la recorre
// lámina por lámina; el compositor del PDF usa la composición de una sola lámina, y el del MP4, esta sin verificar las láminas
// (las verificó el PDF de la misma especificación)
export const DeckView: FC<{ deck: DeckSpec; verifyLayout?: boolean }> = ({ deck, verifyLayout = true }) => (
  <Series>
    {deck.slides.map((slide, index) => (
      <Series.Sequence key={slide.id} durationInFrames={SLIDE_FRAMES}>
        <SlideView deck={deck} slide={slide} index={index} total={deck.slides.length} verifyLayout={verifyLayout} />
      </Series.Sequence>
    ))}
  </Series>
)

export const deckFrames = (deck: DeckSpec) => deck.slides.length * SLIDE_FRAMES
