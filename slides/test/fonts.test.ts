import crypto from 'node:crypto'
import fs from 'node:fs'
import { describe, expect, it } from 'vitest'

// las fuentes vendidas son las de los releases oficiales de Adobe (OFL): source-serif 4.005R y source-sans 3.052R
const EXPECTED: Record<string, string> = {
  'SourceSans3-Bold.ttf': '9214b9d95e4231c609802815c2646c98174e2102d0d37f88978a7f8e71006e6a',
  'SourceSans3-It.ttf': '192afd78f0f54a3c69eaf02d43f4d9a821e9d6110e41d3d25d61a7385cd580e4',
  'SourceSans3-Regular.ttf': '4644c81b86ec9caaa76b634889968ed3c4f4f52f054855933acc7c2b21e53b0f',
  'SourceSans3-Semibold.ttf': 'a3f4f8dcf343a8f24dc61951de93f3ba1558b15cd250ba24af8a40e957081b7d',
  'SourceSerif4-It.ttf': '9d2950a8f1da66e21502c35d646a1d2148e79f9ea43fd2158cf02f5232e7f430',
  'SourceSerif4-Regular.ttf': 'e5a4ee6a3d87bb9024796be390c6771e2a0eb1883dae25effaf57ca01668e24b',
  'SourceSerif4Display-Semibold.ttf': 'ba711c7443224e9c95bc5fcd24a98f3d921e50b3873c27667884351067927a03',
  'SourceSerif4Display-SemiboldIt.ttf': 'd91d4b27ee6e88fbf441b41f07516fbe7b0a9d6272b466b65c2eb2706550bced'
}

describe('las fuentes vendidas', () => {
  it('son exactamente las del release, con su licencia', () => {
    const found = Object.fromEntries(
      fs
        .readdirSync('fonts')
        .filter((f) => f.endsWith('.ttf'))
        .map((f) => [
          f,
          crypto
            .createHash('sha256')
            .update(fs.readFileSync(`fonts/${f}`))
            .digest('hex')
        ])
    )
    expect(found).toEqual(EXPECTED)
    expect(fs.readFileSync('fonts/OFL-SourceSans3.md', 'utf8')).toContain('SIL Open Font License')
    expect(fs.readFileSync('fonts/OFL-SourceSerif4.md', 'utf8')).toContain('SIL Open Font License')
  })
})
