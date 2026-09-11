import express from 'express';
import { authenticateToken } from '../middleware/auth.js';
import { InventarioNoEncontradoError, DisponibilidadInsuficienteError } from '../repositories/ventas.js';

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

export function createVentaRouter({ ventaRepository, jwtSecret }) {
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

  return router;
}
