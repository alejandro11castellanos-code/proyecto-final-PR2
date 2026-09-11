import express from 'express';
import { authenticateToken, authorizeRoles } from '../middleware/auth.js';
import { ESTADOS_VALIDOS } from '../repositories/conciertos.js';

function isBlank(value) {
  return typeof value !== 'string' || !value.trim();
}

function parseFecha(value) {
  const fecha = new Date(value);
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

export function createConciertoRouter({ conciertoRepository, artistaRepository, jwtSecret }) {
  const router = express.Router();
  const requireAuth = authenticateToken(jwtSecret);
  const requireAdmin = authorizeRoles('administrador');

  async function validate(body, { requireEstado = false } = {}) {
    const { id_artista, titulo_evento, fecha_concierto, recinto, estado } = body ?? {};

    if (
      !Number.isInteger(Number(id_artista)) ||
      isBlank(titulo_evento) ||
      isBlank(fecha_concierto) ||
      isBlank(recinto)
    ) {
      return {
        error: 'id_artista, titulo_evento, fecha_concierto y recinto son obligatorios.',
      };
    }
    if (requireEstado && isBlank(estado)) {
      return { error: 'estado es obligatorio.' };
    }
    if (estado !== undefined && estado !== null && !ESTADOS_VALIDOS.includes(estado)) {
      return { error: `estado debe ser uno de: ${ESTADOS_VALIDOS.join(', ')}.` };
    }

    const fecha = parseFecha(fecha_concierto);
    if (!fecha) {
      return { error: 'fecha_concierto no es una fecha válida.' };
    }

    const artista = await artistaRepository.findById(Number(id_artista));
    if (!artista) {
      return { error: 'El artista indicado no existe.' };
    }

    return {
      data: {
        id_artista: Number(id_artista),
        titulo_evento: titulo_evento.trim(),
        fecha_concierto: fecha.toISOString(),
        recinto: recinto.trim(),
        estado: estado ?? null,
      },
    };
  }

  router.get('/', requireAuth, async (_req, res, next) => {
    try {
      res.json(await conciertoRepository.findAll());
    } catch (error) {
      next(error);
    }
  });

  router.get('/:id', requireAuth, async (req, res, next) => {
    try {
      const concierto = await conciertoRepository.findById(req.params.id);
      if (!concierto) return res.status(404).json({ error: 'Concierto no encontrado.' });
      res.json(concierto);
    } catch (error) {
      next(error);
    }
  });

  router.post('/', requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const { error, data } = await validate(req.body);
      if (error) return res.status(400).json({ error });
      res.status(201).json(await conciertoRepository.create(data));
    } catch (error) {
      next(error);
    }
  });

  router.put('/:id', requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const { error, data } = await validate(req.body, { requireEstado: true });
      if (error) return res.status(400).json({ error });
      const concierto = await conciertoRepository.update(req.params.id, data);
      if (!concierto) return res.status(404).json({ error: 'Concierto no encontrado.' });
      res.json(concierto);
    } catch (error) {
      next(error);
    }
  });

  router.delete('/:id', requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const removed = await conciertoRepository.remove(req.params.id);
      if (!removed) return res.status(404).json({ error: 'Concierto no encontrado.' });
      res.status(204).end();
    } catch (error) {
      if (error.code === '23503') {
        return res.status(409).json({
          error: 'No se puede eliminar: el concierto tiene inventario o ventas asociadas.',
        });
      }
      next(error);
    }
  });

  return router;
}
