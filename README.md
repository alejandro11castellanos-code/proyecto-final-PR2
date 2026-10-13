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

- **Filtros en reportes:** el dashboard y el PDF se pueden filtrar por rango
  de fecha de compra, artista, concierto, vendedor y localidad (se combinan).
  La portada del PDF indica qué filtros se aplicaron. La ocupación es una
  foto del inventario, así que solo responde a artista, concierto y
  localidad.

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
