/**
 * Control de acceso al concierto. `marcarIngreso` es un UPDATE condicionado
 * (fecha_ingreso IS NULL): si dos escáneres leen el mismo boleto a la vez, la
 * base de datos deja pasar solo a uno y al otro le devuelve 0 filas. No hace
 * falta bloquear nada a mano.
 */
const COLUMNAS_BOLETO = `b.id_boleto, b.codigo, b.numero, d.cantidad, b.fecha_ingreso,
                c.id_concierto, c.titulo_evento, c.estado AS estado_concierto,
                a.nombre_artistico, l.nombre AS nombre_localidad`;

const UNIONES_BOLETO = `FROM boletos b
         JOIN detalle_ventas d ON d.id_detalle = b.id_detalle
         JOIN inventario_boletos i ON i.id_inventario = d.id_inventario
         JOIN conciertos c ON c.id_concierto = i.id_concierto
         JOIN artistas a ON a.id_artista = c.id_artista
         JOIN localidades l ON l.id_localidad = i.id_localidad`;

export function createAccesoRepository(database) {
  return {
    async buscarPorCodigo(codigo) {
      const result = await database.query(
        `SELECT ${COLUMNAS_BOLETO} ${UNIONES_BOLETO} WHERE b.codigo = $1`,
        [codigo],
      );
      return result.rows[0] ?? null;
    },

    // Devuelve el boleto ya marcado, o null si otro escáner se adelantó.
    async marcarIngreso({ codigo, idValidador }) {
      const marcado = await database.query(
        `UPDATE boletos
         SET fecha_ingreso = (NOW() AT TIME ZONE 'America/Guatemala'), id_validador = $2
         WHERE codigo = $1 AND fecha_ingreso IS NULL
         RETURNING id_boleto`,
        [codigo, idValidador],
      );
      if (marcado.rowCount === 0) return null;

      const result = await database.query(
        `SELECT ${COLUMNAS_BOLETO} ${UNIONES_BOLETO} WHERE b.id_boleto = $1`,
        [marcado.rows[0].id_boleto],
      );
      return result.rows[0];
    },

    // Vendidos = boletos emitidos; ingresados = los que ya pasaron por la puerta.
    async resumenPorConcierto() {
      const result = await database.query(
        `SELECT c.id_concierto, c.titulo_evento, a.nombre_artistico,
                COUNT(b.id_boleto)::int AS vendidos,
                COUNT(b.fecha_ingreso)::int AS ingresados
         FROM conciertos c
         JOIN artistas a ON a.id_artista = c.id_artista
         LEFT JOIN inventario_boletos i ON i.id_concierto = c.id_concierto
         LEFT JOIN detalle_ventas d ON d.id_inventario = i.id_inventario
         LEFT JOIN boletos b ON b.id_detalle = d.id_detalle
         GROUP BY c.id_concierto, c.titulo_evento, a.nombre_artistico
         ORDER BY c.titulo_evento`,
      );
      return result.rows;
    },

    async ingresosRecientes({ idConcierto = null, limite = 50 } = {}) {
      const result = await database.query(
        `SELECT ${COLUMNAS_BOLETO}, u.nombre_completo AS nombre_validador
         ${UNIONES_BOLETO}
         LEFT JOIN usuarios u ON u.id_usuario = b.id_validador
         WHERE b.fecha_ingreso IS NOT NULL AND ($1::int IS NULL OR c.id_concierto = $1)
         ORDER BY b.fecha_ingreso DESC
         LIMIT $2`,
        [idConcierto, limite],
      );
      return result.rows;
    },
  };
}
