import type { FC } from 'react'
import { Composition } from 'remotion'
import { DeckView, deckFrames } from '../Deck.tsx'
import { FPS, SLIDE_FRAMES, SlideView, type SlideProps } from '../Slide.tsx'
import { PAGE } from '../design/tokens.ts'
import { SheetView, sheetSize } from './Sheet.tsx'
import type { DeckSpec } from '../spec/schema.ts'
import sample from '../../fixtures/demo-t1-direccion.json' with { type: 'json' }

// las composiciones del compositor: «Slide», una lámina (el PDF compone una por página), y «Deck», la presentación entera
// (vista previa y, más adelante, el MP4). las props por defecto son la presentación de demostración, para el estudio
export const Root: FC = () => {
  const deck = sample as DeckSpec
  return (
    <>
      <Composition
        id="Slide"
        component={SlideView}
        durationInFrames={SLIDE_FRAMES}
        fps={FPS}
        width={PAGE.width}
        height={PAGE.height}
        defaultProps={{ deck, slide: deck.slides[0]!, index: 0, total: deck.slides.length } satisfies SlideProps}
      />
      <Composition
        id="Deck"
        component={DeckView}
        durationInFrames={deckFrames(deck)}
        fps={FPS}
        width={PAGE.width}
        height={PAGE.height}
        defaultProps={{ deck }}
        calculateMetadata={({ props }) => ({ durationInFrames: deckFrames(props.deck) })}
      />
      <Composition
        id="Sheet"
        component={SheetView}
        durationInFrames={SLIDE_FRAMES}
        fps={FPS}
        {...sheetSize(deck.slides.length)}
        defaultProps={{ deck }}
        calculateMetadata={({ props }) => ({ ...sheetSize(props.deck.slides.length) })}
      />
    </>
  )
}
