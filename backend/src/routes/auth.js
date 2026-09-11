import express from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { authenticateToken } from '../middleware/auth.js';

function publicUser(user) {
  return {
    id_usuario: user.id_usuario,
    nombre_usuario: user.nombre_usuario,
    nombre_completo: user.nombre_completo,
    rol: user.rol,
  };
}

export function createAuthRouter({ userRepository, jwtSecret }) {
  const router = express.Router();

  router.post('/login', async (req, res, next) => {
    const username = req.body?.nombre_usuario?.trim();
    const password = req.body?.contrasena;

    if (!username || typeof password !== 'string' || !password.trim()) {
      return res.status(400).json({
        error: 'Usuario y contraseña son obligatorios.',
      });
    }

    try {
      const user = await userRepository.findByUsername(username);
      const validPassword = user
        ? await bcrypt.compare(password, user.contrasena_hash)
        : false;

      if (!user || !validPassword) {
        return res.status(401).json({ error: 'Credenciales inválidas.' });
      }
      if (!jwtSecret) {
        throw new Error('JWT_SECRET no está configurado.');
      }

      const token = jwt.sign(
        { id_usuario: user.id_usuario, rol: user.rol },
        jwtSecret,
        { expiresIn: '8h' },
      );
      return res.json({ token, usuario: publicUser(user) });
    } catch (error) {
      return next(error);
    }
  });

  router.get('/me', authenticateToken(jwtSecret), async (req, res, next) => {
    try {
      const user = await userRepository.findPublicById(req.user.id_usuario);
      if (!user) {
        return res.status(401).json({ error: 'Autenticación requerida.' });
      }
      return res.json(user);
    } catch (error) {
      return next(error);
    }
  });

  return router;
}
