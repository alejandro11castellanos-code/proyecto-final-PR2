# SVB-GUA — Sistema de Venta Local de Boletos

Proyecto Final · UMG Programación II. Venta de entradas para conciertos en puntos
físicos, con clientes de escritorio para vendedores autorizados y control de aforo
en tiempo real.

## Arquitectura (3 capas)

```
Cliente JavaFX  ──HTTP/REST──▶  API Node.js + Express  ──▶  PostgreSQL (Neon/Supabase)
  (.jar)                          (Render)
```

## Estructura del repositorio

| Carpeta          | Contenido                                              |
|------------------|-------------------------------------------------------|
| `database/`      | `schema.sql` (DDL) y `seed.sql` (datos de prueba)      |
| `backend/`       | API REST en Node.js + Express                          |
| `desktop-client/`| Cliente de escritorio JavaFX (Maven)                   |
| `docs/`          | Contrato de API y documentación de diseño              |

## Puesta en marcha

La base de datos vive en **Neon** (no hay Postgres local en Docker — lo usamos
al principio y lo dimos de baja al migrar). Cada quien en el equipo necesita
su propio `backend/.env` con la `DATABASE_URL` de Neon; no se versiona (está
en `.gitignore`).

### 1. Base de datos (una sola vez, quien administre el proyecto en Neon)

```bash
export NEON_URL="postgresql://usuario:password@host/basededatos?sslmode=require&channel_binding=require"

# El esquema se puede aplicar con psql...
psql "$NEON_URL" -f database/schema.sql

# ...pero el seed NO: database/seed.sql trae placeholders de contraseña
# (__BCRYPT_ADMIN__, __BCRYPT_VENDEDOR__) que este script reemplaza por
# hashes bcrypt reales antes de insertar. psql solo, sin pasar por Node,
# insertaría esos placeholders tal cual y nadie podría loguearse.
cd backend
DATABASE_URL="$NEON_URL" node scripts/seed.js
cd ..
```

### 2. Backend

```bash
cd backend
cp env.example .env    # completar DATABASE_URL (la de Neon) y JWT_SECRET
npm install
npm test
npm run dev            # http://localhost:3000/health
```

**Alternativa con Docker** (por si no querés instalar Node localmente): el
`docker-compose.yml` solo corre el backend, leyendo `backend/.env` — la base
sigue siendo Neon.

```bash
docker compose up -d --build
curl http://localhost:3000/health
```

### 3. Cliente de escritorio

Requiere JDK 21 y Maven.

El cliente usa `http://localhost:3000` por defecto. Para apuntarlo a la API
desplegada, definir `SVB_API_URL` antes de iniciarlo.

```bash
cd desktop-client
export SVB_API_URL="https://api.ejemplo.com"
mvn javafx:run
```

## Roadmap por fases

| Fase | Objetivo                                                    | Estado |
|------|------------------------------------------------------------|--------|
| 0    | Setup: repo, esquema de BD, esqueleto de backend y cliente | ✅     |
| 1    | Autenticación y roles (`administrador` / `vendedor`)        | ✅     |
| 2    | Catálogo de administración (usuarios, artistas, conciertos, localidades) | ✅     |
| 3    | Inventario y consulta de aforo en tiempo real              | ✅     |
| 4    | Venta transaccional con múltiples boletos                  | ✅     |
| 5    | Generación de QR y envío por correo                        | ✅     |
| 6    | Reportes (dashboard JavaFX + exportable con JasperReports) | ✅     |
| 7    | Empaquetado `.jar` y despliegue final                      | ⬜     |

## Instalador del cliente (macOS)

El cliente se empaqueta como `.dmg` con su propio Java incluido: quien lo instala no necesita
Java, Maven ni el código.

```bash
cd desktop-client
./packaging/crear-instalador-mac.sh        # genera target/instalador/SVB-GUA-1.0.0.dmg (~80 MB, para este procesador)
./packaging/crear-instalador-mac.sh app-image   # solo la app suelta, para probarla sin instalar
```

Requiere macOS, JDK 21 (trae `jpackage`) y Maven. `jpackage` solo crea el instalador del sistema
y del procesador en el que corre: el de Windows (`.exe`/`.msi`) y el de Mac Intel se generan en
una máquina de GitHub (ver más abajo).

**Cuál `.dmg` elegir:** depende del procesador de la Mac (menú  → *Acerca de esta Mac*).

| Mac | Instalador | Cómo se genera |
|-----|------------|----------------|
| Apple Silicon (M1, M2, M3, M4) | `SVB-GUA-1.0.0-mac-apple-silicon.dmg` | `./packaging/crear-instalador-mac.sh` en una Mac con Apple Silicon |
| Intel (Macs anteriores a 2020 o con "Intel" en *Acerca de*) | `SVB-GUA-1.0.0-mac-intel.dmg` | Actions -> *Instalador de macOS (Intel)* -> **Run workflow**; el artefacto es **SVB-GUA-Mac-Intel** |

Un `.dmg` para el procesador equivocado no abre: macOS responde que la app no es compatible.

**Instalar:** abrir el `.dmg` y arrastrar *SVB-GUA* a *Aplicaciones*. Como el instalador no está
firmado ni notarizado por Apple, la primera vez macOS lo bloquea ("Apple no puede revisar si
contiene software malicioso"). Se desbloquea una sola vez, de cualquiera de estas formas:

1. Tocar **OK** en el aviso y abrir *Ajustes del Sistema -> Privacidad y seguridad*; abajo, en
   *Seguridad*, aparece **Abrir de todos modos**. Confirmar con la contraseña de la Mac.
2. O, en la terminal: `xattr -cr /Applications/SVB-GUA.app` y abrir la app con doble clic.

(En macOS recientes ya no sirve el clic derecho -> *Abrir*.)

### Windows

El instalador de Windows lo arma GitHub en una máquina Windows (hace falta un flujo propio porque
`jpackage` solo genera el instalador del sistema donde corre). En la pestaña **Actions** del
repositorio: *Instalador de Windows* -> **Run workflow** (se puede indicar la dirección del
servidor). Al terminar, el artefacto **SVB-GUA-Windows** contiene:

- `SVB-GUA-1.0.0.exe`: instalador por usuario (no pide permisos de administrador), con acceso
  directo y entrada en el menú Inicio.
- `SVB-GUA-portatil.zip`: versión portátil; se descomprime y se abre `SVB-GUA\SVB-GUA.exe`, sin instalar.

Como no están firmados, Windows muestra "Windows protegió su PC": clic en **Más información** y
luego **Ejecutar de todas formas**. Ambos incluyen su propio Java.

**Servidor:** la app necesita un backend al que conectarse (para publicarlo en Render, ver `docs/despliegue-render.md`). En la pantalla de login, el enlace
**Cambiar** (junto a "Servidor: ...") permite escribir su dirección (por ejemplo
`http://192.168.1.10:3000`); la app comprueba que responda y la recuerda en
`~/.svb-gua/servidor.txt`. Sin configurar, usa `http://localhost:3000`.

## Extras (fuera del alcance original)

- **Historial de ventas:** pestaña "Historial" (admin, con filtro de
  vendedor y concierto) y "Mis ventas" (vendedor, solo las suyas). "Ver
  boletos" reabre el mismo diálogo de QR/correo de la fase 5 — sirve para
  reimprimir o reenviar los boletos de una venta pasada sin pantalla nueva.


- **Rediseño de la interfaz:** tema global (`theme.css`) con barra lateral de
  navegación, tarjetas, tablas y campos redondeados, login con panel de marca
  y paleta de colores en las gráficas. Animaciones cortas: transición entre
  secciones, entrada en cascada de las tarjetas de Reportes, contadores de KPI
  y barras de ocupación que se llenan, escala al pasar el mouse por los
  botones y sacudida del formulario ante credenciales inválidas.

- **Formularios en tarjeta y fondo de portada:** en Usuarios, Artistas,
  Localidades, Conciertos y Aforo el formulario de alta/edición es una tarjeta
  junto a la tabla (con título "Nuevo"/"Editar" según la selección). En Ventas
  la portada del concierto elegido se muestra difuminada de fondo y cambia con
  un fundido al elegir otro.

- **Control de acceso por QR:** cada boleto es una persona, con su propio QR y un
  código no adivinable. Un usuario con rol `acceso` (la puerta) registra el ingreso
  escribiendo, pegando o escaneando (con lector USB) el código; el boleto se marca como
  usado una sola vez y la app muestra la asistencia por concierto y los últimos
  ingresos, refrescándose sola. Escanear un QR con una cámara cualquiera no registra
  nada. Usuario de desarrollo: `puerta1` / `puerta123`. Detalles en `docs/api-contract.md`.

- **Escáner web con cámara:** `/acceso/` en el propio backend (por ejemplo
  `https://<servidor>/acceso/`) permite escanear los QR con la cámara de un celular: se abre con el
  QR y el botón del panel *Escanear con el celular* en Control de acceso. Funciona con cualquier
  navegador moderno (necesita HTTPS, que Render ya da) y el resultado se refleja en la app.

- **Fecha y hora legibles:** el formulario de conciertos pide la fecha con un
  calendario y la hora en horas/minutos (24 h), sin formatos técnicos. En toda la
  app las fechas se ven como `15 nov 2026, 20:00`. Las fechas viajan como hora
  local de Guatemala sin zona (ver `docs/api-contract.md`).

- **Filtros en reportes:** el dashboard y el PDF se pueden filtrar por rango
  de fecha de compra, artista, concierto, vendedor y localidad (se combinan).
  La portada del PDF indica qué filtros se aplicaron. La ocupación es una
  foto del inventario, así que solo responde a artista, concierto y
  localidad.

- **Compra de varios conciertos y pago en efectivo:** el carrito del punto de venta
  conserva los boletos al cambiar de concierto, así que una misma compra puede mezclarlos.
  Se cobra con tarjeta (simulada) o en efectivo (se escribe el monto recibido y la app
  calcula el cambio); el método queda guardado en la venta y se ve en "Mis ventas".

- **Tabla cruzada (pivote):** en Reportes se elige qué va en filas, qué en
  columnas y qué medir (boletos o ingresos) entre concierto, localidad, artista,
  vendedor, día y método de pago. Se ve como mapa de calor con totales y el PDF la incluye en
  su última página. Por defecto cruza concierto × localidad.

- **Alerta de últimos boletos:** la columna "Disponibles" en Aforo y en el
  punto de venta resalta en naranja "¡Últimos N!" cuando quedan 10 boletos
  o menos de una localidad, y en rojo "AGOTADO" cuando llega a 0. Es un
  umbral fijo (no un %), a propósito: "últimos boletos" tiene que sentirse
  igual en una sala de 10 personas que en un estadio de 10,000.


- **Boleto con diseño real:** el diálogo de boletos (fase 5) ya no es una
  tarjeta genérica — banner con la foto del artista (la misma fuente que
  usa "▶ Escuchar", sin llamada extra), línea de perforación, QR grande
  abajo y una animación de aparición suave (fade + scale) al abrirse.


- **Simulador de pago con tarjeta:** al confirmar una venta en el punto de
  venta, se abre un diálogo que pide número de tarjeta, nombre, vencimiento
  y CVV; valida todo localmente (algoritmo de Luhn para el número, mes/año
  no vencido, CVV de 3-4 dígitos) y "aprueba" el pago tras una espera falsa,
  sin conectarse a ningún banco ni pasarela real. Es intencional: el
  enunciado excluye explícitamente integrar una pasarela de pago (ver
  sección "No Incluye" del documento original), así que esto se queda
  100% local — solo agrega el paso visual de cobro que le faltaba a la demo.


- **Adelanto musical del artista:** en `Conciertos` y en el punto de venta,
  un panel muestra imagen + nombre de artista + canción, con botón
  "▶ Escuchar" para un adelanto de ~30s. Usa la iTunes Search API como
  fuente principal (imagen = portada del sencillo/álbum) y Deezer como
  respaldo (imagen = foto real del artista) — ambas públicas, sin
  autenticación. Se descartó el Web Playback SDK de Spotify: corre solo en
  navegador, exige cuenta Premium por usuario y OAuth, nada de lo cual
  encaja en un cliente de escritorio Java.

## Equipo

- **Backend (2):** BD, API REST, autenticación, gestión transaccional, despliegue en Render.
- **Frontend (2):** cliente JavaFX, vistas de login/administración/punto de venta, integración con la API.
