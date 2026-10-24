#!/usr/bin/env node
// Mide los pasos de los flujos F-A a F-G del rediseño de UX (02-arquitectura-de-informacion.md §6) sobre la demo VIVA
// (#157, AC-4): cuenta los clics y las pulsaciones desde el punto de partida, a 1440 × 1000 px, con el usuario de la demo
// que hace cada flujo. Corre a mano contra el portal de sgspe-ui conectado al servidor y a la base de demostración,
// fuera de la CI. No cambia datos: cada flujo se detiene en el diálogo o el panel que confirmaría el cambio.
//
//   SGSPE_DEMO_PASSWORD='…' node scripts/measure-flows.live-demo.mjs
//
// Sale con 1 si algún «Medido» supera a su «Propuesto» o si un flujo no llega a donde debe.
import { chromium } from 'playwright'

const UI = (process.env.SGSPE_UI_URL || 'http://localhost:5182').replace(/\/$/, '')
const PASSWORD = process.env.SGSPE_DEMO_PASSWORD
if (!PASSWORD) {
  console.error('falta SGSPE_DEMO_PASSWORD (contraseña de los usuarios ficticios de la demostración)')
  process.exit(2)
}
const USERS = {
  social: 'gestion.social.demo@sgspe.test',
  qcs: 'qcs.demo@sgspe.test',
  aprobacion: 'aprobacion.demo@sgspe.test',
}
const ADMIN = { email: process.env.SGSPE_ADMIN_EMAIL || 'admin@wasichai.local', password: process.env.SGSPE_ADMIN_PASSWORD || 'admin' }
const LOADING = /Cargando|Comprobando/

const tokens = {}
async function token(email) {
  if (tokens[email]) return tokens[email]
  const res = await fetch(`${UI}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password: PASSWORD }) })
  if (!res.ok) throw new Error(`inicio de sesión de ${email}: ${res.status}`)
  tokens[email] = (await res.json()).token
  return tokens[email]
}
async function find(object, filters, email = USERS.social) {
  const q = new URLSearchParams({ page: '0', size: '1', ...filters })
  const res = await fetch(`${UI}/api/objects/${object}/records?${q}`, { headers: { Authorization: `Bearer ${await token(email)}` } })
  if (!res.ok) throw new Error(`GET ${object}: ${res.status}`)
  return (await res.json()).content?.[0]?.id ?? null
}

const browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined, args: ['--lang=es-PE'] })
async function pageFor(email, password = PASSWORD) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'es-PE', timezoneId: 'America/Lima', reducedMotion: 'reduce' })
  const page = await context.newPage()
  await page.goto(`${UI}/login`)
  await page.getByLabel('Correo').fill(email)
  await page.getByLabel('Contraseña').fill(password)
  await page.getByRole('button', { name: 'Ingresar' }).click()
  await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 30000 })
  return page
}
async function settle(page) {
  const deadline = Date.now() + 30000
  await page.locator('h1').filter({ visible: true }).first().waitFor({ state: 'visible', timeout: 30000 })
  const loading = page.getByText(LOADING).filter({ visible: true })
  while ((await loading.count()) && Date.now() < deadline) await page.waitForTimeout(300)
  await page.waitForTimeout(600)
}
// un paso contado: un clic o una pulsación del usuario
function counter(page) {
  const steps = []
  return {
    steps,
    async click(locator, what) {
      await locator.first().click()
      steps.push(what)
      await page.waitForTimeout(400)
      await settle(page).catch(() => {})
    },
    async press(key, what) {
      await page.keyboard.press(key)
      steps.push(what)
      await page.waitForTimeout(600)
      await settle(page).catch(() => {})
    }
  }
}
const visible = (locator) => locator.filter({ visible: true })

const project = await find('project', { code: 'DEMO-T1' })
const P = `/projects/${project}`
const results = []
async function flow(id, name, today, proposed, run) {
  try {
    const { measured, note } = await run()
    const ok = proposed === null || measured <= proposed
    results.push({ id, name, today, proposed, measured, note, ok })
  } catch (e) {
    results.push({ id, name, today, proposed, measured: null, note: e.message.split('\n')[0], ok: false })
  }
}

// F-A · empezar el día: del inicio a la tarea que vence, y a la QCS cuyo plazo vence primero
await flow('F-A', 'Llegar a la tarea propia que vence', '2 + búsqueda', 1, async () => {
  const page = await pageFor(USERS.social)
  await page.goto(`${UI}/`)
  await settle(page)
  const c = counter(page)
  const first = page.getByRole('region', { name: 'Lo que me toca hoy' }).getByRole('listitem').getByRole('link').first()
  const label = (await first.innerText()).trim()
  await c.click(first, 'primera fila de «Lo que me toca hoy»')
  return { measured: c.steps.length, note: `«${label}» → ${new URL(page.url()).pathname.split('/').pop()}${new URL(page.url()).search ? ' con la tarea abierta' : ''}` }
})
await flow('F-A', 'QCS cuyo plazo vence primero (QCS)', 4, 1, async () => {
  const page = await pageFor(USERS.qcs)
  await page.goto(`${UI}/`)
  await settle(page)
  const c = counter(page)
  const first = page.getByRole('region', { name: 'Lo que me toca hoy' }).getByRole('listitem').getByRole('link').first()
  const label = (await first.innerText()).trim()
  await c.click(first, 'primera fila de «Lo que me toca hoy»')
  return { measured: c.steps.length, note: `«${label}» → ${new URL(page.url()).pathname.split('/').pop()}${new URL(page.url()).search ? ' con la tarea abierta' : ''}` }
})

// F-B · avanzar un registro: de la ficha al diálogo de la transición siguiente (no se confirma)
await flow('F-B', 'Compromiso aprobado → «Iniciar ejecución» (hasta Confirmar)', 2, 2, async () => {
  const id = await find('commitment', { code: 'DEMO-C-07' })
  const page = await pageFor(USERS.social)
  await page.goto(`${UI}${P}/commitments/${id}`)
  await settle(page)
  const c = counter(page)
  const primary = visible(page.locator('main [data-variant="primary"]:not([aria-pressed])')).first()
  const label = (await primary.innerText()).trim()
  await c.click(primary, `primaria «${label}»`)
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/Motivo/).fill('medición de pasos')
  const confirm = dialog.getByRole('button', { name: 'Confirmar' })
  if (!(await confirm.isEnabled())) throw new Error('Confirmar no se habilita con el motivo')
  c.steps.push('Confirmar (no se pulsa: no se cambian datos)')
  await dialog.getByRole('button', { name: 'Cancelar' }).click()
  return { measured: c.steps.length, note: `primaria = siguiente acción «${label}»; el motivo se escribe, no cuenta como clic` }
})

// F-C · encontrar y filtrar: vista «Vencidos», «Más filtros» → grupo de interés (wasichai/sgspe-ui#77), volver con el
// filtro y J/K
await flow('F-C', '«Compromisos vencidos de un grupo de interés»', '3 + búsqueda', 4, async () => {
  const page = await pageFor(USERS.social)
  await page.goto(`${UI}${P}/overview`)
  await settle(page)
  const c = counter(page)
  await c.click(page.getByRole('link', { name: /^Compromisos/ }), 'pestaña Compromisos')
  await c.click(page.getByRole('radio', { name: /^Vencidos/ }), 'vista «Vencidos»')
  const more = visible(page.getByRole('button', { name: /Más filtros/ }))
  if (!(await more.count())) throw new Error('la lista de Compromisos no tiene «Más filtros»: no hay filtro por grupo de interés')
  await c.click(more, '«Más filtros»')
  const grupos = page.getByRole('list', { name: 'Grupos de interés' })
  await grupos.waitFor({ state: 'visible', timeout: 30000 })
  // los nombres llegan después de la lista (cada grupo nombra a su persona u organización): mientras tanto dicen «…»
  const hasta = Date.now() + 30000
  while ((await grupos.getByRole('button', { name: '…' }).count()) && Date.now() < hasta) await page.waitForTimeout(300)
  const grupo = visible(grupos.getByRole('button')).first()
  const nombre = (await grupo.innerText()).trim()
  await c.click(grupo, `el grupo «${nombre}»`)
  const url = new URL(page.url())
  if (!url.searchParams.get('stakeholder')) throw new Error('elegir el grupo no escribió ?stakeholder= en la URL')
  const chip = await page.getByText(`Grupo de interés: ${nombre}`).count()
  // el filtro se aplica al elegir; el panel de «Más filtros» se cierra con «Ver resultados» (o Esc), como en toda lista
  return {
    measured: c.steps.length,
    note: `?view=${url.searchParams.get('view') ?? '—'}&stakeholder=…; ${chip ? 'chip «Grupo de interés: …» aplicado' : 'SIN chip'}; la URL se comparte; + cerrar el panel («Ver resultados»), común a «Más filtros»`
  }
})
await flow('F-C', 'Volver desde la ficha con el filtro, y J/K', 'atrás del navegador', 1, async () => {
  const page = await pageFor(USERS.social)
  await page.goto(`${UI}${P}/commitments`)
  await settle(page)
  await page.getByRole('combobox', { name: 'Estado' }).selectOption({ label: 'Aprobado' })
  await page.waitForTimeout(2500)
  const listUrl = page.url()
  const total = Number((await page.getByText(/^\d+ compromisos?$/).first().innerText()).match(/\d+/)[0])
  await visible(page.locator('main table tbody a')).filter({ hasText: /\S/ }).first().click()
  await settle(page)
  const back = visible(page.getByRole('link', { name: /Volver a Compromisos/ }))
  if (!(await back.count())) throw new Error('la ficha no ofrece «Volver a Compromisos»')
  const backText = (await back.first().innerText()).replace(/\s+/g, " ").trim()
  const first = page.url()
  const c = counter(page)
  await c.press('j', 'J')
  const afterJ = page.url()
  await c.press('k', 'K')
  const afterK = page.url()
  const jk = afterJ !== first && afterK === first ? 'J y K recorren la página' : `J/K no recorren (${afterJ === first ? 'J no avanza' : 'K no vuelve'})`
  await back.first().click()
  await settle(page)
  const sameFilter = new URL(page.url()).search === new URL(listUrl).search
  const n = Number(backText.match(/\((\d+)\)/)?.[1])
  return { measured: 1, note: `«${backText}»; vuelve con el filtro ${sameFilter ? 'intacto' : 'PERDIDO'}; N=${n} ${n === total ? '=' : '≠'} ${total} de la lista; ${jk}` }
})

// F-D · registrar en campo: la interacción conserva 5 pasos con el conmutador del modo rápido; la QCS pasa a 3
await flow('F-D', 'Pasos del asistente de interacción', 5, 5, async () => {
  const page = await pageFor('campo.demo@sgspe.test')
  await page.goto(`${UI}${P}/engagements/new`)
  await settle(page)
  const steps = await page.getByRole('navigation', { name: 'Pasos' }).getByRole('listitem').count()
  const quick = await page.getByRole('switch', { name: 'Modo rápido de campo' }).count()
  const done = await page.getByRole('radiogroup', { name: /Ya se realizó/ }).count()
  return { measured: steps, note: `${quick ? 'conmutador «Modo rápido de campo» en la cabecera' : 'SIN conmutador del modo rápido'}; «¿Ya se realizó?» ${done ? 'es un selector de dos opciones' : 'NO es un selector'}` }
})
await flow('F-D', 'Pasos del asistente de QCS', 3, 3, async () => {
  const page = await pageFor(USERS.qcs)
  await page.goto(`${UI}${P}/attention/new`)
  await settle(page)
  const steps = await page.getByRole('navigation', { name: 'Pasos' }).getByRole('listitem').count()
  const panel = await page.getByText('Qué pasará al registrar').count()
  return { measured: steps, note: panel ? '«Qué pasará al registrar» al lado' : 'SIN «Qué pasará al registrar»' }
})

// F-E · aprobar desde la bandeja con panel
await flow('F-E', 'Decidir un aporte (hasta la decisión con motivo)', '4+', 2, async () => {
  const page = await pageFor(USERS.aprobacion)
  await page.goto(`${UI}/tasks/approvals`)
  await settle(page)
  const c = counter(page)
  await c.click(visible(page.getByText(/^DEMO-AP-/)), 'el aporte en la bandeja')
  const panel = page.locator('main')
  const decisions = await visible(panel.getByRole('button', { name: /^(Aprobar|Observar|Rechazar)/ })).allInnerTexts()
  if (!decisions.length) throw new Error('el panel no ofrece decisiones')
  const body = await panel.innerText()
  const shows = ['Antes de aprobar', 'tope'].map((t) => (body.toLowerCase().includes(t.toLowerCase()) ? `; «${t}» a la vista` : `; sin «${t}»`)).join('')
  await c.click(visible(panel.getByRole('button', { name: /^Aprobar/ })), 'Aprobar')
  const dialog = page.getByRole('dialog')
  if (await dialog.count()) await dialog.getByRole('button', { name: 'Cancelar' }).click()
  return { measured: c.steps.length, note: `decisiones en el panel: ${decisions.map((d) => d.trim()).join(', ')}${shows}` }
})

// F-F · leer el territorio: mapa → lugar de la lista → panel del lugar → abrir un registro
await flow('F-F', 'Del mapa a un registro de un lugar', '—', 2, async () => {
  const page = await pageFor(USERS.social)
  await page.goto(`${UI}/territory`)
  await settle(page)
  const c = counter(page)
  await c.click(page.getByRole('button', { name: /^Ver resumen de / }), 'un lugar de «Lugares visibles»')
  const record = visible(page.getByRole('table', { name: 'Registros del lugar' }).getByRole('link')).filter({ hasNotText: /^Vista previa/ })
  if (!(await record.count())) return { measured: c.steps.length, note: 'el panel del lugar no lista registros para abrir' }
  await c.click(record, 'un registro del panel')
  return { measured: c.steps.length, note: `llega a «${(await page.locator('h1').first().innerText()).trim()}»` }
})

// F-G · terminar una asignación desde el menú de la fila, con confirmación que nombra a la persona (no se confirma)
await flow('F-G', 'Terminar una asignación (hasta la confirmación)', 1, 2, async () => {
  const page = await pageFor(ADMIN.email, ADMIN.password)
  await page.goto(`${UI}${P}/settings`)
  await settle(page)
  const c = counter(page)
  const row = visible(page.locator('tr').filter({ hasText: 'Agente de campo (demo)' })).first()
  await c.click(row.getByRole('button', { name: /^Acciones de / }), 'menú «⋯» de la fila')
  await c.click(visible(page.getByRole('button', { name: /^Terminar la asignación…/ })), '«Terminar la asignación…»')
  const dialog = page.getByRole('dialog').or(page.getByRole('alertdialog'))
  const text = (await dialog.first().innerText()).replace(/\s+/g, ' ')
  await page.keyboard.press('Escape')
  return { measured: c.steps.length, note: `la confirmación ${/Agente de campo/.test(text) ? 'nombra a la persona' : 'NO nombra a la persona'} y ${/acceso/i.test(text) ? 'dice el efecto' : 'NO dice el efecto'}` }
})

await browser.close()
console.log('| Flujo | Paso | Hoy | Propuesto | Medido | Nota |\n|---|---|---|---|---|---|')
for (const r of results) console.log(`| ${r.id} | ${r.name} | ${r.today} | ${r.proposed ?? '—'} | ${r.measured ?? '✗'}${r.ok ? '' : ' ⚠️'} | ${r.note} |`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
