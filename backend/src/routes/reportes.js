import express from 'express';
import { authenticateToken, authorizeRoles } from '../middleware/auth.js';

/**
 * Dashboard de reportería — exclusivo de administrador, igual que la
 * gestión de usuarios: es información gerencial, no algo que necesite el
 * vendedor para vender.
 */
export function createReporteRouter({ reporteRepository, jwtSecret }) {
  const router = express.Router();
  const requireAuth = authenticateToken(jwtSecret);
  const requireAdmin = authorizeRoles('administrador');

  router.get('/dashboard', requireAuth, requireAdmin, async (_req, res, next) => {
    try {
      const [resumen, porConcierto, porArtista, porVendedor, porDia, porLocalidad, ocupacion] =
        await Promise.all([
          reporteRepository.resumen(),
          reporteRepository.porConcierto(),
          reporteRepository.porArtista(),
          reporteRepository.porVendedor(),
          reporteRepository.porDia(),
          reporteRepository.porLocalidad(),
          reporteRepository.ocupacion(),
        ]);

      res.json({
        resumen,
        por_concierto: porConcierto,
        por_artista: porArtista,
        por_vendedor: porVendedor,
        por_dia: porDia,
        por_localidad: porLocalidad,
        ocupacion,
      });
    } catch (error) {
      next(error);
    }
  });

  return router;
}
