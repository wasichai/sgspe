#!/usr/bin/env node
// Cuenta las acciones primarias visibles en las pantallas del rediseño de UX, sobre la demo VIVA (#157, AC-5): la regla
// de 05-especificacion-de-componentes.md §1 es una sola primaria por pantalla. Corre a mano contra el portal de
// sgspe-ui conectado al servidor y a la base de demostración, fuera de la CI, con los usuarios ficticios que ven cada
// pantalla y a 1440 × 1000 px. Sale con 1 si alguna pantalla tiene más de una primaria visible o no se pudo abrir.
//
//   SGSPE_DEMO_PASSWORD='…' node scripts/count-primaries.live-demo.mjs [--shots <carpeta>]
//
// Una primaria es un `Button` de @wasichai/ui con data-variant="primary" (su variante por defecto) o cualquier botón o
// enlace con el fondo de marca (`bg-brand`), por si alguna pantalla lo dibuja a mano. Con --shots guarda la captura de
// cada pantalla con más de una, para el issue.
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { chromium } from 'playwright'

const UI = (process.env.SGSPE_UI_URL || 'http://localhost:5182').replace(/\/$/, '')
const PASSWORD = process.env.SGSPE_DEMO_PASSWORD
const ADMIN = { email: process.env.SGSPE_ADMIN_EMAIL || 'admin@wasichai.local', password: process.env.SGSPE_ADMIN_PASSWORD || 'admin' }
const args = process.argv.slice(2)
const shots = args.includes('--shots') ? args[args.indexOf('--shots') + 1] : null
if (!PASSWORD) {
  console.error('falta SGSPE_DEMO_PASSWORD (contraseña de los usuarios ficticios de la demostración)')
  process.exit(2)
}

const USERS = {
  social: 'gestion.social.demo@sgspe.test',
  riesgos: 'riesgos.demo@sgspe.test',
  qcs: 'qcs.demo@sgspe.test',
  inversion: 'inversion.demo@sgspe.test'
}
const BAD = [/No se pudo cargar esta información/, /No hubo respuesta del servidor/, /Esta página no existe/, /Su cuenta no tiene permiso/, /No puede abrir/]
const LOADING = /Cargando|Comprobando/
const PRIMARY = 'button[data-variant="primary"], a[data-variant="primary"], button.bg-brand, a.bg-brand'

// ---- API ---------------------------------------------------------------------------------------------------------
const tokens = {}
async function token(email, password = PASSWORD) {
  if (tokens[email]) return tokens[email]
  const res = await fetch(`${UI}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) })
  if (!res.ok) throw new Error(`inicio de sesión de ${email}: ${res.status}`)
  tokens[email] = (await res.json()).token
  return tokens[email]
}
async function find(object, filters, email = USERS.social) {
  const q = new URLSearchParams({ page: '0', size: '1', ...filters })
  const res = await fetch(`${UI}/api/objects/${object}/records?${q}`, { headers: { Authorization: `Bearer ${await token(email)}` } })
  if (!res.ok) throw new Error(`GET ${object}: ${res.status}`)
  const page = await res.json()
  // sin registros (la demo no trae acuerdos), la ficha se informa como no medida en lugar de abortar el recorrido
  return page.content?.[0]?.id ?? null
}

// ---- navegador ---------------------------------------------------------------------------------------------------
const browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined, args: ['--lang=es-PE'] })
const pages = {}
async function pageFor(email, password = PASSWORD) {
  if (pages[email]) return pages[email]
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'es-PE', timezoneId: 'America/Lima', reducedMotion: 'reduce' })
  const page = await context.newPage()
  await page.goto(`${UI}/login`)
  await page.getByLabel('Correo').fill(email)
  await page.getByLabel('Contraseña').fill(password)
  await page.getByRole('button', { name: 'Ingresar' }).click()
  await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 30000 })
  pages[email] = page
  return page
}
async function settle(page) {
  const deadline = Date.now() + 30000
  await page.locator('h1').filter({ visible: true }).first().waitFor({ state: 'visible', timeout: 30000 })
  const loading = page.getByText(LOADING).filter({ visible: true })
  while ((await loading.count()) && Date.now() < deadline) await page.waitForTimeout(500)
  await page.waitForTimeout(900)
  if (await loading.count()) throw new Error(`la pantalla sigue en «${(await loading.first().innerText()).trim()}»`)
  const body = await page.locator('body').innerText()
  for (const re of BAD) if (re.test(body)) throw new Error(`la pantalla muestra «${body.match(re)[0]}»`)
}
// las primarias visibles en el viewport o fuera de él (la regla vale para la pantalla entera, no solo lo que se ve). un
// conmutador (aria-pressed) o una pestaña marcada se pinta con la variante primaria, pero es un estado, no una acción
async function primaries(page) {
  return page
    .locator(PRIMARY)
    .filter({ visible: true })
    .evaluateAll((els) =>
      els.filter((e) => !e.hasAttribute('aria-pressed') && e.getAttribute('role') !== 'tab').map((e) => (e.getAttribute('aria-label') || e.textContent || '').trim())
    )
}

// ---- pantallas ---------------------------------------------------------------------------------------------------
const project = await find('project', { code: 'DEMO-T1' })
const P = `/projects/${project}`
const id = {
  commitment: await find('commitment', { code: 'DEMO-C-03' }),
  agreement: await find('agreement', {}),
  grievance: await find('grievance', { code: 'DEMO-Q-03' }, USERS.qcs),
  risk: await find('social_risk', { code: 'DEMO-R-03' }, USERS.riesgos),
  warning: await find('early_warning', {}, USERS.riesgos),
  engagement: await find('engagement', { engagement_type: 'ASAMBLEA' }),
  contribution: await find('social_contribution', { code: 'DEMO-AP-02' }, USERS.inversion),
  program: await find('social_program', {}, USERS.inversion)
}
const SCREENS = [
  ['Inicio', USERS.social, '/'],
  ['Compromisos', USERS.social, `${P}/commitments`],
  ['Compromiso', USERS.social, `${P}/commitments/${id.commitment}`],
  ['Acuerdos', USERS.social, `${P}/agreements`],
  ['Acuerdo', USERS.social, `${P}/agreements/${id.agreement}`],
  ['QCS', USERS.qcs, `${P}/attention`],
  ['Queja', USERS.qcs, `${P}/grievances/${id.grievance}`],
  ['Riesgos', USERS.riesgos, `${P}/risks`],
  ['Riesgo', USERS.riesgos, `${P}/risks/${id.risk}`],
  ['Alertas', USERS.riesgos, `${P}/risks/warnings`],
  ['Alerta', USERS.riesgos, `${P}/risks/warnings/${id.warning}`],
  ['Relacionamiento', USERS.social, `${P}/engagements`],
  ['Interacción', USERS.social, `${P}/engagements/${id.engagement}`],
  ['Aporte', USERS.inversion, `${P}/contributions/${id.contribution}`],
  // los aportes no tienen lista propia: son pestañas de «Programas sociales»
  ['Programas y aportes', USERS.inversion, `${P}/programs`],
  ['Programa', USERS.inversion, `${P}/programs/${id.program}`],
  ['Project 360', USERS.social, `${P}/overview`],
  ['Bandeja de tareas', USERS.social, '/tasks'],
  ['S51 Revisión ejecutiva', USERS.social, '/dashboards/executive'],
  ['S53 Administración', ADMIN.email, '/administration']
]

let failed = 0
for (const [name, email, path] of SCREENS) {
  if (path.endsWith('/null')) {
    console.log(`- -  ${name.padEnd(24)} sin registro en la demo`)
    continue
  }
  const page = await pageFor(email, email === ADMIN.email ? ADMIN.password : PASSWORD)
  try {
    await page.goto(`${UI}${path}`)
    await settle(page)
    const found = await primaries(page)
    const mark = found.length > 1 ? '✗' : '✓'
    if (found.length > 1) {
      failed++
      if (shots) {
        mkdirSync(shots, { recursive: true })
        await page.screenshot({ path: join(shots, `${name.replace(/\W+/g, '-').toLowerCase()}.png`), fullPage: true })
      }
    }
    console.log(`${mark} ${found.length}  ${name.padEnd(24)} ${path}${found.length ? `  [${found.join(' | ')}]` : ''}`)
  } catch (e) {
    failed++
    console.log(`! -  ${name.padEnd(24)} ${path}  ${e.message}`)
  }
}
await browser.close()
console.log(failed ? `${failed} pantallas con más de una primaria o sin abrir` : 'ninguna pantalla con más de una primaria')
process.exit(failed ? 1 : 0)
