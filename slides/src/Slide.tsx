import { useEffect, useRef, useState, type FC } from 'react'
import { cancelRender, continueRender, delayRender, getRemotionEnvironment, useCurrentFrame } from 'remotion'
import { verify } from './checks.ts'
import { FPS, SLIDE_FRAMES } from './timing.ts'
import { DeckProvider, deckContext } from './context.tsx'
import { fontsReady, loadFonts } from './fonts.ts'
import { LAYOUTS, type LayoutProps } from './layouts/index.ts'
import type { DeckSpec, Layout, SlideSpec } from './spec/schema.ts'

// lo que recibe la composición de una lámina: los datos comunes de la presentación (sin las demás láminas) y su lugar
export type SlideProps = {
  deck: Pick<DeckSpec, 'meta' | 'theme' | 'assets'>
  slide: SlideSpec
  index: number
  total: number
  // la hoja de contacto compone las láminas sin verificarlas: cada una ya se verificó sola
  verifyLayout?: boolean
}

// cuántos fotogramas dura una lámina (a 30 por segundo): su último fotograma es el estado completo, el que va al PDF
export { FPS, SLIDE_FRAMES }

// el prefijo del error con que una lámina cancela la composición: el servicio lo lee y responde con su código (§16.7)
export const FAILURE = 'SGSPE'

export function failure(code: string, slide: string, detail = ''): Error {
  return new Error(`${FAILURE}:${code}:${slide}:${detail}`)
}

function Layout<L extends Layout>({ slide }: { slide: SlideSpec & { data: { layout: L } } }) {
  const View = LAYOUTS[slide.data.layout] as FC<LayoutProps<L>>
  return <View slide={slide as LayoutProps<L>['slide']} />
}

// una lámina: espera las fuentes y, al componer el último fotograma, la verifica antes de soltar la captura
export const SlideView: FC<SlideProps> = ({ deck, slide, index, total, verifyLayout = true }) => {
  const root = useRef<HTMLDivElement>(null)
  const released = useRef(false)
  const [handle] = useState(() => delayRender(`lámina ${slide.id}`))
  const frame = useCurrentFrame()
  const context = deckContext(deck, index, total)
  const last = frame === SLIDE_FRAMES - 1
  useEffect(() => {
    let alive = true
    ;(async () => {
      await loadFonts()
      if (!fontsReady()) throw failure('FONT_MISSING', slide.id)
      await new Promise((resolve) => requestAnimationFrame(resolve))
      const element = root.current
      if (element) await Promise.all([...element.querySelectorAll('img')].map((img) => img.decode().catch(() => undefined)))
      if (alive && element && last && verifyLayout && getRemotionEnvironment().isRendering) {
        const problem = verify(element, context.tone.type.min)
        if (problem) throw failure(problem.code, slide.id, problem.detail)
      }
      if (!released.current) {
        released.current = true
        continueRender(handle)
      }
    })().catch((error: unknown) => cancelRender(error instanceof Error ? error : new Error(String(error))))
    return () => {
      alive = false
    }
  }, [handle, slide.id, last, verifyLayout, context.tone.type.min])
  return (
    <DeckProvider value={context}>
      <div ref={root} style={{ position: 'absolute', inset: 0 }}>
        <Layout slide={slide} />
      </div>
    </DeckProvider>
  )
}
