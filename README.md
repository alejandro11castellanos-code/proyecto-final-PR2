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

### 1. Base de datos

Crear una base PostgreSQL en Neon o Supabase y exportar la URL de conexión:

```bash
export DATABASE_URL="postgresql://usuario:password@host/basededatos"
psql "$DATABASE_URL" -f database/schema.sql
psql "$DATABASE_URL" -f database/seed.sql
```

### 2. Backend

```bash
cd backend
cp env.example .env    # completar DATABASE_URL y JWT_SECRET
npm install
npm test
npm run dev            # http://localhost:3000/health
```

### 3. Cliente de escritorio

Requiere JDK 21 y Maven.

```bash
cd desktop-client
mvn javafx:run
```

## Roadmap por fases

| Fase | Objetivo                                                    | Estado |
|------|------------------------------------------------------------|--------|
| 0    | Setup: repo, esquema de BD, esqueleto de backend y cliente | ✅     |
| 1    | Autenticación y roles (`administrador` / `vendedor`)        | ⬜     |
| 2    | Catálogo de administración (artistas, conciertos, localidades) | ⬜  |
| 3    | Inventario y consulta de aforo en tiempo real              | ⬜     |
| 4    | Venta transaccional con múltiples boletos                  | ⬜     |
| 5    | Generación de QR y envío por correo                        | ⬜     |
| 6    | Reportes con JasperReports                                 | ⬜     |
| 7    | Empaquetado `.jar` y despliegue final                      | ⬜     |

## Equipo

- **Backend (2):** BD, API REST, autenticación, gestión transaccional, despliegue en Render.
- **Frontend (2):** cliente JavaFX, vistas de login/administración/punto de venta, integración con la API.
