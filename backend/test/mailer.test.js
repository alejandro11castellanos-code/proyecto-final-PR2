import test from 'node:test';
import assert from 'node:assert/strict';
import { createMailer, MailerError } from '../src/services/mailer.js';

const venta = { id_venta: 7, total_venta: '450.00' };
const boletos = [
  {
    titulo_evento: 'Gira <Aniversario>',
    nombre_localidad: 'VIP',
    numero: 1,
    cantidad: 2,
    codigo: 'SVB-AAA',
    qr: 'data:image/png;base64,QUJD',
  },
  {
    titulo_evento: 'Gira <Aniversario>',
    nombre_localidad: 'VIP',
    numero: 2,
    cantidad: 2,
    codigo: 'SVB-BBB',
    qr: 'data:image/png;base64,REVG',
  },
];

function fetchFalso(respuesta = { status: 201, body: { messageId: '<abc@brevo>' } }) {
  const llamadas = [];
  const fetchImpl = async (url, opciones) => {
    llamadas.push({ url, opciones });
    return {
      ok: respuesta.status >= 200 && respuesta.status < 300,
      status: respuesta.status,
      json: async () => respuesta.body,
    };
  };
  return { fetchImpl, llamadas };
}

test('con apiKey envía los boletos por la API HTTPS de Brevo', async () => {
  const { fetchImpl, llamadas } = fetchFalso();
  const mailer = createMailer({ apiKey: 'xkeysib-secreta', from: 'SVB-GUA <boletos@ejemplo.com>', fetchImpl });

  await mailer.enviarBoletos({ destinatario: 'cliente@correo.com', venta, boletos });

  assert.equal(llamadas.length, 1);
  const { url, opciones } = llamadas[0];
  assert.equal(url, 'https://api.brevo.com/v3/smtp/email');
  assert.equal(opciones.method, 'POST');
  assert.equal(opciones.headers['api-key'], 'xkeysib-secreta');
  assert.equal(opciones.headers['content-type'], 'application/json');

  const cuerpo = JSON.parse(opciones.body);
  assert.deepEqual(cuerpo.sender, { name: 'SVB-GUA', email: 'boletos@ejemplo.com' });
  assert.deepEqual(cuerpo.to, [{ email: 'cliente@correo.com' }]);
  assert.equal(cuerpo.subject, 'Tus boletos — venta #7');
  // Los QR viajan como adjuntos PNG en base64, sin el prefijo data:.
  assert.deepEqual(cuerpo.attachment, [
    { name: 'boleto-1.png', content: 'QUJD' },
    { name: 'boleto-2.png', content: 'REVG' },
  ]);
  assert.match(cuerpo.htmlContent, /SVB-AAA/);
  assert.match(cuerpo.htmlContent, /SVB-BBB/);
  assert.match(cuerpo.htmlContent, /boleto-1\.png/);
});

test('el remitente puede ser solo un correo, sin nombre', async () => {
  const { fetchImpl, llamadas } = fetchFalso();
  const mailer = createMailer({ apiKey: 'k', from: 'boletos@ejemplo.com', fetchImpl });

  await mailer.enviarBoletos({ destinatario: 'a@b.co', venta, boletos });

  assert.deepEqual(JSON.parse(llamadas[0].opciones.body).sender, { email: 'boletos@ejemplo.com' });
});

test('escapa el HTML de los datos del concierto', async () => {
  const { fetchImpl, llamadas } = fetchFalso();
  const mailer = createMailer({ apiKey: 'k', from: 'a@b.co', fetchImpl });

  await mailer.enviarBoletos({ destinatario: 'a@b.co', venta, boletos });

  const html = JSON.parse(llamadas[0].opciones.body).htmlContent;
  assert.ok(!html.includes('<Aniversario>'), 'el título no debe inyectar HTML');
  assert.match(html, /Gira &lt;Aniversario&gt;/);
});

test('un rechazo de Brevo se convierte en MailerError con su mensaje', async () => {
  const { fetchImpl } = fetchFalso({ status: 401, body: { code: 'unauthorized', message: 'Key not found' } });
  const mailer = createMailer({ apiKey: 'mala', from: 'a@b.co', fetchImpl });

  await assert.rejects(
    mailer.enviarBoletos({ destinatario: 'a@b.co', venta, boletos }),
    (error) => error instanceof MailerError && /Key not found/.test(error.message),
  );
});

test('un fallo de red se convierte en MailerError', async () => {
  const fetchImpl = async () => {
    throw new Error('getaddrinfo ENOTFOUND');
  };
  const mailer = createMailer({ apiKey: 'k', from: 'a@b.co', fetchImpl });

  await assert.rejects(
    mailer.enviarBoletos({ destinatario: 'a@b.co', venta, boletos }),
    (error) => error instanceof MailerError && /ENOTFOUND/.test(error.message),
  );
});

test('sin apiKey ni SMTP_HOST responde con un error de configuración claro', async () => {
  const mailer = createMailer({ apiKey: undefined, host: undefined, from: 'a@b.co' });

  await assert.rejects(
    mailer.enviarBoletos({ destinatario: 'a@b.co', venta, boletos }),
    (error) => error instanceof MailerError && /BREVO_API_KEY/.test(error.message),
  );
});

test('con apiKey y SMTP_HOST a la vez, gana la API de Brevo', async () => {
  const { fetchImpl, llamadas } = fetchFalso();
  const mailer = createMailer({ apiKey: 'k', host: 'smtp.ejemplo.com', from: 'a@b.co', fetchImpl });

  await mailer.enviarBoletos({ destinatario: 'a@b.co', venta, boletos });

  assert.equal(llamadas.length, 1);
});
