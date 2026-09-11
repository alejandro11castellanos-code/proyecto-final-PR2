import express from 'express';
import { authenticateToken } from '../middleware/auth.js';
import { InventarioNoEncontradoError, DisponibilidadInsuficienteError } from '../repositories/ventas.js';
import { codigoBoleto } from '../services/qrcode.js';
import { MailerError } from '../services/mailer.js';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

async function construirBoletos(venta, qrProvider) {
  return Promise.all(venta.items.map(async (item) => {
    const codigo = codigoBoleto(venta.id_venta, item.id_detalle);
    return {
      id_detalle: item.id_detalle,
      titulo_evento: item.titulo_evento,
      nombre_localidad: item.nombre_localidad,
      cantidad: item.cantidad,
      codigo,
      qr: await qrProvider.generar(codigo),
    };
  }));
}

function validarItems(rawItems) {
  if (!Array.isArray(rawItems) || rawItems.length === 0) {
    return { error: 'items debe ser una lista con al menos un elemento.' };
  }

  const vistos = new Set();
  const items = [];
  for (const raw of rawItems) {
    const idInventario = Number(raw?.id_inventario);
    const cantidad = Number(raw?.cantidad);
    if (!Number.isInteger(idInventario) || idInventario <= 0
        || !Number.isInteger(cantidad) || cantidad <= 0) {
      return { error: 'Cada ítem necesita id_inventario y cantidad (enteros positivos).' };
    }
    if (vistos.has(idInventario)) {
      return {
        error: `El inventario ${idInventario} está repetido; agrupá la cantidad en un solo ítem.`,
      };
    }
    vistos.add(idInventario);
    items.push({ idInventario, cantidad });
  }
  return { items };
}

export function createVentaRouter({ ventaRepository, qrProvider, mailer, jwtSecret }) {
  const router = express.Router();
  const requireAuth = authenticateToken(jwtSecret);

  router.post('/', requireAuth, async (req, res, next) => {
    const { error, items } = validarItems(req.body?.items);
    if (error) return res.status(400).json({ error });

    try {
      const venta = await ventaRepository.crear({ idVendedor: req.user.id_usuario, items });
      res.status(201).json(venta);
    } catch (thrown) {
      if (thrown instanceof InventarioNoEncontradoError) {
        return res.status(404).json({ error: thrown.message });
      }
      if (thrown instanceof DisponibilidadInsuficienteError) {
        return res.status(409).json({ error: thrown.message });
      }
      next(thrown);
    }
  });

  router.get('/:id', requireAuth, async (req, res, next) => {
    try {
      const venta = await ventaRepository.findById(req.params.id);
      if (!venta) return res.status(404).json({ error: 'Venta no encontrada.' });
      res.json(venta);
    } catch (error) {
      next(error);
    }
  });

  router.get('/:id/boletos', requireAuth, async (req, res, next) => {
    try {
      const venta = await ventaRepository.findById(req.params.id);
      if (!venta) return res.status(404).json({ error: 'Venta no encontrada.' });
      res.json({ id_venta: venta.id_venta, boletos: await construirBoletos(venta, qrProvider) });
    } catch (error) {
      next(error);
    }
  });

  router.post('/:id/enviar', requireAuth, async (req, res, next) => {
    const email = req.body?.email;
    if (typeof email !== 'string' || !EMAIL_REGEX.test(email.trim())) {
      return res.status(400).json({ error: 'email es obligatorio y debe tener un formato válido.' });
    }

    try {
      const venta = await ventaRepository.findById(req.params.id);
      if (!venta) return res.status(404).json({ error: 'Venta no encontrada.' });

      const boletos = await construirBoletos(venta, qrProvider);
      await mailer.enviarBoletos({ destinatario: email.trim(), venta, boletos });
      res.json({ enviado: true, destinatario: email.trim() });
    } catch (error) {
      if (error instanceof MailerError) {
        return res.status(502).json({ error: error.message });
      }
      next(error);
    }
  });

  return router;
}
