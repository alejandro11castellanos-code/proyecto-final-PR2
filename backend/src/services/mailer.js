import nodemailer from 'nodemailer';

export class MailerError extends Error {
  constructor(message) {
    super(message);
    this.name = 'MailerError';
  }
}

function renderHtml(venta, boletos) {
  const filas = boletos
    .map((boleto, index) => `
      <li style="margin-bottom:16px;">
        <strong>${boleto.titulo_evento}</strong> — ${boleto.nombre_localidad} × ${boleto.cantidad}
        <br>
        <img src="cid:boleto${index}" alt="Código QR del boleto" width="180" height="180">
      </li>`)
    .join('');

  return `
    <h1>Venta #${venta.id_venta}</h1>
    <p>Total: Q ${venta.total_venta}</p>
    <ul style="list-style:none; padding:0;">${filas}</ul>`;
}

/**
 * Envía los boletos de una venta por correo. Sin `SMTP_HOST` configurado, no
 * hay a dónde mandar el mensaje y se rechaza con un error claro en vez de
 * fallar de forma confusa dentro de nodemailer.
 */
export function createMailer({
  host = process.env.SMTP_HOST,
  port = Number(process.env.SMTP_PORT ?? 587),
  user = process.env.SMTP_USER,
  pass = process.env.SMTP_PASS,
  from = process.env.SMTP_FROM || 'SVB-GUA <no-reply@svb-gua.local>',
} = {}) {
  return {
    async enviarBoletos({ destinatario, venta, boletos }) {
      if (!host) {
        throw new MailerError('El servidor de correo no está configurado (SMTP_HOST).');
      }

      const transporter = nodemailer.createTransport({
        host,
        port,
        secure: port === 465,
        auth: user && pass ? { user, pass } : undefined,
      });

      const attachments = boletos.map((boleto, index) => ({
        filename: `boleto-${index + 1}.png`,
        content: boleto.qr.replace(/^data:image\/png;base64,/, ''),
        encoding: 'base64',
        cid: `boleto${index}`,
      }));

      try {
        return await transporter.sendMail({
          from,
          to: destinatario,
          subject: `Tus boletos — venta #${venta.id_venta}`,
          html: renderHtml(venta, boletos),
          attachments,
        });
      } catch (error) {
        throw new MailerError(`No se pudo enviar el correo: ${error.message}`);
      }
    },
  };
}
