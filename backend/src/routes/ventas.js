import express from 'express';
import { authenticateToken, authorizeRoles } from '../middleware/auth.js';
import { InventarioNoEncontradoError, DisponibilidadInsuficienteError } from '../repositories/ventas.js';
import { MailerError } from '../services/mailer.js';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Un boleto por persona: un renglón con cantidad 3 produce 3 boletos, cada uno con su propio
// código y QR. Así el ingreso se controla persona por persona.
async function construirBoletos(venta, qrProvider) {
  const boletos = venta.items.flatMap((item) => (item.boletos ?? []).map((boleto) => ({
    id_boleto: boleto.id_boleto,
    id_detalle: item.id_detalle,
    titulo_evento: item.titulo_evento,
    nombre_artistico: item.nombre_artistico,
    nombre_localidad: item.nombre_localidad,
    numero: boleto.numero,
    cantidad: item.cantidad,
    codigo: boleto.codigo,
    ingresado: Boolean(boleto.fecha_ingreso),
  })));
  return Promise.all(boletos.map(async (boleto) => ({ ...boleto, qr: await qrProvider.generar(boleto.codigo) })));
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

const METODOS_PAGO = ['efectivo', 'tarjeta'];

// Un cliente que no manda metodo_pago es uno anterior al pago en efectivo, cuyo único cobro era el
// simulador de tarjeta: por eso la ausencia se registra como 'tarjeta' y no como un error.
function validarMetodoPago(raw) {
  if (raw === undefined) return { metodoPago: 'tarjeta' };
  if (typeof raw !== 'string' || !METODOS_PAGO.includes(raw)) {
    return { error: `metodo_pago debe ser uno de: ${METODOS_PAGO.join(', ')}.` };
  }
  return { metodoPago: raw };
}

export function createVentaRouter({ ventaRepository, qrProvider, facturaProvider, mailer, jwtSecret }) {
  const router = express.Router();
  // El rol de control de acceso solo valida ingresos: no vende ni consulta ventas.
  const requireAuth = [authenticateToken(jwtSecret), authorizeRoles('administrador', 'vendedor')];

  router.post('/', requireAuth, async (req, res, next) => {
    const { error, items } = validarItems(req.body?.items);
    if (error) return res.status(400).json({ error });
    const { error: errorPago, metodoPago } = validarMetodoPago(req.body?.metodo_pago);
    if (errorPago) return res.status(400).json({ error: errorPago });

    try {
      const venta = await ventaRepository.crear({ idVendedor: req.user.id_usuario, items, metodoPago });
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

  // El vendedor solo ve su propio historial (no puede pedir el de otro
  // vendedor); el administrador puede filtrar por vendedor y/o concierto.
  router.get('/', requireAuth, async (req, res, next) => {
    const esAdmin = req.user.rol === 'administrador';

    if (req.query.vendedor !== undefined && (!esAdmin || Number.isNaN(Number(req.query.vendedor)))) {
      return res.status(400).json({ error: 'vendedor debe ser un id numérico (solo administrador puede filtrar).' });
    }
    if (req.query.concierto !== undefined && Number.isNaN(Number(req.query.concierto))) {
      return res.status(400).json({ error: 'concierto debe ser un id numérico.' });
    }

    const idVendedor = esAdmin
      ? (req.query.vendedor !== undefined ? Number(req.query.vendedor) : null)
      : req.user.id_usuario;
    const idConcierto = req.query.concierto !== undefined ? Number(req.query.concierto) : null;

    try {
      res.json(await ventaRepository.listar({ idVendedor, idConcierto }));
    } catch (error) {
      next(error);
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
      // La factura se genera antes de enviar: si falla, no sale un correo a medias (boletos sin su factura).
      const factura = await facturaProvider.generar({ venta, email: email.trim() });
      await mailer.enviarBoletos({ destinatario: email.trim(), venta, boletos, factura });
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
