// prepara el bundle de las composiciones una sola vez, al construir la imagen (llamar a bundle() por pedido es un antipatrón
// según la documentación de Remotion). uso: node scripts/bundle.ts <carpeta>
import { bundle } from '@remotion/bundler'
import path from 'node:path'

const out = path.resolve(process.argv[2] ?? 'bundle')
await bundle({ entryPoint: path.resolve('src/remotion/index.ts'), outDir: out, enableCaching: false })
process.stderr.write(JSON.stringify({ event: 'bundled', out }) + '\n')
