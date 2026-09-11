import express from 'express';
import bcrypt from 'bcryptjs';
import { authenticateToken, authorizeRoles } from '../middleware/auth.js';

const ROLES_VALIDOS = ['administrador', 'vendedor'];
const CONTRASENA_MINIMA = 6;

function isBlank(value) {
  return typeof value !== 'string' || !value.trim();
}

function validarContrasena(contrasena) {
  return typeof contrasena === 'string' && contrasena.length >= CONTRASENA_MINIMA;
}

export function createUsuarioRouter({ userRepository, jwtSecret }) {
  const router = express.Router();
  const requireAuth = authenticateToken(jwtSecret);
  const requireAdmin = authorizeRoles('administrador');

  router.get('/', requireAuth, requireAdmin, async (_req, res, next) => {
    try {
      res.json(await userRepository.findAll());
    } catch (error) {
      next(error);
    }
  });

  router.post('/', requireAuth, requireAdmin, async (req, res, next) => {
    const { nombre_usuario, contrasena, nombre_completo, rol } = req.body ?? {};
    if (isBlank(nombre_usuario) || isBlank(nombre_completo) || !ROLES_VALIDOS.includes(rol)) {
      return res.status(400).json({
        error: `nombre_usuario y nombre_completo son obligatorios; rol debe ser uno de: ${ROLES_VALIDOS.join(', ')}.`,
      });
    }
    if (!validarContrasena(contrasena)) {
      return res.status(400).json({
        error: `La contraseña debe tener al menos ${CONTRASENA_MINIMA} caracteres.`,
      });
    }

    try {
      const contrasenaHash = await bcrypt.hash(contrasena, 10);
      const usuario = await userRepository.create({
        nombre_usuario: nombre_usuario.trim(),
        contrasena_hash: contrasenaHash,
        nombre_completo: nombre_completo.trim(),
        rol,
      });
      res.status(201).json(usuario);
    } catch (error) {
      if (error.code === '23505') {
        return res.status(409).json({ error: 'Ya existe un usuario con ese nombre.' });
      }
      next(error);
    }
  });

  router.put('/:id', requireAuth, requireAdmin, async (req, res, next) => {
    const { nombre_completo, rol } = req.body ?? {};
    if (isBlank(nombre_completo) || !ROLES_VALIDOS.includes(rol)) {
      return res.status(400).json({
        error: `nombre_completo es obligatorio y rol debe ser uno de: ${ROLES_VALIDOS.join(', ')}.`,
      });
    }

    try {
      const usuario = await userRepository.update(req.params.id, {
        nombre_completo: nombre_completo.trim(),
        rol,
      });
      if (!usuario) return res.status(404).json({ error: 'Usuario no encontrado.' });
      res.json(usuario);
    } catch (error) {
      next(error);
    }
  });

  router.put('/:id/contrasena', requireAuth, requireAdmin, async (req, res, next) => {
    const { contrasena } = req.body ?? {};
    if (!validarContrasena(contrasena)) {
      return res.status(400).json({
        error: `La contraseña debe tener al menos ${CONTRASENA_MINIMA} caracteres.`,
      });
    }

    try {
      const contrasenaHash = await bcrypt.hash(contrasena, 10);
      const actualizado = await userRepository.updatePassword(req.params.id, contrasenaHash);
      if (!actualizado) return res.status(404).json({ error: 'Usuario no encontrado.' });
      res.json({ actualizado: true });
    } catch (error) {
      next(error);
    }
  });

  router.delete('/:id', requireAuth, requireAdmin, async (req, res, next) => {
    if (Number(req.params.id) === req.user.id_usuario) {
      return res.status(400).json({ error: 'No podés eliminar tu propio usuario.' });
    }

    try {
      const removed = await userRepository.remove(req.params.id);
      if (!removed) return res.status(404).json({ error: 'Usuario no encontrado.' });
      res.status(204).end();
    } catch (error) {
      if (error.code === '23503') {
        return res.status(409).json({
          error: 'No se puede eliminar: el usuario tiene ventas registradas.',
        });
      }
      next(error);
    }
  });

  return router;
}
