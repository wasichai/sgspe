% sgspe-informe: la clase de los entregables de SGSPE en PDF (ADR-0030, ASM-139 a ASM-141).
% La versiona el backend y viaja en cada paquete; su SHA-256 se congela en el modelo. Ningún texto del modelo entra aquí:
% main.tex la usa con los macros \sgspe... y el texto ya escapado por LatexText.
%
% Opciones: capitulos (el nivel 1 es un capítulo en página nueva; si no, una sección).
% Borrador: si main.tex se compone con \sgspeborrador definido, cada página lleva BORRADOR y la revisión del borrador.
\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{sgspe-informe}[2026/10/19 v1.0 Entregables de SGSPE]

\newif\ifsgspe@capitulos
\DeclareOption{capitulos}{\sgspe@capitulostrue}
\ProcessOptions\relax

\LoadClass[paper=a4,fontsize=10.5pt,parskip=half-,headings=normal,numbers=noenddot,toc=listof,listof=totoc,
  open=any,oneside,footsepline=0.4pt,headsepline=0.4pt]{scrreprt}

% idioma y fuentes: guiones y rótulos en español; Source Serif 4 en el cuerpo y Source Sans 3 en lo demás (ASM-141)
\RequirePackage[spanish,es-tabla,es-noshorthands,es-nolayout]{babel}
\RequirePackage{fontspec}
\setmainfont{Source Serif 4}[Numbers={Lining,Proportional}]
\setsansfont{Source Sans 3}[Numbers={Lining,Proportional}]
\RequirePackage[protrusion=true,expansion=true,tracking=false,kerning=false,spacing=false]{microtype}

% página: A4 con márgenes de 2,5 cm (3 cm abajo)
\RequirePackage[top=2.5cm,bottom=3cm,left=2.5cm,right=2.5cm,headsep=0.8cm,footskip=1.3cm,headheight=14pt]{geometry}
\linespread{1.12}
\clubpenalty=10000
\widowpenalty=10000
\displaywidowpenalty=10000
\emergencystretch=2.5em
\raggedbottom

\RequirePackage[table]{xcolor}
\definecolor{acento}{HTML}{1F4E79}
\definecolor{sgspegris}{HTML}{595959}
\definecolor{sgspefilete}{HTML}{404040}
\definecolor{sgspefina}{HTML}{D0D4DA}

\RequirePackage{graphicx}
\RequirePackage{array,booktabs,longtable,multirow,colortbl,ragged2e}
\RequirePackage{pdflscape}
\RequirePackage{needspace}
\RequirePackage{enumitem}
\setlist{itemsep=2pt,topsep=3pt,parsep=0pt}
\setlist[itemize]{leftmargin=1.4em,label=\textcolor{acento}{\textbullet}}
\setlist[enumerate]{leftmargin=1.8em}
\RequirePackage[bottom,hang]{footmisc}
\RequirePackage{caption}
% «Tabla 4.1.» y medio cuadratín antes del título
\DeclareCaptionLabelSeparator{sgspe}{.\enskip}
\captionsetup{font={sf,small},labelfont={bf,color=acento},labelsep=sgspe,justification=raggedright,
  singlelinecheck=false,position=top,skip=5pt}
\captionsetup[longtable]{position=top}

% títulos: en sans y en el color de acento, con el número literal del tipo (\sgspenumero) y entradas de índice dinámicas
\setcounter{secnumdepth}{5}
\addtokomafont{disposition}{\color{acento}}
\setkomafont{chapter}{\sffamily\bfseries\huge}
\setkomafont{section}{\sffamily\bfseries\Large}
\setkomafont{subsection}{\sffamily\bfseries\large}
\setkomafont{subsubsection}{\sffamily\bfseries\normalsize}
\setkomafont{paragraph}{\sffamily\bfseries\itshape\normalsize}
\setkomafont{subparagraph}{\sffamily\itshape\normalsize}
\setkomafont{chapterentry}{\sffamily\bfseries}
\renewcommand*{\chapterformat}{\thechapter\enskip}
\renewcommand*{\sectionformat}{\thesection\enskip}
\renewcommand*{\subsectionformat}{\thesubsection\enskip}
\renewcommand*{\subsubsectionformat}{\thesubsubsection\enskip}
\renewcommand*{\paragraphformat}{\theparagraph\enskip}
\RedeclareSectionCommand[beforeskip=0pt,afterskip=1.4\baselineskip]{chapter}
\RedeclareSectionCommand[beforeskip=-1.8\baselineskip,afterskip=.7\baselineskip]{section}
\RedeclareSectionCommand[beforeskip=-1.4\baselineskip,afterskip=.5\baselineskip]{subsection}
\RedeclareSectionCommand[beforeskip=-1.2\baselineskip,afterskip=.4\baselineskip]{subsubsection}
\RedeclareSectionCommand[beforeskip=-1\baselineskip,afterskip=.3\baselineskip]{paragraph}
\RedeclareSectionCommand[beforeskip=-.8\baselineskip,afterskip=.3\baselineskip]{subparagraph}
\RedeclareSectionCommands[tocdynnumwidth]{chapter,section,subsection,subsubsection,paragraph,subparagraph}
\DeclareTOCStyleEntries[dynnumwidth]{tocline}{figure,table}
% el número de un título, de una tabla o de una figura es el literal del tipo: «4.1», «Tabla 5.2»
\newcommand*{\sgspenumero}[2]{\expandafter\renewcommand\expandafter*\csname the#1\endcsname{#2}}

% encabezado y pie: el título corto y el capítulo arriba; el código, la revisión y el nivel, y «Página X de Y» abajo
\RequirePackage{zref-totpages}
\RequirePackage[automark]{scrlayer-scrpage}
\newcommand*{\sgspecodigo}{}
\newcommand*{\sgsperevision}{}
\newcommand*{\sgspenivel}{}
\newcommand*{\sgspetitulocorto}{}
% \sgspemeta{código}{revisión del borrador}{revisión de emisión}{nivel, vacío en INTERNO}{título corto}
\newcommand*{\sgspemeta}[5]{%
  \renewcommand*{\sgspecodigo}{#1}%
  \ifdefined\sgspeborrador\renewcommand*{\sgsperevision}{#2}\else\renewcommand*{\sgsperevision}{#3}\fi
  \renewcommand*{\sgspenivel}{\if\relax\detokenize{#4}\relax\else\ ·\ #4\fi}%
  \renewcommand*{\sgspetitulocorto}{#5}}
\clearpairofpagestyles
\setkomafont{pageheadfoot}{\sffamily\footnotesize\color{sgspegris}}
\setkomafont{pagenumber}{}
\ihead{\sgspetitulocorto}
\ifsgspe@capitulos\ohead{\leftmark}\else\ohead{}\fi
\ifoot*{\sgspecodigo\ ·\ Rev.\ \sgsperevision\sgspenivel}
\ofoot*{Página \thepage\ de \ztotpages}
\renewcommand*{\chapterpagestyle}{scrheadings}
\pagestyle{scrheadings}

% la marca de los borradores (RQ-REP-021): BORRADOR en diagonal, gris claro, en cada página
% con el gancho de LaTeX al enviar cada página, también la portada
\ifdefined\sgspeborrador
  \AddToHook{shipout/background}{\put(0.5\paperwidth,-0.5\paperheight){\makebox(0,0){\rotatebox{45}{%
    \sffamily\bfseries\fontsize{96}{96}\selectfont\color{black!10}BORRADOR}}}}
\fi

% tablas: sans de 9 pt con cifras tabulares, filetes de booktabs sin verticales, una línea fina entre filas
\setlength{\tabcolsep}{4.5pt}
\renewcommand*{\arraystretch}{1.18}
\setlength{\aboverulesep}{0.3ex}
\setlength{\belowrulesep}{0.45ex}
\setlength{\cmidrulewidth}{0.3pt}
\setlength{\LTpre}{6pt}
\setlength{\LTpost}{10pt}
% las tablas van a la izquierda, con la leyenda del ancho de la caja
\setlength{\LTleft}{0pt}
\setlength{\LTright}{\fill}
\setlength{\LTcapwidth}{\linewidth}
\newcommand*{\sgspetablafuente}{\sffamily\fontsize{9}{11}\selectfont\addfontfeatures{Numbers=Tabular}\arrayrulecolor{sgspefilete}\hbadness=10000\relax}
\newcommand*{\sgspefina}{\arrayrulecolor{sgspefina}}
\newcommand*{\sgspefuerte}{\arrayrulecolor{sgspefilete}}
\newcommand*{\sgspecontinua}{{\footnotesize\itshape\color{sgspegris}\tablename~\thetable\ (continuación)}}
% la línea «Fuente:» de una tabla o figura
\newcommand{\sgspefuente}[1]{\par\nopagebreak\vspace{3pt}{\sffamily\fontsize{8.5}{10.5}\selectfont\itshape\color{sgspegris}#1\par}\vspace{10pt}}
\newcommand{\sgspefuenteentabla}[1]{{\fontsize{8.5}{10.5}\selectfont\itshape\color{sgspegris}#1}}
% el alto máximo de un mapa o una foto en la página
\newcommand*{\sgspealtomapa}{\dimexpr\textheight-7\baselineskip\relax}
\newcommand*{\sgspeausente}[1]{{\sffamily\small\color{sgspegris}[#1]}}

% gráficos con pgfplots, vectoriales, con las fuentes de la clase y sin modo matemático
\RequirePackage{pgfplots}
\pgfplotsset{compat=1.18}
\pgfplotsset{
  sgspe/.style={
    width=\linewidth, height=7.5cm, scale only axis=false,
    font=\sffamily\footnotesize,
    axis lines=left, axis line style={sgspegris}, tick style={sgspegris},
    ymajorgrids, grid style={black!10},
    legend style={draw=none, fill=none, font=\sffamily\footnotesize, legend columns=-1, at={(0.5,-0.18)}, anchor=north},
    legend cell align=left,
    tick label style={/pgf/number format/assume math mode=true},
    /pgf/number format/use comma, /pgf/number format/1000 sep={\,},
    unbounded coords=jump,
    cycle list={{acento,mark=*},{orange!80!black,mark=square*},{teal,mark=triangle*},{sgspegris,mark=diamond*},
      {red!70!black,mark=pentagon*},{violet,mark=otimes*}}
  }
}

\RequirePackage{hyperref}
\hypersetup{hidelinks,bookmarksnumbered,bookmarksopen,pdfcreator={SGSPE}}

% la portada: logo, proyecto, título, el contenido de la sección «portada» (el mapa de ubicación) y la autoría
% \sgspeportada{título}{proyecto}{preparado para}{suscrito por}{dirección}{fecha}{archivo del logo o vacío}{contenido}
\newcommand{\sgspefila}[2]{\if\relax\detokenize{#2}\relax\else\textcolor{sgspegris}{#1}&#2\\\fi}
\newcommand{\sgspeportada}[8]{%
  \begin{titlepage}
    \sffamily
    \if\relax\detokenize{#7}\relax\else\noindent\includegraphics[height=1.8cm,width=6cm,keepaspectratio]{#7}\par\fi
    \vspace*{1.2cm}
    {\color{acento}\rule{\linewidth}{1.5pt}}\par\vspace{0.9cm}
    {\large\color{sgspegris}#2\par}\vspace{0.4cm}
    {\fontsize{26}{31}\selectfont\bfseries\color{acento}\RaggedRight #1\par}
    \vspace{0.8cm}
    #8
    \vfill
    {\color{acento}\rule{\linewidth}{0.6pt}}\par\vspace{0.3cm}
    \noindent\begin{tabular}{@{}>{\RaggedRight}p{0.24\linewidth}>{\RaggedRight\arraybackslash}p{0.72\linewidth}@{}}
      \sgspefila{Preparado para}{#3}
      \sgspefila{Suscrito por}{#4}
      \sgspefila{Dirección}{#5}
      \sgspefila{Fecha}{#6}
      \sgspefila{Código}{\sgspecodigo\ · Rev.\ \sgsperevision\sgspenivel}
    \end{tabular}
  \end{titlepage}}

\endinput
