import express from 'express';
import { authenticateToken, authorizeRoles } from '../middleware/auth.js';

function isBlank(value) {
  return typeof value !== 'string' || !value.trim();
}

function validate(body) {
  const { nombre_artistico, genero_musical, pais_origen } = body ?? {};
  if (isBlank(nombre_artistico) || isBlank(genero_musical)) {
    return { error: 'nombre_artistico y genero_musical son obligatorios.' };
  }
  return {
    data: {
      nombre_artistico: nombre_artistico.trim(),
      genero_musical: genero_musical.trim(),
      pais_origen: typeof pais_origen === 'string' && pais_origen.trim() ? pais_origen.trim() : null,
    },
  };
}

export function createArtistaRouter({ artistaRepository, jwtSecret }) {
  const router = express.Router();
  const requireAuth = authenticateToken(jwtSecret);
  const requireAdmin = authorizeRoles('administrador');

  router.get('/', requireAuth, async (_req, res, next) => {
    try {
      res.json(await artistaRepository.findAll());
    } catch (error) {
      next(error);
    }
  });

  router.get('/:id', requireAuth, async (req, res, next) => {
    try {
      const artista = await artistaRepository.findById(req.params.id);
      if (!artista) return res.status(404).json({ error: 'Artista no encontrado.' });
      res.json(artista);
    } catch (error) {
      next(error);
    }
  });

  router.post('/', requireAuth, requireAdmin, async (req, res, next) => {
    const { error, data } = validate(req.body);
    if (error) return res.status(400).json({ error });
    try {
      res.status(201).json(await artistaRepository.create(data));
    } catch (error_) {
      next(error_);
    }
  });

  router.put('/:id', requireAuth, requireAdmin, async (req, res, next) => {
    const { error, data } = validate(req.body);
    if (error) return res.status(400).json({ error });
    try {
      const artista = await artistaRepository.update(req.params.id, data);
      if (!artista) return res.status(404).json({ error: 'Artista no encontrado.' });
      res.json(artista);
    } catch (error_) {
      next(error_);
    }
  });

  router.delete('/:id', requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const removed = await artistaRepository.remove(req.params.id);
      if (!removed) return res.status(404).json({ error: 'Artista no encontrado.' });
      res.status(204).end();
    } catch (error) {
      if (error.code === '23503') {
        return res.status(409).json({
          error: 'No se puede eliminar: el artista tiene conciertos asociados.',
        });
      }
      next(error);
    }
  });

  return router;
}
