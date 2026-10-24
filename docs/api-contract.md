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
| GET    | `/usuarios` | Lista usuarios (sin hash) | administrador | 2 ✅ |
| POST   | `/usuarios` | Crea un usuario | administrador | 2 ✅ |
| PUT    | `/usuarios/:id` | Edita nombre y rol | administrador | 2 ✅ |
| PUT    | `/usuarios/:id/contrasena` | Restablece la contraseña | administrador | 2 ✅ |
| DELETE | `/usuarios/:id` | Elimina un usuario | administrador | 2 ✅ |
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

### Usuarios

Todo `/usuarios/*` es exclusivo de `administrador` (incluida la lectura: no
hay razón para que un vendedor vea la lista de usuarios).

`POST /usuarios` exige `nombre_usuario`, `contrasena` (mínimo 6
caracteres), `nombre_completo` y `rol` (`administrador` | `vendedor`).
Responde `409` si `nombre_usuario` ya existe. Ninguna respuesta incluye
`contrasena_hash`.

`PUT /usuarios/:id` edita `nombre_completo` y `rol` (no el nombre de
usuario, que es el identificador de login). `PUT /usuarios/:id/contrasena`
restablece la contraseña por separado, con el mismo mínimo de 6 caracteres.

`DELETE /usuarios/:id` responde `400` si el administrador intenta eliminar
su propia cuenta, y `409` si el usuario tiene ventas registradas
(`ventas.id_vendedor` lo referencia).

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

## Fase 4 — Venta transaccional

| Método | Ruta          | Descripción                                                    | Rol |
|--------|---------------|-----------------------------------------------------------------|-----|
| POST   | `/ventas`     | Registra una venta con uno o más ítems en una sola transacción  | *   |
| GET    | `/ventas` | Historial (lista liviana, sin detalle por ítem)                      | *   |
| GET    | `/ventas/:id` | Detalle de una venta, con localidad y evento de cada ítem        | *   |

`GET /ventas` — el vendedor solo ve las suyas (`id_vendedor` sale del
token; si intenta pasar `?vendedor=` de otro, `400`). El administrador
puede filtrar por `?vendedor=<id>` y/o `?concierto=<id>`. Máximo 200 filas,
ordenadas por fecha descendente. Cada fila trae `id_venta`, `fecha_venta`,
`id_vendedor`, `nombre_vendedor`, `total_venta` y `boletos` (suma de
`detalle_ventas.cantidad`) — el detalle por ítem se pide aparte con
`GET /ventas/:id`.

```json
// POST /ventas
{
  "items": [
    { "id_inventario": 1, "cantidad": 2 },
    { "id_inventario": 3, "cantidad": 4 }
  ],
  "metodo_pago": "efectivo"
}
```

`id_vendedor` sale del token, no del cuerpo. Cada `id_inventario` debe
aparecer una sola vez (cantidades repetidas se rechazan con `400`, para que
el cliente las agrupe). Los renglones pueden ser de **conciertos distintos**: una
misma compra puede mezclarlos, y cada boleto conserva el suyo.

`metodo_pago` es `"efectivo"` o `"tarjeta"` y es opcional: si falta se registra
`"tarjeta"` (los clientes anteriores solo tenían el simulador de tarjeta); un valor
distinto responde `400`. Se guarda en `ventas.metodo_pago` y viaja en `POST /ventas`,
`GET /ventas`, `GET /ventas/:id` y como dimensión `metodo` de la tabla cruzada. El
cobro en efectivo (monto recibido y cambio) se calcula en el cliente: no se guarda.
La transacción, en orden:

1. Bloquea todas las filas de `inventario_boletos` involucradas
   (`SELECT ... FOR UPDATE`, en orden de `id_inventario` para evitar
   deadlocks entre ventas concurrentes que comparten localidades).
2. Valida `cantidad_disponible >= cantidad` para **todos** los ítems antes de
   aplicar **ninguno** — si un ítem no existe (`404`) o no alcanza (`409`),
   la venta completa se revierte, incluidos los ítems que sí tenían stock.
3. Descuenta el inventario, inserta `ventas` + `detalle_ventas` y confirma.

Verificado con dos ventas concurrentes por el mismo cupo limitado: la
segunda ve la disponibilidad real que dejó la primera y nunca se vende de
más (`cantidad_disponible` no baja de `0`).

```json
// respuesta 201 / 200 de GET
{
  "id_venta": 1,
  "id_vendedor": 2,
  "fecha_venta": "2026-09-11T04:00:04.824Z",
  "total_venta": "300.00",
  "items": [
    {
      "id_detalle": 1,
      "id_inventario": 7,
      "cantidad": 3,
      "precio_unitario": "100.00",
      "subtotal": "300.00",
      "nombre_localidad": "Platea",
      "id_concierto": 2,
      "titulo_evento": "Noche Acustica"
    }
  ]
}
```

## Fase 5 — Códigos QR y envío por correo

No existe una tabla de boletos individuales: cada **ítem de la venta**
(`detalle_ventas`) genera un único QR que representa esas `cantidad`
entradas de esa localidad — no un QR por asiento.

| Método | Ruta                  | Descripción                        | Rol |
|--------|-----------------------|-------------------------------------|-----|
| GET    | `/ventas/:id/boletos` | Genera el QR de cada ítem de la venta | *   |
| POST   | `/ventas/:id/enviar`  | Genera los QR y los envía por correo  | *   |

El código de cada boleto es `SVBGUA-V<id_venta>-D<id_detalle>` y el QR viaja
como `data:image/png;base64,...` (se puede decodificar directo en el cliente
de escritorio o incrustar en un `<img>`).

```json
// GET /ventas/1/boletos
{
  "id_venta": 1,
  "boletos": [
    {
      "id_detalle": 1,
      "titulo_evento": "Gira Aniversario 2026",
      "nombre_artistico": "Los Miserables",
      "nombre_localidad": "VIP",
      "cantidad": 2,
      "codigo": "SVBGUA-V1-D1",
      "qr": "data:image/png;base64,iVBORw0KGgo..."
    }
  ]
}
```

`POST /ventas/:id/enviar` recibe `{ "email": "..." }`, valida el formato y
manda un correo HTML con un QR por boleto. Con `BREVO_API_KEY` + `MAIL_FROM`
(remitente verificado en Brevo) sale por la API HTTPS de Brevo y los QR van como
PNG adjuntos; sin ella, por SMTP (`SMTP_HOST`/`SMTP_PORT`/`SMTP_USER`/`SMTP_PASS`)
con el QR incrustado (`cid`) más el PNG adjunto. Sin ninguno de los dos
configurados responde `502` con un mensaje
claro en vez de fallar oscuro dentro de nodemailer. Verificado enviando un
correo real a una casilla de prueba (Ethereal): asunto, remitente y los QR
incrustados llegan correctos y son escaneables.

## Fase 6 — Reportes

`GET /reportes/dashboard` — exclusivo de `administrador` (igual que
`/usuarios`: es información gerencial, no algo que necesite el vendedor).
Una sola llamada trae las siete secciones que arma el dashboard:

```json
{
  "resumen": { "ingresos_totales": "5000.00", "boletos_vendidos": 12, "precio_promedio": "416.67" },
  "por_concierto": [{ "id_concierto": 1, "titulo_evento": "...", "nombre_artistico": "...", "ingresos": "5000.00", "boletos": 12 }],
  "por_artista": [{ "id_artista": 1, "nombre_artistico": "...", "ingresos": "5000.00", "boletos": 12 }],
  "por_vendedor": [{ "id_usuario": 1, "nombre_completo": "...", "rol": "administrador", "ingresos": "5000.00", "ventas": 1 }],
  "por_dia": [{ "dia": "2026-09-18", "ingresos": "5000.00", "boletos": 12 }],
  "por_localidad": [{ "id_localidad": 2, "nombre": "Platea", "boletos": 6 }],
  "ocupacion": [{ "id_concierto": 1, "titulo_evento": "...", "cantidad_total": 2300, "vendido": 12 }]
}
```

### Filtros (query string, todos opcionales)

`GET /reportes/dashboard?desde=2026-09-01&hasta=2026-09-30&artista=1&concierto=2&vendedor=3&localidad=4`

| Parámetro | Formato | Efecto |
|-----------|---------|--------|
| `desde`, `hasta` | `AAAA-MM-DD` (inclusivos; se comparan por día de `fecha_venta`) | Solo ventas dentro del rango |
| `artista`, `concierto`, `vendedor`, `localidad` | id entero positivo | Solo ventas de ese artista / concierto / vendedor / localidad |

Los filtros se combinan con AND. Un parámetro presente pero inválido
(fecha mal formada o inexistente, `desde` > `hasta`, id no numérico, cero,
negativo o vacío) responde `400` con `{ "error": "..." }`; nunca se ignora
en silencio. Una combinación sin ventas (p. ej. un concierto de otro
artista) responde `200` con ceros, no error. Sin parámetros el resultado es
idéntico al anterior a los filtros (verificado contra Neon: las 7 consultas
coinciden fila por fila).

Cómo se aplican: las ventas se filtran en un CTE `hechos` (una fila por
renglón vendido) y las dimensiones (conciertos, artistas, vendedores,
localidades) se le unen con `LEFT JOIN`, así que un concierto sin ventas en
el rango sigue apareciendo en cero. `por_vendedor` suma los subtotales de
los renglones que cumplen el filtro (no `total_venta`) para que un filtro
por concierto o localidad no atribuya al vendedor renglones ajenos.
**`ocupacion` es una foto del inventario**, no un acumulado de ventas: solo
responde a `artista`, `concierto` y `localidad`; ignora fechas y vendedor.

`por_vendedor` **no filtra por rol**: cualquier usuario autenticado puede
vender (incluido un administrador probando el punto de venta), así que se
agregan todas las ventas por `id_vendedor` sin importar el rol de quien las
hizo — de lo contrario una venta de un administrador sumaría en `resumen`
pero desaparecería de este desglose, y los totales dejarían de coincidir.
Verificado justamente por eso: con una venta real hecha por un
administrador, `resumen.ingresos_totales` coincide con la suma de
`por_vendedor[].ingresos`.

El destino de estos datos: la pantalla de administración los grafica con
los charts nativos de JavaFX (dashboard en vivo); JasperReports queda para
el reporte exportable/imprimible aparte (PDF), no para esta pantalla.

### Tabla cruzada (pivote)

`GET /reportes/cruzado` — exclusivo de `administrador`. Cruza dos dimensiones
y mide una cantidad; acepta los mismos filtros que el dashboard.

| Parámetro | Valores | Por defecto |
|-----------|---------|-------------|
| `filas`, `columnas` | `concierto`, `localidad`, `artista`, `vendedor`, `dia`, `metodo` (distintas entre sí) | `concierto` × `localidad` |
| `medida` | `boletos` (entero), `ingresos` (decimal como texto, igual que el resto) | `boletos` |

```json
{
  "filas": "concierto", "columnas": "localidad", "medida": "boletos",
  "celdas": [{ "fila": "Gira Aniversario 2026", "columna": "Platea", "valor": 15 }]
}
```

Formato plano (una celda por combinación) para que cada cliente arme su
matriz. Solo aparecen las combinaciones **con ventas**: no se inventan
ceros para pares que quizá ni existen (una localidad que ese concierto no
ofrece). Una dimensión o medida desconocida, o la misma dimensión en filas y
columnas, responde `400`. Las expresiones SQL de cada dimensión salen de una
lista cerrada del repositorio; el texto del usuario nunca se concatena.

Verificado contra Neon: las 40 combinaciones (5 × 4 dimensiones × 2
medidas) suman exactamente `resumen` (boletos e ingresos).

El cliente la muestra como mapa de calor con totales (`TablaCruzadaPane`) y
el PDF la incluye como última página apaisada con un `crosstab` nativo de
JasperReports (`tabla_cruzada.jrxml`). Para agregar otra dimensión basta una
entrada en `DIMENSIONES` del repositorio y en la ruta.

### Exportación a PDF (JasperReports)

El botón "Exportar PDF" del dashboard (`ReportesPane`) arma el mismo
`Dashboard` que ya se graficó y se lo pasa a `ReporteVentasService`, que
compila y llena las plantillas JRXML (portada con KPIs y, por cada corte,
una gráfica JasperReports/JFreeChart más su tabla: concierto, artista y
vendedor en barras, ventas por día en línea y boletos por localidad en
pastel) y las concatena en un solo PDF vía
`JRPdfExporter` — no son subreports XML, son cuatro `JasperPrint`
independientes fusionados al exportar, más simple de mantener sin un
diseñador visual.

Dos detalles de JasperReports que no son obvios y costaron una vuelta de
verificación: los parámetros del reporte deben viajar en un `HashMap`
mutable (JasperReports hace `.put()` internamente; `Map.of()` explota con
`UnsupportedOperationException`), y las filas de datos deben viajar como
`Map<String, ?>` vía `JRMapCollectionDataSource`, no como los `record` de
`CatalogClient` — los accessors de un record son `x()`, no `getX()`, y el
`JRBeanCollectionDataSource` que lee JavaBeans no los reconoce.

Se fijó la dependencia en `jasperreports:6.21.4` a propósito: la rama 7.x
migró de `javax` a `jakarta`, y este proyecto no tiene nada de eso — 6.21.x
es la última versión antes de ese cambio. Verificado generando un PDF real
de 6 páginas contra el dashboard de datos reales en Neon, renderizado a
imagen para revisar las gráficas, y también ejecutándolo con el toolkit de
JavaFX activo (las gráficas usan AWT; no hubo choque).

Dos detalles de las gráficas: JFreeChart recorta a "..." las etiquetas del
eje X más anchas que su categoría, y Jasper no expone ese ajuste en el XML,
por eso `por_dia.jrxml` usa `EtiquetasVerticalesCustomizer` (rota las
etiquetas). Y cuando no hay ventas con los filtros elegidos la gráfica se
oculta (`hayDatos`) y se imprime un aviso, en vez de ejes vacíos con "-0".


## Fechas y horas

Todas las fechas y horas de la API son **hora local de Guatemala, sin zona**, en
formato `AAAA-MM-DDTHH:mm:ss` (por ejemplo `2026-11-15T20:00:00`). No llevan `Z`
ni desplazamiento: la columna `TIMESTAMP` de Postgres no guarda zona, así que
cualquier zona que se enviara se descartaba en silencio y la hora se corría.

- **Lectura:** `node-pg` convertía estas columnas a `Date` interpretándolas en la
  zona del proceso, de modo que el mismo dato salía distinto según dónde corriera
  el servidor (verificado: guardar `20:00` devolvía `02:00Z` del día siguiente).
  Ahora el parser de `TIMESTAMP` (`src/db/timestamps.js`) devuelve el texto tal cual.
- **Escritura (`POST`/`PUT /conciertos`):** `fecha_concierto` acepta
  `AAAA-MM-DDTHH:mm` o `AAAA-MM-DDTHH:mm:ss`. Una fecha con `Z` o con
  desplazamiento, un día inexistente, una hora fuera de rango o una fecha sin hora
  responden `400`.
- **`ventas.fecha_venta`** toma por defecto `NOW() AT TIME ZONE 'America/Guatemala'`
  (antes `NOW()`, que en Neon es UTC). Las ventas anteriores a este cambio quedaron
  guardadas en UTC; las de demostración sembradas a mano ya estaban en hora local.

El cliente de escritorio muestra estas fechas como `15 nov 2026, 20:00`
(`Fechas.java`) y en el formulario de conciertos las pide por separado: selector
de fecha y hora/minutos en 24 h.


## Boletos y control de acceso

**Un boleto es una persona.** Al registrar una venta, cada renglón de `detalle_ventas` con
`cantidad` N genera N filas en `boletos`, cada una con su propio código, dentro de la misma
transacción. El código tiene la forma `SVBGUA-V{venta}-D{renglon}-B{n}-{token}` y el token
son 8 caracteres aleatorios (sin 0, O, 1, I ni L): sin él, conocer un código permitiría
deducir los demás (V30-D34, V30-D35...) y colarse con uno inventado.

`GET /ventas/:id/boletos` devuelve **un elemento por persona**:
`{ id_boleto, id_detalle, titulo_evento, nombre_artistico, nombre_localidad, numero, cantidad,
codigo, ingresado, qr }`, donde `numero` de `cantidad` ubica el boleto dentro de su renglón y
`qr` codifica exactamente `codigo`. El correo adjunta un QR por boleto.

**Escanear un QR no registra nada.** El QR solo contiene el código; escanearlo con una cámara
normal no llama a ningún servicio. El ingreso lo registra una persona autenticada que envía el
código a la API. Así nadie "gasta" el boleto de otro por curiosidad.

| Método | Ruta | Acceso | Qué hace |
|--------|------|--------|----------|
| POST | `/accesos` | administrador, acceso | Registra el ingreso del boleto `{ "codigo": "..." }` |
| GET | `/accesos/resumen` | administrador, acceso | Por concierto: `vendidos` e `ingresados` |
| GET | `/accesos?concierto=` | administrador, acceso | Últimos 50 ingresos (opcionalmente de un concierto) |

`POST /accesos` normaliza el código (espacios y mayúsculas) y responde: `200` con los datos
del boleto y `fecha_ingreso`; `400` sin código; `404` si no existe; `409` si ya ingresó
(`"Este boleto ya ingresó el 15/10/2026 21:52."`) o si el concierto está `cancelado` o
`finalizado`. Se marca con un único `UPDATE ... WHERE fecha_ingreso IS NULL`, así que si dos
escáneres leen el mismo boleto a la vez la base deja pasar a uno solo (verificado con 10
peticiones simultáneas: una `200` y nueve `409`).

**Rol `acceso` (control de acceso).** Solo registra y consulta ingresos. No puede vender, ni
ver el historial, ni usar el resto del catálogo de administración: las rutas de `/ventas` ahora
exigen administrador o vendedor.

**Migración.** Una base creada con la versión anterior se actualiza con
`database/migrations/001_boletos_y_control_de_acceso.sql` (rol, tabla `boletos` y boletos de
las ventas existentes) y `database/migrations/002_metodo_pago.sql` (columna
`ventas.metodo_pago`, con las ventas existentes como `tarjeta`); ambas se pueden ejecutar
más de una vez. Una base nueva solo necesita
`schema.sql`. El seed agrega el usuario `puerta1` (rol `acceso`).


## Escáner web (`/acceso/`)

Página que sirve el propio backend para registrar ingresos desde el navegador de un celular o una
laptop, con la cámara. Usa la misma API y el mismo login (`POST /auth/login`, `POST /accesos`), así
que lo que se escanea ahí aparece en la pantalla de Control de acceso de la app de escritorio.

| Ruta | Qué sirve |
|------|-----------|
| `GET /acceso/` | La página (HTML, `scanner.css`, `scanner.js`, `nucleo.js`) |
| `GET /acceso/jsqr.js` | Librería de lectura de QR (`jsQR`), servida desde `node_modules` |
| `GET /acceso/enlace.png` | QR con la dirección de la propia página, para abrirla desde el celular |

- **HTTPS.** Los navegadores solo entregan la cámara en páginas HTTPS (o `localhost`). En Render la
  dirección ya es HTTPS; en local solo funciona desde la misma máquina.
- **Acceso.** La página es pública, pero no hace nada sin iniciar sesión con un usuario
  `administrador` o `acceso`; con otro rol la propia página lo rechaza y la API devuelve 403.
- **Seguridad de la página.** `Content-Security-Policy` estricta: solo carga scripts y estilos de este
  mismo servidor, sin código en línea, y solo se conecta a este mismo servidor; `Permissions-Policy`
  permite la cámara únicamente al propio sitio. La sesión vive en `sessionStorage` (se borra al cerrar
  la pestaña).
- **Lectura.** Cada ~120 ms toma un cuadro de la cámara y lo decodifica en el navegador. Un código
  se ignora durante 5 s después de leerlo (el QR sigue a la vista), un QR que no tiene la forma de un
  boleto de SVB-GUA se descarta sin llamar al servidor, y cada lectura va con sonido y vibración
  distintos para aceptado/rechazado.
- La lógica sin navegador está en `public/acceso/nucleo.js` y se prueba en Node.
