import express from 'express';
import { authenticateToken, authorizeRoles } from '../middleware/auth.js';

function isPositiveInteger(value) {
  return typeof value !== 'boolean' && Number.isInteger(Number(value)) && Number(value) > 0;
}

function isValidPrecio(value) {
  const number = Number(value);
  return typeof value !== 'boolean' && value !== '' && value !== null && !Number.isNaN(number) && number >= 0;
}

/**
 * Rutas anidadas bajo /conciertos/:idConcierto/inventario. Requiere
 * mergeParams para leer :idConcierto desde el router padre.
 */
export function createInventarioRouter({
  inventarioRepository,
  conciertoRepository,
  localidadRepository,
  jwtSecret,
}) {
  const router = express.Router({ mergeParams: true });
  const requireAuth = authenticateToken(jwtSecret);
  const requireAdmin = authorizeRoles('administrador');

  async function findConcierto(req) {
    return conciertoRepository.findById(req.params.idConcierto);
  }

  // Busca el registro y confirma que pertenece al concierto de la URL, para
  // que no se pueda editar/borrar inventario de otro concierto por error.
  async function findInventarioDelConcierto(req) {
    const registro = await inventarioRepository.findById(req.params.idInventario);
    if (!registro || Number(registro.id_concierto) !== Number(req.params.idConcierto)) {
      return null;
    }
    return registro;
  }

  router.get('/', requireAuth, async (req, res, next) => {
    try {
      const concierto = await findConcierto(req);
      if (!concierto) return res.status(404).json({ error: 'Concierto no encontrado.' });
      res.json(await inventarioRepository.findAllByConcierto(req.params.idConcierto));
    } catch (error) {
      next(error);
    }
  });

  router.post('/', requireAuth, requireAdmin, async (req, res, next) => {
    const { id_localidad, precio, cantidad_total } = req.body ?? {};
    if (!isPositiveInteger(id_localidad) || !isValidPrecio(precio) || !isPositiveInteger(cantidad_total)) {
      return res.status(400).json({
        error: 'id_localidad, precio y cantidad_total son obligatorios y deben ser válidos.',
      });
    }

    try {
      const concierto = await findConcierto(req);
      if (!concierto) return res.status(404).json({ error: 'Concierto no encontrado.' });

      const localidad = await localidadRepository.findById(Number(id_localidad));
      if (!localidad) {
        return res.status(400).json({ error: 'La localidad indicada no existe.' });
      }

      const creado = await inventarioRepository.create({
        id_concierto: Number(req.params.idConcierto),
        id_localidad: Number(id_localidad),
        precio: Number(precio),
        cantidad_total: Number(cantidad_total),
      });
      res.status(201).json(await inventarioRepository.findById(creado.id_inventario));
    } catch (error) {
      if (error.code === '23505') {
        return res.status(409).json({
          error: 'Esa localidad ya tiene aforo asignado para este concierto.',
        });
      }
      next(error);
    }
  });

  router.put('/:idInventario', requireAuth, requireAdmin, async (req, res, next) => {
    const { precio, cantidad_total } = req.body ?? {};
    if (!isValidPrecio(precio) || !isPositiveInteger(cantidad_total)) {
      return res.status(400).json({
        error: 'precio y cantidad_total son obligatorios y deben ser válidos.',
      });
    }

    try {
      const concierto = await findConcierto(req);
      if (!concierto) return res.status(404).json({ error: 'Concierto no encontrado.' });

      const existente = await findInventarioDelConcierto(req);
      if (!existente) return res.status(404).json({ error: 'Registro de inventario no encontrado.' });

      const actualizado = await inventarioRepository.updatePrecioYAforo(req.params.idInventario, {
        precio: Number(precio),
        cantidad_total: Number(cantidad_total),
      });
      res.json(actualizado);
    } catch (error) {
      if (error.code === '23514') {
        return res.status(400).json({
          error: 'No se puede reducir el aforo por debajo de lo ya vendido.',
        });
      }
      next(error);
    }
  });

  router.delete('/:idInventario', requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const concierto = await findConcierto(req);
      if (!concierto) return res.status(404).json({ error: 'Concierto no encontrado.' });

      const existente = await findInventarioDelConcierto(req);
      if (!existente) return res.status(404).json({ error: 'Registro de inventario no encontrado.' });

      await inventarioRepository.remove(req.params.idInventario);
      res.status(204).end();
    } catch (error) {
      if (error.code === '23503') {
        return res.status(409).json({
          error: 'No se puede eliminar: ya tiene boletos vendidos.',
        });
      }
      next(error);
    }
  });

  return router;
}
