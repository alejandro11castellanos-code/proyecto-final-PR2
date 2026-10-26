import nodemailer from 'nodemailer';

export class MailerError extends Error {
  constructor(message) {
    super(message);
    this.name = 'MailerError';
  }
}

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

function escapeHtml(valor) {
  return String(valor).replace(/[&<>"']/g, (caracter) => ESCAPES[caracter]);
}

// `incrustarQr` (SMTP): el QR va dentro del cuerpo vía cid. Sin él (API HTTPS de
// Brevo, que no admite cid) el QR viaja como adjunto y el cuerpo lo indica.
function renderHtml(venta, boletos, { incrustarQr, conFactura }) {
  const filas = boletos
    .map((boleto, index) => `
      <li style="margin-bottom:16px;">
        <strong>${escapeHtml(boleto.titulo_evento)}</strong> — ${escapeHtml(boleto.nombre_localidad)}, boleto ${boleto.numero} de ${boleto.cantidad}
        <br>
        <span style="font-family:monospace;font-size:12px;">${escapeHtml(boleto.codigo)}</span>
        <br>
        ${incrustarQr
    ? `<img src="cid:boleto${index}" alt="Código QR del boleto" width="180" height="180">`
    : `<em>Código QR adjunto: boleto-${index + 1}.png</em>`}
      </li>`)
    .join('');

  return `
    <h1>Venta #${venta.id_venta}</h1>
    <p>Total: Q ${venta.total_venta}</p>
    ${conFactura ? '<p>Adjuntamos la factura de su compra (PDF).</p>' : ''}
    <ul style="list-style:none; padding:0;">${filas}</ul>`;
}

// "Nombre <correo@x.com>" -> { name, email }; un correo solo -> { email }.
function parsearRemitente(from) {
  const coincide = /^\s*(.*?)\s*<([^>]+)>\s*$/.exec(from);
  if (!coincide) return { email: from.trim() };
  const [, name, email] = coincide;
  return name ? { name, email } : { email };
}

const BREVO_URL = 'https://api.brevo.com/v3/smtp/email';
const ASUNTO = (venta) => `Tus boletos — venta #${venta.id_venta}`;
const SIN_PREFIJO = (qr) => qr.replace(/^data:image\/png;base64,/, '');

async function enviarPorBrevo({ apiKey, from, fetchImpl, destinatario, venta, boletos, factura }) {
  const cuerpo = {
    sender: parsearRemitente(from),
    to: [{ email: destinatario }],
    subject: ASUNTO(venta),
    htmlContent: renderHtml(venta, boletos, { incrustarQr: false, conFactura: Boolean(factura) }),
    attachment: [
      ...boletos.map((boleto, index) => ({
        name: `boleto-${index + 1}.png`,
        content: SIN_PREFIJO(boleto.qr),
      })),
      // La factura (PDF) viaja después de los QR.
      ...(factura ? [{ name: factura.nombre, content: factura.contenido.toString('base64') }] : []),
    ],
  };

  let respuesta;
  try {
    respuesta = await fetchImpl(BREVO_URL, {
      method: 'POST',
      headers: { 'api-key': apiKey, 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify(cuerpo),
      signal: AbortSignal.timeout(20000),
    });
  } catch (error) {
    throw new MailerError(`No se pudo enviar el correo: ${error.message}`);
  }

  if (!respuesta.ok) {
    let detalle = `HTTP ${respuesta.status}`;
    try {
      const datos = await respuesta.json();
      if (datos?.message) detalle = `${datos.message} (HTTP ${respuesta.status})`;
    } catch {
      // Brevo respondió sin JSON: queda solo el código HTTP.
    }
    throw new MailerError(`No se pudo enviar el correo: ${detalle}`);
  }
  return respuesta.json();
}

/**
 * Envía los boletos de una venta por correo. Con `BREVO_API_KEY` usa la API
 * HTTPS de Brevo (funciona en Render, que bloquea los puertos SMTP del plan
 * gratuito); sin ella, cae a SMTP si hay `SMTP_HOST`. Sin ninguno de los dos
 * no hay a dónde mandar el mensaje y se rechaza con un error claro.
 */
export function createMailer({
  apiKey = process.env.BREVO_API_KEY,
  fetchImpl = globalThis.fetch,
  host = process.env.SMTP_HOST,
  port = Number(process.env.SMTP_PORT ?? 587),
  user = process.env.SMTP_USER,
  pass = process.env.SMTP_PASS,
  from = process.env.MAIL_FROM || process.env.SMTP_FROM || 'SVB-GUA <no-reply@svb-gua.local>',
} = {}) {
  return {
    async enviarBoletos({ destinatario, venta, boletos, factura }) {
      if (apiKey) {
        return enviarPorBrevo({ apiKey, from, fetchImpl, destinatario, venta, boletos, factura });
      }
      if (!host) {
        throw new MailerError(
          'El servidor de correo no está configurado (BREVO_API_KEY o SMTP_HOST).',
        );
      }

      const transporter = nodemailer.createTransport({
        host,
        port,
        secure: port === 465,
        auth: user && pass ? { user, pass } : undefined,
      });

      const attachments = boletos.map((boleto, index) => ({
        filename: `boleto-${index + 1}.png`,
        content: SIN_PREFIJO(boleto.qr),
        encoding: 'base64',
        cid: `boleto${index}`,
      }));
      if (factura) {
        attachments.push({ filename: factura.nombre, content: factura.contenido, contentType: 'application/pdf' });
      }

      try {
        return await transporter.sendMail({
          from,
          to: destinatario,
          subject: ASUNTO(venta),
          html: renderHtml(venta, boletos, { incrustarQr: true, conFactura: Boolean(factura) }),
          attachments,
        });
      } catch (error) {
        throw new MailerError(`No se pudo enviar el correo: ${error.message}`);
      }
    },
  };
}
