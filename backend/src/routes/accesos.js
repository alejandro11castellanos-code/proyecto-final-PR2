import express from 'express';
import { authenticateToken, authorizeRoles } from '../middleware/auth.js';

const ESTADOS_SIN_INGRESO = ['cancelado', 'finalizado'];
const LIMITE_RECIENTES = 50;

// "2026-10-15T19:05:00" -> "15/10/2026 19:05". La fecha ya viene como hora local sin zona.
function formatearFechaHora(iso) {
  const [fecha, hora] = String(iso).split('T');
  const [anio, mes, dia] = fecha.split('-');
  return `${dia}/${mes}/${anio} ${hora.slice(0, 5)}`;
}

function datosDelBoleto(boleto) {
  return {
    id_boleto: boleto.id_boleto,
    codigo: boleto.codigo,
    titulo_evento: boleto.titulo_evento,
    nombre_artistico: boleto.nombre_artistico,
    nombre_localidad: boleto.nombre_localidad,
    numero: boleto.numero,
    cantidad: boleto.cantidad,
    fecha_ingreso: boleto.fecha_ingreso,
  };
}

/**
 * Control de acceso. Escanear un QR con una cámara cualquiera no registra nada:
 * el QR solo contiene un código. El ingreso lo registra una persona autenticada
 * (administrador o rol "acceso") que envía ese código a POST /accesos.
 */
export function createAccesoRouter({ accesoRepository, jwtSecret }) {
  const router = express.Router();
  const requireAuth = authenticateToken(jwtSecret);
  const requirePuerta = authorizeRoles('administrador', 'acceso');

  router.post('/', requireAuth, requirePuerta, async (req, res, next) => {
    const crudo = req.body?.codigo;
    if (typeof crudo !== 'string' || !crudo.trim()) {
      return res.status(400).json({ error: 'codigo es obligatorio.' });
    }
    const codigo = crudo.trim().toUpperCase();

    try {
      const boleto = await accesoRepository.buscarPorCodigo(codigo);
      if (!boleto) {
        return res.status(404).json({ error: 'El código no es válido.' });
      }
      if (ESTADOS_SIN_INGRESO.includes(boleto.estado_concierto)) {
        return res.status(409).json({ error: `El concierto está ${boleto.estado_concierto}; no se permite el ingreso.` });
      }
      if (boleto.fecha_ingreso) {
        return res.status(409).json({ error: `Este boleto ya ingresó el ${formatearFechaHora(boleto.fecha_ingreso)}.` });
      }

      const marcado = await accesoRepository.marcarIngreso({ codigo, idValidador: req.user.id_usuario });
      if (!marcado) {
        // Otro escáner lo registró entre la consulta y la marca.
        const actual = await accesoRepository.buscarPorCodigo(codigo);
        const cuando = actual?.fecha_ingreso ? ` el ${formatearFechaHora(actual.fecha_ingreso)}` : '';
        return res.status(409).json({ error: `Este boleto ya ingresó${cuando}.` });
      }
      return res.json({ ...datosDelBoleto(marcado), mensaje: 'Ingreso registrado.' });
    } catch (error) {
      return next(error);
    }
  });

  router.get('/resumen', requireAuth, requirePuerta, async (_req, res, next) => {
    try {
      res.json(await accesoRepository.resumenPorConcierto());
    } catch (error) {
      next(error);
    }
  });

  router.get('/', requireAuth, requirePuerta, async (req, res, next) => {
    let idConcierto = null;
    if (req.query.concierto !== undefined) {
      if (!/^[1-9]\d*$/.test(String(req.query.concierto))) {
        return res.status(400).json({ error: 'concierto debe ser un id numérico positivo.' });
      }
      idConcierto = Number(req.query.concierto);
    }
    try {
      res.json(await accesoRepository.ingresosRecientes({ idConcierto, limite: LIMITE_RECIENTES }));
    } catch (error) {
      next(error);
    }
  });

  return router;
}
