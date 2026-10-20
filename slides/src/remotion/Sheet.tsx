import type { FC } from 'react'
import { AbsoluteFill } from 'remotion'
import { SlideView } from '../Slide.tsx'
import { PAGE } from '../design/tokens.ts'
import type { DeckSpec } from '../spec/schema.ts'

export const SHEET_COLUMNS = 4
const SCALE = 0.25
const GAP = 24

export const sheetSize = (count: number) => {
  const rows = Math.ceil(count / SHEET_COLUMNS)
  return { width: GAP + SHEET_COLUMNS * (PAGE.width * SCALE + GAP), height: GAP + rows * (PAGE.height * SCALE + GAP) }
}

// la hoja de contacto de la revisión visual (deliverables.md §16.9): cada lámina a un cuarto de tamaño; se compone en el último
// fotograma, el estado final de cada una
export const SheetView: FC<{ deck: DeckSpec }> = ({ deck }) => (
  <AbsoluteFill style={{ background: '#2B323B' }}>
    {deck.slides.map((slide, index) => (
      <div
        key={slide.id}
        style={{
          position: 'absolute',
          left: GAP + (index % SHEET_COLUMNS) * (PAGE.width * SCALE + GAP),
          top: GAP + Math.floor(index / SHEET_COLUMNS) * (PAGE.height * SCALE + GAP),
          width: PAGE.width,
          height: PAGE.height,
          transform: `scale(${SCALE})`,
          transformOrigin: 'top left',
          overflow: 'hidden'
        }}
      >
        <SlideView deck={deck} slide={slide} index={index} total={deck.slides.length} verifyLayout={false} />
      </div>
    ))}
  </AbsoluteFill>
)
