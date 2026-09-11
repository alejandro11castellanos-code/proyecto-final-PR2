# Contrato de API — SVB-GUA

Base URL local: `http://localhost:3000`
Todas las respuestas son JSON. Salvo login y `/health`, cada endpoint exige
`Authorization: Bearer <token>`.

## Convenciones

- Errores: `{ "error": "mensaje legible" }` con el código HTTP correspondiente.
- Fechas en ISO 8601 UTC.
- Roles: `administrador` (catálogo y reportes), `vendedor` (consulta y venta).

## Estado actual

| Método | Ruta       | Descripción           | Rol | Fase |
|--------|-----------|-----------------------|-----|------|
| GET    | `/health` | Estado del servicio   | —   | 0 ✅ |
| POST   | `/auth/login` | Inicia sesión y devuelve un JWT | — | 1 ✅ |
| GET    | `/auth/me` | Devuelve el usuario autenticado | * | 1 ✅ |

## Fase 1 — Autenticación

### Iniciar sesión

`POST /auth/login`

```json
{
  "nombre_usuario": "vendedor",
  "contrasena": "vendedor123"
}
```

Respuesta `200`:

```json
{
  "token": "<jwt>",
  "usuario": {
    "id_usuario": 2,
    "nombre_usuario": "vendedor",
    "nombre_completo": "Vendedor de prueba",
    "rol": "vendedor"
  }
}
```

Credenciales faltantes responden `400`; credenciales incorrectas responden `401`
sin indicar cuál dato falló.

### Consultar la sesión

`GET /auth/me`, con el encabezado `Authorization: Bearer <token>`.

Respuesta `200`:

```json
{
  "id_usuario": 2,
  "nombre_usuario": "vendedor",
  "nombre_completo": "Vendedor de prueba",
  "rol": "vendedor"
}
```

Un token ausente, inválido o perteneciente a un usuario eliminado responde `401`.

## Endpoints planificados

### Fase 2 — Catálogo (administrador)

| Método | Ruta                  | Descripción                     |
|--------|-----------------------|---------------------------------|
| GET/POST | `/artistas`         | Listar / crear artistas         |
| PUT/DELETE | `/artistas/:id`   | Editar / eliminar artista       |
| GET/POST | `/conciertos`       | Listar / crear conciertos       |
| PUT/DELETE | `/conciertos/:id` | Editar / eliminar concierto     |
| GET/POST | `/localidades`      | Listar / crear localidades      |

### Fase 3 — Inventario y disponibilidad

| Método | Ruta                                   | Descripción                                  |
|--------|----------------------------------------|----------------------------------------------|
| POST   | `/conciertos/:id/inventario`           | Asignar aforo y precio por localidad         |
| GET    | `/conciertos/:id/disponibilidad`       | Boletos disponibles por localidad (tiempo real) |

### Fase 4 — Venta transaccional

| Método | Ruta        | Descripción                                                    |
|--------|-------------|---------------------------------------------------------------|
| POST   | `/ventas`   | Registra una venta con múltiples ítems en una sola transacción |
| GET    | `/ventas/:id` | Detalle de una venta                                         |

Cuerpo tentativo de `POST /ventas`:

```json
{
  "items": [
    { "id_inventario": 1, "cantidad": 2 },
    { "id_inventario": 3, "cantidad": 4 }
  ]
}
```

Reglas: la transacción bloquea cada fila de `inventario_boletos`
(`SELECT ... FOR UPDATE`), valida `cantidad_disponible >= cantidad`, descuenta
el inventario, inserta `ventas` + `detalle_ventas` y confirma. Si algún ítem
falla, se revierte todo.

### Fases 5-6 — QR/correo y reportes

| Método | Ruta                          | Descripción                              |
|--------|-------------------------------|------------------------------------------|
| GET    | `/ventas/:id/boletos`         | Códigos QR de la venta                   |
| POST   | `/ventas/:id/enviar`          | Envía los boletos por correo             |
| GET    | `/reportes/ventas`            | Datos para reportes (por concierto/artista/vendedor) |
