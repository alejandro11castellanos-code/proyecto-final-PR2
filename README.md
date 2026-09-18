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
| 6    | Reportes con JasperReports                                 | ⬜     |
| 7    | Empaquetado `.jar` y despliegue final                      | ⬜     |

## Extras (fuera del alcance original)

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
