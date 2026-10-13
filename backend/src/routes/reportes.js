import express from 'express';
import { authenticateToken, authorizeRoles } from '../middleware/auth.js';

const FECHA = /^\d{4}-\d{2}-\d{2}$/;

function fechaValida(valor) {
  if (!FECHA.test(valor)) return false;
  const fecha = new Date(`${valor}T00:00:00Z`);
  return !Number.isNaN(fecha.getTime()) && fecha.toISOString().startsWith(valor);
}

// Devuelve { filtros } o { error }. Un parámetro ausente es null (sin filtrar);
// uno presente pero inválido es error, nunca se ignora en silencio.
function leerFiltros(query) {
  const filtros = { desde: null, hasta: null, idArtista: null, idConcierto: null, idVendedor: null, idLocalidad: null };

  for (const [param, clave] of [['desde', 'desde'], ['hasta', 'hasta']]) {
    if (query[param] === undefined) continue;
    if (typeof query[param] !== 'string' || !fechaValida(query[param])) {
      return { error: `${param} debe ser una fecha válida con formato AAAA-MM-DD.` };
    }
    filtros[clave] = query[param];
  }
  if (filtros.desde && filtros.hasta && filtros.desde > filtros.hasta) {
    return { error: 'desde no puede ser posterior a hasta.' };
  }

  for (const [param, clave] of [
    ['artista', 'idArtista'], ['concierto', 'idConcierto'], ['vendedor', 'idVendedor'], ['localidad', 'idLocalidad'],
  ]) {
    if (query[param] === undefined) continue;
    if (typeof query[param] !== 'string' || !/^[1-9]\d*$/.test(query[param])) {
      return { error: `${param} debe ser un id numérico positivo.` };
    }
    filtros[clave] = Number(query[param]);
  }
  return { filtros };
}

/**
 * Dashboard de reportería — exclusivo de administrador, igual que la
 * gestión de usuarios: es información gerencial, no algo que necesite el
 * vendedor para vender.
 */
export function createReporteRouter({ reporteRepository, jwtSecret }) {
  const router = express.Router();
  const requireAuth = authenticateToken(jwtSecret);
  const requireAdmin = authorizeRoles('administrador');

  router.get('/dashboard', requireAuth, requireAdmin, async (req, res, next) => {
    const { filtros, error } = leerFiltros(req.query);
    if (error) return res.status(400).json({ error });

    try {
      const [resumen, porConcierto, porArtista, porVendedor, porDia, porLocalidad, ocupacion] =
        await Promise.all([
          reporteRepository.resumen(filtros),
          reporteRepository.porConcierto(filtros),
          reporteRepository.porArtista(filtros),
          reporteRepository.porVendedor(filtros),
          reporteRepository.porDia(filtros),
          reporteRepository.porLocalidad(filtros),
          reporteRepository.ocupacion(filtros),
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
