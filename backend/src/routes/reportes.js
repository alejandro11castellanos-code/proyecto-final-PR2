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

// Dimensiones y medidas que admite la tabla cruzada. El repositorio las
// traduce a SQL con una lista cerrada: el texto del usuario nunca llega al SQL.
const DIMENSIONES = ['concierto', 'localidad', 'artista', 'vendedor', 'dia'];
const MEDIDAS = ['boletos', 'ingresos'];

// Devuelve { ejes } o { error }. Los parámetros ausentes toman el pivote
// principal (concierto × localidad en boletos).
function leerEjes(query) {
  const ejes = {
    filas: query.filas ?? 'concierto',
    columnas: query.columnas ?? 'localidad',
    medida: query.medida ?? 'boletos',
  };
  if (!DIMENSIONES.includes(ejes.filas)) {
    return { error: `filas debe ser una de: ${DIMENSIONES.join(', ')}.` };
  }
  if (!DIMENSIONES.includes(ejes.columnas)) {
    return { error: `columnas debe ser una de: ${DIMENSIONES.join(', ')}.` };
  }
  if (ejes.filas === ejes.columnas) {
    return { error: 'filas y columnas deben ser dimensiones distintas.' };
  }
  if (!MEDIDAS.includes(ejes.medida)) {
    return { error: `medida debe ser una de: ${MEDIDAS.join(', ')}.` };
  }
  return { ejes };
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

  // Tabla cruzada (pivote): una celda por combinación fila × columna con
  // ventas, en formato plano para que el cliente arme la matriz.
  router.get('/cruzado', requireAuth, requireAdmin, async (req, res, next) => {
    const { filtros, error: errorFiltros } = leerFiltros(req.query);
    if (errorFiltros) return res.status(400).json({ error: errorFiltros });
    const { ejes, error: errorEjes } = leerEjes(req.query);
    if (errorEjes) return res.status(400).json({ error: errorEjes });

    try {
      const celdas = await reporteRepository.cruzado(filtros, ejes);
      res.json({ ...ejes, celdas });
    } catch (error) {
      next(error);
    }
  });

  return router;
}
