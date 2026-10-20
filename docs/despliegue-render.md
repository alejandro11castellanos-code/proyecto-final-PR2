# Publicar el backend en Render

La base de datos ya está en **Neon**; Render solo ejecuta la API (Node + Express) y se conecta a
Neon con `DATABASE_URL`. El archivo `render.yaml` de la raíz describe el servicio.

## Pasos

1. **Cuenta y repositorio.** Entrar a <https://render.com> (se puede usar la cuenta de GitHub) y
   autorizar el acceso al repositorio `proyecto-final-PR2`.
2. **Crear el servicio.** *New +* -> **Blueprint** -> elegir el repositorio. Render lee
   `render.yaml` y propone el servicio `svb-gua-backend` (plan *Free*).
   Conviene elegir la región más cercana a la de Neon.
3. **Variables de entorno.** Render pide `DATABASE_URL`: pegar la cadena de conexión de Neon
   (la misma de `backend/.env`, sección *Connection string* del panel de Neon).
   `JWT_SECRET` lo genera Render solo.
4. **Desplegar** (*Apply*). El primer despliegue tarda unos minutos.
5. **Comprobar.** Abrir `https://<nombre>.onrender.com/health`; debe responder
   `{"status":"ok","service":"svb-gua-backend",...}`.
6. **Conectar la app.** En el login del cliente: **Cambiar** -> pegar esa URL -> *Probar y guardar*.

## Que la app ya apunte al servidor (opcional)

Para repartir un instalador que ya traiga la dirección (los usuarios igual pueden cambiarla):

```bash
cd desktop-client
SVB_API_URL=https://<nombre>.onrender.com ./packaging/crear-instalador-mac.sh
```

## Correo (opcional)

Enviar boletos por correo necesita SMTP. En Render: *Environment* -> agregar `SMTP_HOST`,
`SMTP_PORT`, `SMTP_USER`, `SMTP_PASS` y `SMTP_FROM`. Sin ellos, el envío responde 502 con un
mensaje claro y todo lo demás funciona.

## Qué esperar del plan gratuito

- El servicio **se duerme tras ~15 minutos sin uso**; la primera petición después tarda de 30 a 60
  segundos en responder (la app mostrará "Comprobando..." o un error de conexión; basta reintentar).
- Con `autoDeploy: true`, cada `git push` a `main` vuelve a desplegar.
- Los registros están en la pestaña *Logs* del servicio.

## Antes de compartir la dirección

Con el servidor público, **cualquiera que conozca la URL puede intentar entrar**. El login limita
los intentos fallidos (20 por IP cada 15 minutos), pero las cuentas de desarrollo del seed
(`admin`/`admin123`, `vendedor1`/`vendedor123`, `puerta1`/`puerta123`) tienen contraseñas
conocidas. Cambiarlas desde la app (*Usuarios* -> seleccionar -> **Restablecer contraseña**) antes
de dar la URL a otras personas.
