# sgspe-latex (ADR-0030, ASM-139): compone con LuaLaTeX el paquete que arma LatexWriter. TeX Live de Debian trixie sobre la
# imagen fijada por digest, Source Sans 3 de Debian y Source Serif 4 con SHA-256 fijo; la caché de fuentes de luaotfload se
# arma al construir. corre como `tex`, sin red de salida y con el sistema de archivos de solo lectura (compose.yml)
FROM debian:trixie-slim@sha256:a29215f6a35e51e22adffa17f89e9d2ef06214e64a2bad10d765c46aea49f11f

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      texlive-luatex texlive-latex-recommended texlive-latex-extra texlive-pictures texlive-lang-spanish \
      texlive-fonts-recommended latexmk fonts-adobe-sourcesans3 python3 curl ca-certificates unzip \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/ss.zip https://github.com/adobe-fonts/source-serif/releases/download/4.005R/source-serif-4.005_Desktop.zip \
 && echo "549fdb8f9a682bd06944298621404969f6de77c2e422ff3b8244a1dcd6a0c425  /tmp/ss.zip" | sha256sum -c - \
 && mkdir -p /usr/share/fonts/opentype/source-serif-4 \
 && unzip -q -j /tmp/ss.zip 'source-serif-4.005_Desktop/OTF/SourceSerif4-*.otf' -d /usr/share/fonts/opentype/source-serif-4 \
 && rm /tmp/ss.zip \
 && apt-get purge -y curl unzip && apt-get autoremove -y

RUN useradd --create-home --uid 10001 tex \
 && mkdir -p /opt/sgspe-latex/texmf-var && chown tex /opt/sgspe-latex/texmf-var
COPY server.py warmup.tex /opt/sgspe-latex/
USER tex
WORKDIR /home/tex
# la caché de nombres y de fuentes de luaotfload: sin ella, cada composición tarda en cargarlas. queda en la imagen, de solo
# lectura; luaotfload exige una caché escribible, así que server.py la copia al tmpfs al arrancar (TEXMFVAR)
ENV TEXMFVAR=/opt/sgspe-latex/texmf-var TEXMFCACHE=/opt/sgspe-latex/texmf-var
RUN luaotfload-tool -u \
 && mkdir -p /tmp/warmup && cd /tmp/warmup \
 && lualatex --nosocket -interaction=nonstopmode -halt-on-error /opt/sgspe-latex/warmup.tex >/dev/null \
 && rm -rf /tmp/warmup
ENV TEXMFVAR=/tmp/texmf-var TEXMFCACHE=/tmp/texmf-var

ENV LATEX_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s CMD python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=3)"
CMD ["python3", "/opt/sgspe-latex/server.py"]
