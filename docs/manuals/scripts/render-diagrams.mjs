#!/usr/bin/env node
// Renderiza los diagramas Mermaid de assets/diagrams/*.mmd a SVG (para el HTML) y PNG a 3x (para el PDF, porque
// prawn-svg no dibuja los foreignObject con que Mermaid escribe las etiquetas). Usa el Chromium de Playwright y el
// mermaid.min.js de node_modules: no descarga nada en tiempo de construcción.
//
//   node scripts/render-diagrams.mjs            # solo los que cambiaron (por fecha)
//   node scripts/render-diagrams.mjs --force    # todos
//
// Variables: PLAYWRIGHT_CHROMIUM_EXECUTABLE (opcional) apunta a un Chromium ya instalado.
import { readdirSync, readFileSync, statSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, join, basename } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const dir = join(root, 'assets', 'diagrams')
const force = process.argv.includes('--force')
const mermaidJs = readFileSync(join(root, 'node_modules', 'mermaid', 'dist', 'mermaid.min.js'), 'utf8')

// la paleta "Pino y piedra" del portal (sgspe-ui/src/theme/tokens.css), la misma de los manuales
const themeVariables = {
  fontFamily: 'IBM Plex Sans, DejaVu Sans, sans-serif',
  fontSize: '15px',
  primaryColor: '#e3efec',
  primaryBorderColor: '#1d5c52',
  primaryTextColor: '#18211d',
  secondaryColor: '#f3eee2',
  secondaryBorderColor: '#8a5a00',
  tertiaryColor: '#eef0ec',
  tertiaryBorderColor: '#838b85',
  lineColor: '#46514b',
  textColor: '#18211d',
  clusterBkg: '#f9faf8',
  clusterBorder: '#d9ddd6',
  edgeLabelBackground: '#ffffff',
  noteBkgColor: '#fbf0d9',
  noteBorderColor: '#7f5300',
  actorBkg: '#e3efec',
  actorBorder: '#1d5c52',
  signalColor: '#18211d',
  labelBoxBkgColor: '#e3efec'
}

const sources = readdirSync(dir).filter((f) => f.endsWith('.mmd')).sort()
if (sources.length === 0) {
  console.error(`No hay diagramas .mmd en ${dir}`)
  process.exit(2)
}

const pending = sources.filter((f) => {
  const png = join(dir, f.replace(/\.mmd$/, '.png'))
  return force || !existsSync(png) || statSync(png).mtimeMs < statSync(join(dir, f)).mtimeMs
})
if (pending.length === 0) {
  console.log(`Diagramas al día (${sources.length}).`)
  process.exit(0)
}

const browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined })
const page = await browser.newPage({ deviceScaleFactor: 3, viewport: { width: 1400, height: 1000 } })
await page.setContent('<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#fff"><div id="d"></div></body></html>')
await page.addScriptTag({ content: mermaidJs })
await page.evaluate((tv) => {
  window.mermaid.initialize({ startOnLoad: false, theme: 'base', themeVariables: tv, securityLevel: 'strict', flowchart: { curve: 'basis', padding: 14 } })
}, themeVariables)

let failed = 0
for (const file of pending) {
  const name = basename(file, '.mmd')
  const code = readFileSync(join(dir, file), 'utf8')
  try {
    const svg = await page.evaluate(async ({ code, id }) => {
      const { svg } = await window.mermaid.render(id, code)
      document.getElementById('d').innerHTML = svg
      return svg
    }, { code, id: `m-${name}` })
    writeFileSync(join(dir, `${name}.svg`), svg)
    const el = await page.$('#d svg')
    await el.screenshot({ path: join(dir, `${name}.png`), omitBackground: false })
    console.log(`✓ ${name}`)
  } catch (e) {
    failed++
    console.error(`✗ ${name}: ${e.message.split('\n')[0]}`)
  }
}
await browser.close()
process.exit(failed ? 1 : 0)
