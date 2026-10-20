import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

// la composición es una función de la especificación (RQ-REP-037): ni la fecha, ni el azar, ni el entorno
const files = (dir: string): string[] =>
  fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? files(path.join(dir, e.name)) : [path.join(dir, e.name)]))

describe('las composiciones son deterministas', () => {
  it('src no usa Date, Math.random ni process.env', () => {
    const offenders = files('src')
      .filter((f) => /\.(ts|tsx)$/.test(f))
      .filter((f) => /\bnew Date\b|\bDate\.now\b|Math\.random|process\.env/.test(fs.readFileSync(f, 'utf8')))
    expect(offenders).toEqual([])
  })

  it('src no interpreta HTML ni carga recursos remotos', () => {
    const offenders = files('src').filter((f) => /dangerouslySetInnerHTML|https?:\/\/|fetch\(/.test(fs.readFileSync(f, 'utf8')))
    expect(offenders).toEqual([])
  })
})
