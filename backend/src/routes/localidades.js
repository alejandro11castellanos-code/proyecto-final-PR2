import express from 'express';
import { authenticateToken, authorizeRoles } from '../middleware/auth.js';

function isBlank(value) {
  return typeof value !== 'string' || !value.trim();
}

export function createLocalidadRouter({ localidadRepository, jwtSecret }) {
  const router = express.Router();
  const requireAuth = authenticateToken(jwtSecret);
  const requireAdmin = authorizeRoles('administrador');

  router.get('/', requireAuth, async (_req, res, next) => {
    try {
      res.json(await localidadRepository.findAll());
    } catch (error) {
      next(error);
    }
  });

  router.post('/', requireAuth, requireAdmin, async (req, res, next) => {
    const { nombre } = req.body ?? {};
    if (isBlank(nombre)) {
      return res.status(400).json({ error: 'nombre es obligatorio.' });
    }
    try {
      res.status(201).json(await localidadRepository.create({ nombre: nombre.trim() }));
    } catch (error) {
      if (error.code === '23505') {
        return res.status(409).json({ error: 'Ya existe una localidad con ese nombre.' });
      }
      next(error);
    }
  });

  router.put('/:id', requireAuth, requireAdmin, async (req, res, next) => {
    const { nombre } = req.body ?? {};
    if (isBlank(nombre)) {
      return res.status(400).json({ error: 'nombre es obligatorio.' });
    }
    try {
      const localidad = await localidadRepository.update(req.params.id, { nombre: nombre.trim() });
      if (!localidad) return res.status(404).json({ error: 'Localidad no encontrada.' });
      res.json(localidad);
    } catch (error) {
      if (error.code === '23505') {
        return res.status(409).json({ error: 'Ya existe una localidad con ese nombre.' });
      }
      next(error);
    }
  });

  router.delete('/:id', requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const removed = await localidadRepository.remove(req.params.id);
      if (!removed) return res.status(404).json({ error: 'Localidad no encontrada.' });
      res.status(204).end();
    } catch (error) {
      if (error.code === '23503') {
        return res.status(409).json({
          error: 'No se puede eliminar: la localidad tiene inventario asociado.',
        });
      }
      next(error);
    }
  });

  return router;
}
