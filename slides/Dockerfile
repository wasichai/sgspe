# sgspe-slides (ADR-0031): compone el PDF de las presentaciones con Remotion. dos etapas: la primera instala, prepara el bundle
# de las composiciones y descarga el Chrome headless-shell de Remotion (con red, al construir); la segunda solo corre, sin red
# de salida en producción, como usuario sin privilegios y con el sistema de archivos de solo lectura
FROM node:24-bookworm-slim@sha256:a5f4366e834695d5f363b920c104953a535d3a9864d1bdf74566a0ebe01daee5 AS build
WORKDIR /app
COPY package.json yarn.lock ./
RUN yarn install --frozen-lockfile --non-interactive --silent
COPY tsconfig.json ./
COPY fonts ./fonts
COPY fixtures ./fixtures
COPY src ./src
COPY scripts ./scripts
COPY server ./server
RUN node scripts/bundle.ts /app/bundle && npx remotion browser ensure

FROM node:24-bookworm-slim@sha256:a5f4366e834695d5f363b920c104953a535d3a9864d1bdf74566a0ebe01daee5
# las bibliotecas de ejecución que pide Chrome (las de https://www.remotion.dev/docs/docker, sin los paquetes -dev)
RUN apt-get update \
 && apt-get install -y --no-install-recommends libnss3 libdbus-1-3 libatk1.0-0 libatk-bridge2.0-0 libgbm1 libasound2 \
    libxrandr2 libxkbcommon0 libxfixes3 libxcomposite1 libxdamage1 libpango-1.0-0 libcairo2 libcups2 \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/bundle ./bundle
COPY --from=build /app/package.json ./
COPY --from=build /app/src ./src
COPY --from=build /app/server ./server
ENV NODE_ENV=production \
    HOME=/tmp \
    SLIDES_BUNDLE=/app/bundle \
    SLIDES_PORT=8080
USER node
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=6 \
  CMD node -e "fetch('http://127.0.0.1:8080/health').then(r => process.exit(r.ok ? 0 : 1), () => process.exit(1))"
CMD ["node", "server/main.ts"]
