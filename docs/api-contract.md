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
| GET    | `/artistas` | Lista artistas | * | 2 ✅ |
| GET    | `/artistas/:id` | Detalle de un artista | * | 2 ✅ |
| POST   | `/artistas` | Crea un artista | administrador | 2 ✅ |
| PUT    | `/artistas/:id` | Edita un artista | administrador | 2 ✅ |
| DELETE | `/artistas/:id` | Elimina un artista | administrador | 2 ✅ |
| GET    | `/localidades` | Lista localidades | * | 2 ✅ |
| POST   | `/localidades` | Crea una localidad | administrador | 2 ✅ |
| PUT    | `/localidades/:id` | Edita una localidad | administrador | 2 ✅ |
| DELETE | `/localidades/:id` | Elimina una localidad | administrador | 2 ✅ |
| GET    | `/conciertos` | Lista conciertos (con nombre de artista) | * | 2 ✅ |
| GET    | `/conciertos/:id` | Detalle de un concierto | * | 2 ✅ |
| POST   | `/conciertos` | Crea un concierto | administrador | 2 ✅ |
| PUT    | `/conciertos/:id` | Edita un concierto (requiere `estado`) | administrador | 2 ✅ |
| DELETE | `/conciertos/:id` | Elimina un concierto | administrador | 2 ✅ |
| GET    | `/conciertos/:id/inventario` | Aforo y disponibilidad por localidad (tiempo real) | * | 3 ✅ |
| POST   | `/conciertos/:id/inventario` | Asigna precio y aforo a una localidad | administrador | 3 ✅ |
| PUT    | `/conciertos/:id/inventario/:idInventario` | Edita precio/aforo preservando lo vendido | administrador | 3 ✅ |
| DELETE | `/conciertos/:id/inventario/:idInventario` | Quita una localidad del concierto | administrador | 3 ✅ |

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

## Fase 2 — Catálogo

Lectura (`GET`) abierta a cualquier rol autenticado — el vendedor la necesita
para armar la venta. Escritura (`POST`/`PUT`/`DELETE`) exclusiva de
`administrador`; un vendedor recibe `403`.

### Artistas

`POST /artistas` y `PUT /artistas/:id` exigen `nombre_artistico` y
`genero_musical`; `pais_origen` es opcional. `DELETE` responde `409` si el
artista tiene conciertos asociados (`No se puede eliminar: el artista tiene
conciertos asociados.`).

### Localidades

`POST /localidades` y `PUT /localidades/:id` exigen `nombre` (único). Un
nombre repetido responde `409`. `DELETE` responde `409` si la localidad tiene
inventario asociado.

### Conciertos

`POST /conciertos` exige `id_artista` (debe existir), `titulo_evento`,
`fecha_concierto` (ISO 8601) y `recinto`; `estado` es opcional y por defecto
queda `programado`. `PUT /conciertos/:id` además exige `estado` de forma
explícita (`programado` | `activo` | `finalizado` | `cancelado`). `DELETE`
responde `409` si el concierto tiene inventario o ventas asociadas.

```json
{
  "id_artista": 1,
  "titulo_evento": "Gira Aniversario 2026",
  "fecha_concierto": "2026-11-15T20:00:00Z",
  "recinto": "Gran Sala Efraín Recinos",
  "estado": "activo"
}
```

## Fase 3 — Inventario y aforo

Anidado bajo el concierto: `/conciertos/:idConcierto/inventario`. Lectura
abierta a cualquier rol (el vendedor la necesita para ver disponibilidad
antes de vender); escritura solo `administrador`.

`POST` exige `id_localidad` (debe existir), `precio` (`>= 0`) y
`cantidad_total` (entero positivo); `cantidad_disponible` arranca igual a
`cantidad_total`. Responde `409` si esa localidad ya tiene aforo asignado
para ese concierto.

`PUT /conciertos/:id/inventario/:idInventario` recibe `precio` y
`cantidad_total` nuevos y **preserva lo ya vendido**: internamente
`cantidad_disponible` se ajusta por la diferencia entre el aforo nuevo y el
viejo, nunca se resetea. Si el nuevo aforo cae por debajo de lo ya vendido,
la base de datos rechaza el cambio (`CHECK`) y la API responde `400`.

`DELETE` responde `409` si ese registro ya tiene boletos vendidos
(`detalle_ventas` lo referencia).

```json
// POST /conciertos/1/inventario
{ "id_localidad": 2, "precio": 450.00, "cantidad_total": 300 }
```

```json
// respuesta 201 (y forma de cada fila en GET)
{
  "id_inventario": 5,
  "id_concierto": 1,
  "id_localidad": 2,
  "nombre_localidad": "Platea",
  "precio": "450.00",
  "cantidad_total": 300,
  "cantidad_disponible": 300
}
```

## Endpoints planificados

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
