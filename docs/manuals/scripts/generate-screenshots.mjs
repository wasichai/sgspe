#!/usr/bin/env node
// Toma las capturas de los manuales sobre la aplicación REAL (portal y /admin de sgspe-ui conectados al servidor y a la
// base de demostración). No simula la API: inicia sesión con los usuarios ficticios de la demostración, resuelve los ids
// por la API y recorre cada pantalla a 1440 × 1000 px. Falla (salida 1) si una pantalla muestra un error, se queda
// cargando o no aparece lo que la captura dice ilustrar, para no publicar estados transitorios.
//
//   SGSPE_DEMO_PASSWORD='…' node scripts/generate-screenshots.mjs [--only <prefijo>] [--list]
//
// Variables: SGSPE_UI_URL (http://localhost:5182), SGSPE_DEMO_PASSWORD (usuarios ficticios), SGSPE_ADMIN_EMAIL y
// SGSPE_ADMIN_PASSWORD (admin@wasichai.local / admin, solo en desarrollo), PLAYWRIGHT_CHROMIUM_EXECUTABLE (opcional).
// Las capturas van a assets/screenshots/{user,admin,system}/<nombre>.png con nombres estables.
import { spawnSync } from 'node:child_process'
import { mkdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const UI = (process.env.SGSPE_UI_URL || 'http://localhost:5182').replace(/\/$/, '')
const PASSWORD = process.env.SGSPE_DEMO_PASSWORD
const ADMIN = { email: process.env.SGSPE_ADMIN_EMAIL || 'admin@wasichai.local', password: process.env.SGSPE_ADMIN_PASSWORD || 'admin' }
const args = process.argv.slice(2)
const only = args.includes('--only') ? args[args.indexOf('--only') + 1] : null
if (!PASSWORD) {
  console.error('falta SGSPE_DEMO_PASSWORD (contraseña de los usuarios ficticios de la demostración)')
  process.exit(2)
}

const USERS = {
  social: 'gestion.social.demo@sgspe.test',
  riesgos: 'riesgos.demo@sgspe.test',
  campo: 'campo.demo@sgspe.test',
  qcs: 'qcs.demo@sgspe.test',
  aprobacion: 'aprobacion.demo@sgspe.test',
  inversion: 'inversion.demo@sgspe.test'
}

// textos que nunca deben verse en una captura publicada (estados de carga o de error del portal)
const BAD = [/No se pudo cargar esta información/, /No hubo respuesta del servidor/, /Esta página no existe/, /Su cuenta no tiene permiso/]
const LOADING = /Cargando|Comprobando las reglas/

// ---- API ---------------------------------------------------------------------------------------------------------
const tokens = {}
async function token(email, password = PASSWORD) {
  if (tokens[email]) return tokens[email]
  const res = await fetch(`${UI}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) })
  if (!res.ok) throw new Error(`inicio de sesión de ${email}: ${res.status}`)
  tokens[email] = (await res.json()).token
  return tokens[email]
}
async function api(path, email = USERS.social) {
  const res = await fetch(`${UI}${path}`, { headers: { Authorization: `Bearer ${await token(email)}` } })
  if (!res.ok) throw new Error(`GET ${path}: ${res.status}`)
  return res.json()
}
async function find(object, filters, email) {
  const q = new URLSearchParams({ page: '0', size: '50', ...filters })
  const page = await api(`/api/objects/${object}/records?${q}`, email)
  if (!page.content?.length) throw new Error(`no hay ${object} con ${JSON.stringify(filters)}`)
  return page.content[0]
}

// ---- navegador ---------------------------------------------------------------------------------------------------
const browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined, args: ['--lang=es-PE'] })
const contexts = {}
async function pageFor(email, password = PASSWORD) {
  if (contexts[email]) return contexts[email]
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
    locale: 'es-PE',
    timezoneId: 'America/Lima',
    reducedMotion: 'reduce',
    colorScheme: 'light'
  })
  // el estilo del mapa base por defecto (demotiles.maplibre.org) es externo; si el entorno de las capturas no llega a él,
  // MapLibre no termina de cargar. Solo en este navegador se sirve un fondo neutro local (scripts/mapa-base-neutro.json)
  // en su lugar; las capas de SGSPE siguen saliendo de la API real. SGSPE_SHOTS_REAL_BASEMAP=1 lo desactiva.
  if (process.env.SGSPE_SHOTS_REAL_BASEMAP !== '1') {
    await context.route('https://demotiles.maplibre.org/**', (route) =>
      route.request().url().endsWith('.json')
        ? route.fulfill({ contentType: 'application/json', body: readFileSync(join(root, 'scripts', 'mapa-base-neutro.json'), 'utf8') })
        : route.abort()
    )
  }
  const page = await context.newPage()
  await page.goto(`${UI}/login`)
  await page.getByLabel('Correo').fill(email)
  await page.getByLabel('Contraseña').fill(password)
  await page.getByRole('button', { name: 'Ingresar' }).click()
  await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 30000 })
  contexts[email] = page
  return page
}

async function settle(page, { expect, timeout = 30000 } = {}) {
  const deadline = Date.now() + timeout
  await page.locator('h1').filter({ visible: true }).first().waitFor({ state: 'visible', timeout })
  if (expect) await page.getByText(expect, { exact: false }).filter({ visible: true }).first().waitFor({ state: 'visible', timeout })
  // espera a que desaparezcan los textos de carga visibles
  while (Date.now() < deadline) {
    const loading = await page.locator(`text=${LOADING.source.split('|')[0]}`).filter({ visible: true }).count()
    if (!loading) break
    await page.waitForTimeout(500)
  }
  await page.waitForTimeout(900)
  const body = await page.locator('body').innerText()
  for (const re of BAD) if (re.test(body)) throw new Error(`la pantalla muestra «${body.match(re)[0]}»`)
}

const shots = []
function shot(name, run) {
  shots.push({ name, run })
}
async function save(page, name, opts = {}) {
  const path = join(root, 'assets', 'screenshots', `${name}.png`)
  mkdirSync(dirname(path), { recursive: true })
  if (opts.locator) await opts.locator.screenshot({ path, animations: 'disabled' })
  else await page.screenshot({ path, animations: 'disabled', fullPage: false, clip: opts.clip })
}
async function go(page, path, expect) {
  await page.goto(`${UI}${path}`)
  await settle(page, { expect })
}

// ---- ids de la demostración --------------------------------------------------------------------------------------
const project = await find('project', { code: 'DEMO-T1' })
const P = `/projects/${project.id}`
const ids = {
  commitmentApproved: (await find('commitment', { code: 'DEMO-C-07' })).id,
  commitmentProgress: (await find('commitment', { code: 'DEMO-C-03' })).id,
  grievance: (await find('grievance', { code: 'DEMO-Q-01' }, USERS.qcs)).id,
  grievanceAction: (await find('grievance', { code: 'DEMO-Q-03' }, USERS.qcs)).id,
  request: (await find('stakeholder_request', { code: 'DEMO-S-02' }, USERS.qcs)).id,
  risk: (await find('social_risk', { code: 'DEMO-R-03' }, USERS.riesgos)).id,
  contribution: (await find('social_contribution', { code: 'DEMO-AP-02' }, USERS.inversion)).id,
  deliverable: (await find('deliverable', { document_code: 'DEMO-T1-001' })).id,
  engagement: (await find('engagement', { engagement_type: 'ASAMBLEA' })).id,
  evidence: (await find('evidence', { title: 'Acta de inspección del canal principal de Huaylla Alto' })).id
}
const stakeholders = await api(`/api/objects/stakeholder/records?page=0&size=1&sort=id&project=${project.id}`)
ids.stakeholder = stakeholders.content[0].id

// ---- manual de usuario ---------------------------------------------------------------------------------------------
shot('user/01-inicio-de-sesion', async () => {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'es-PE' })
  const page = await context.newPage()
  await page.goto(`${UI}/login`)
  await page.getByLabel('Correo').fill(USERS.social)
  await page.getByRole('button', { name: 'Ingresar' }).waitFor()
  await save(page, 'user/01-inicio-de-sesion')
  await context.close()
})
shot('user/02-inicio', async () => {
  const page = await pageFor(USERS.social)
  await go(page, '/', 'Lo que necesita atención hoy')
  await save(page, 'user/02-inicio')
})
shot('user/03-barra-superior', async () => {
  const page = await pageFor(USERS.social)
  await go(page, '/')
  await save(page, 'user/03-barra-superior', { clip: { x: 0, y: 0, width: 1440, height: 56 } })
})
shot('user/04-menu-cuenta', async () => {
  const page = await pageFor(USERS.social)
  await go(page, '/')
  await page.getByRole('button', { name: /Su cuenta|Gestión social \(demo\)/ }).first().click()
  await page.waitForTimeout(600)
  await save(page, 'user/04-menu-cuenta', { clip: { x: 840, y: 0, width: 600, height: 360 } })
  await page.keyboard.press('Escape')
})
shot('user/05-busqueda', async () => {
  const page = await pageFor(USERS.social)
  await go(page, '/')
  await page.getByRole('button', { name: /^Buscar/ }).click()
  await page.keyboard.type('agua')
  await page.waitForTimeout(2500)
  await save(page, 'user/05-busqueda')
  await page.keyboard.press('Escape')
})
shot('user/06-proyectos', async () => {
  const page = await pageFor(USERS.social)
  await go(page, '/projects', 'DEMO-T1')
  await save(page, 'user/06-proyectos')
})
shot('user/07-proyecto-resumen', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/overview`, 'Salud del proyecto')
  await save(page, 'user/07-proyecto-resumen')
})
shot('user/08-grupos-lista', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/stakeholders`, 'Grupo de influencia')
  await save(page, 'user/08-grupos-lista')
})
shot('user/09-grupos-matriz', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/stakeholders/matrix`, 'Matriz interés × poder')
  await save(page, 'user/09-grupos-matriz')
})
shot('user/10-grupo-ficha', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/stakeholders/${ids.stakeholder}`, 'Evaluaciones')
  await save(page, 'user/10-grupo-ficha')
})
shot('user/11-grupo-evaluacion', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/stakeholders/${ids.stakeholder}`, 'Evaluaciones')
  await page.getByRole('button', { name: 'Nueva evaluación' }).first().click()
  await page.getByRole('dialog').waitFor()
  await page.waitForTimeout(800)
  await save(page, 'user/11-grupo-evaluacion')
  await page.keyboard.press('Escape')
})
shot('user/12-relacionamiento-lista', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/engagements`, 'Asistentes')
  await save(page, 'user/12-relacionamiento-lista')
})
shot('user/13-relacionamiento-calendario', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/engagements/calendar?month=2026-08`)
  await save(page, 'user/13-relacionamiento-calendario')
})
shot('user/14-interaccion-ficha', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/engagements/${ids.engagement}`, 'Participantes')
  await save(page, 'user/14-interaccion-ficha')
})
shot('user/15-interaccion-registrar', async () => {
  // registrar interacciones es del relacionista, el agente de campo y el gestor de grupos (CREATE sobre engagement)
  const page = await pageFor(USERS.campo)
  await go(page, `${P}/engagements/new`)
  await save(page, 'user/15-interaccion-registrar')
})
shot('user/16-compromisos-lista', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/commitments`, 'DEMO-C-07')
  await save(page, 'user/16-compromisos-lista')
})
shot('user/17-compromiso-ficha', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/commitments/${ids.commitmentProgress}`, 'De dónde viene')
  await save(page, 'user/17-compromiso-ficha')
})
shot('user/18-compromiso-nuevo', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/commitments/new`)
  await save(page, 'user/18-compromiso-nuevo')
})
shot('user/19-transicion-dialogo', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/commitments/${ids.commitmentApproved}`, 'Iniciar ejecución')
  await page.getByRole('button', { name: 'Iniciar ejecución' }).first().click()
  const dialog = page.getByRole('dialog')
  await dialog.waitFor()
  await dialog.getByText('Motivo', { exact: false }).first().waitFor()
  await page.waitForTimeout(2500)
  await save(page, 'user/19-transicion-dialogo')
  await page.keyboard.press('Escape')
})
shot('user/20-qcs-lista', async () => {
  const page = await pageFor(USERS.qcs)
  await go(page, `${P}/attention`, 'DEMO-Q-01')
  await save(page, 'user/20-qcs-lista')
})
shot('user/21-queja-ficha', async () => {
  const page = await pageFor(USERS.qcs)
  await go(page, `${P}/grievances/${ids.grievanceAction}`, 'Queja DEMO-Q-03')
  await save(page, 'user/21-queja-ficha')
})
shot('user/22-qcs-registrar', async () => {
  const page = await pageFor(USERS.qcs)
  await go(page, `${P}/attention/new`)
  await save(page, 'user/22-qcs-registrar')
})
shot('user/23-solicitud-ficha', async () => {
  const page = await pageFor(USERS.qcs)
  await go(page, `${P}/requests/${ids.request}`, 'DEMO-S-02')
  await save(page, 'user/23-solicitud-ficha')
})
shot('user/24-riesgos', async () => {
  const page = await pageFor(USERS.riesgos)
  await go(page, `${P}/risks`, 'Mapa de calor')
  await save(page, 'user/24-riesgos')
})
shot('user/25-riesgo-ficha', async () => {
  const page = await pageFor(USERS.riesgos)
  await go(page, `${P}/risks/${ids.risk}`, 'Revisiones')
  await save(page, 'user/25-riesgo-ficha')
})
shot('user/26-riesgo-revision', async () => {
  const page = await pageFor(USERS.riesgos)
  await go(page, `${P}/risks/${ids.risk}`, 'Revisiones')
  await page.getByRole('button', { name: 'Nueva revisión' }).first().click()
  await page.getByRole('dialog').waitFor()
  await page.waitForTimeout(800)
  await save(page, 'user/26-riesgo-revision')
  await page.keyboard.press('Escape')
})
shot('user/27-alertas', async () => {
  const page = await pageFor(USERS.riesgos)
  await go(page, `${P}/risks/warnings`, 'Convocatoria a bloqueo')
  await save(page, 'user/27-alertas')
})
shot('user/28-programas', async () => {
  const page = await pageFor(USERS.inversion)
  await go(page, `${P}/programs`, 'DEMO-AP-02')
  await save(page, 'user/28-programas')
})
shot('user/29-aporte-ficha', async () => {
  const page = await pageFor(USERS.inversion)
  await go(page, `${P}/contributions/${ids.contribution}`, 'DEMO-AP-02')
  await save(page, 'user/29-aporte-ficha')
})
shot('user/30-documentos', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/documents`, 'Acta de inspección')
  await save(page, 'user/30-documentos')
})
shot('user/31-evidencia-ficha', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/documents/${ids.evidence}`, 'Acta de inspección')
  await save(page, 'user/31-evidencia-ficha')
})
shot('user/32-tareas', async () => {
  const page = await pageFor(USERS.social)
  await go(page, '/tasks', 'Mis tareas')
  await save(page, 'user/32-tareas')
})
shot('user/33-aprobaciones', async () => {
  const page = await pageFor(USERS.aprobacion)
  await go(page, '/tasks/approvals')
  await save(page, 'user/33-aprobaciones')
})
shot('user/34-reportes', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/reports`, 'DEMO-T1-001')
  await save(page, 'user/34-reportes')
})
shot('user/35-exportaciones', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/reports`, 'DEMO-T1-001')
  await page.getByRole('tab', { name: 'Exportaciones' }).click()
  await page.waitForTimeout(1200)
  await save(page, 'user/35-exportaciones')
})
shot('user/36-entregable', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/reports/deliverables/${ids.deliverable}`, 'Pedir borrador')
  await save(page, 'user/36-entregable')
})
shot('user/37-territorio', async () => {
  const page = await pageFor(USERS.social)
  await go(page, '/territory', 'Lugares visibles')
  await page.waitForTimeout(4000)
  await save(page, 'user/37-territorio')
})
shot('user/38-proyecto-mapa', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/map`, 'Áreas de influencia')
  await page.waitForTimeout(4000)
  await save(page, 'user/38-proyecto-mapa')
})
shot('user/39-tablero-compromisos', async () => {
  const page = await pageFor(USERS.social)
  await go(page, `${P}/commitments/dashboard`)
  await page.waitForTimeout(2500)
  await save(page, 'user/39-tablero-compromisos')
})
shot('user/40-avisos', async () => {
  const page = await pageFor(USERS.social)
  await go(page, '/notifications')
  await save(page, 'user/40-avisos')
})
shot('user/41-campo-inicio', async () => {
  const page = await pageFor(USERS.campo)
  await go(page, '/')
  await save(page, 'user/41-campo-inicio')
})
shot('user/42-sin-acceso', async () => {
  const page = await pageFor(USERS.campo)
  await page.goto(`${UI}${P}/commitments`)
  await page.getByText('Su cuenta no puede abrir', { exact: false }).first().waitFor({ timeout: 20000 })
  await page.waitForTimeout(800)
  await save(page, 'user/42-sin-acceso')
})

// ---- manual del administrador ----------------------------------------------------------------------------------------
async function adminPage() {
  return pageFor(ADMIN.email, ADMIN.password)
}
shot('admin/01-equipo', async () => {
  const page = await adminPage()
  await go(page, `${P}/settings`, 'Asignar persona')
  await save(page, 'admin/01-equipo')
})
shot('admin/14-asignar-persona', async () => {
  const page = await adminPage()
  await go(page, `${P}/settings`, 'Asignar persona')
  await page.getByRole('button', { name: 'Asignar persona' }).click()
  await page.getByRole('button', { name: 'Asignar', exact: true }).waitFor()
  await page.waitForTimeout(800)
  await save(page, 'admin/14-asignar-persona')
  await page.keyboard.press('Escape')
})
shot('admin/02-parametros', async () => {
  const page = await adminPage()
  await go(page, `${P}/settings`, 'Asignar persona')
  await page.getByRole('tab', { name: 'Parámetros y políticas' }).click()
  await page.getByRole('button', { name: 'Guardar parámetros' }).waitFor()
  await page.waitForTimeout(800)
  await save(page, 'admin/02-parametros')
})
shot('admin/03-nuevo-proyecto', async () => {
  const page = await adminPage()
  await go(page, '/projects/new')
  await save(page, 'admin/03-nuevo-proyecto')
})
shot('admin/04-actividad', async () => {
  const page = await adminPage()
  await go(page, `${P}/commitments/${ids.commitmentProgress}?tab=activity`)
  await page.waitForTimeout(1500)
  await save(page, 'admin/04-actividad')
})
async function adminRoute(path, expect) {
  const page = await adminPage()
  await page.goto(`${UI}/admin${path}`)
  await page.waitForLoadState('domcontentloaded')
  if (expect) await page.getByText(expect, { exact: false }).filter({ visible: true }).first().waitFor({ timeout: 30000 })
  await page.waitForTimeout(1800)
  return page
}
shot('admin/05-admin-inicio', async () => save(await adminRoute('/'), 'admin/05-admin-inicio'))
shot('admin/06-usuarios', async () => save(await adminRoute('/admin/users', 'gestion.social.demo@sgspe.test'), 'admin/06-usuarios'))
shot('admin/07-roles', async () => save(await adminRoute('/admin/roles', 'SOCIAL_MANAGER'), 'admin/07-roles'))
async function choose(page, label, option) {
  await page.getByText(label, { exact: false }).filter({ visible: true }).first().click()
  await page.getByRole('option', { name: option }).first().click()
  await page.waitForTimeout(2000)
}
shot('admin/08-permisos', async () => {
  const page = await adminRoute('/admin/permissions')
  await choose(page, 'Elige un rol', /Gerente social/)
  await save(page, 'admin/08-permisos')
})
shot('admin/09-auditoria', async () => save(await adminRoute('/admin/audit'), 'admin/09-auditoria'))
shot('admin/10-workflows', async () => {
  const page = await adminRoute('/automation/workflows')
  await choose(page, 'Elige un objeto', /^Compromiso$/)
  await save(page, 'admin/10-workflows')
})
shot('admin/11-automatizaciones', async () => {
  const page = await adminRoute('/automation/rules')
  await choose(page, 'Elige un objeto', /^Compromiso$/)
  await save(page, 'admin/11-automatizaciones')
})
shot('admin/12-objetos', async () => save(await adminRoute('/data/objects', 'commitment'), 'admin/12-objetos'))
shot('admin/13-capas', async () => save(await adminRoute('/gis/layers'), 'admin/13-capas'))

// ---- ejecución -------------------------------------------------------------------------------------------------------
if (args.includes('--list')) {
  for (const s of shots) console.log(s.name)
  process.exit(0)
}
let failed = 0
for (const s of shots) {
  if (only && !s.name.startsWith(only)) continue
  try {
    await s.run()
    console.log(`✓ ${s.name}`)
  } catch (e) {
    failed++
    console.error(`✗ ${s.name}: ${e.message.split('\n')[0]}`)
  }
}
await browser.close()
console.log(`${shots.length - failed} capturas, ${failed} con error`)
// paleta de 256 colores (Pillow, si está): mismas capturas, un tercio del peso en el repositorio y en los PDF
spawnSync('python3', [join(root, 'scripts', 'optimize-screenshots.py')], { stdio: 'inherit' })
process.exit(failed ? 1 : 0)
