/**
 * Consultas agregadas para el dashboard de reportería. Todas devuelven
 * arreglos vacíos/ceros con datos limpios (sin filas) en vez de fallar —
 * un proyecto recién sembrado no tiene por qué romper esta pantalla.
 */
export function createReporteRepository(database) {
  return {
    async resumen() {
      const result = await database.query(
        `SELECT
           COALESCE(SUM(dv.subtotal), 0) AS ingresos_totales,
           COALESCE(SUM(dv.cantidad), 0) AS boletos_vendidos
         FROM detalle_ventas dv`,
      );
      const fila = result.rows[0];
      const ingresos = Number(fila.ingresos_totales);
      const boletos = Number(fila.boletos_vendidos);
      return {
        ingresos_totales: fila.ingresos_totales,
        boletos_vendidos: boletos,
        precio_promedio: boletos > 0 ? (ingresos / boletos).toFixed(2) : '0.00',
      };
    },

    async porConcierto() {
      const result = await database.query(
        `SELECT c.id_concierto, c.titulo_evento, a.nombre_artistico,
                COALESCE(SUM(dv.subtotal), 0) AS ingresos,
                COALESCE(SUM(dv.cantidad), 0) AS boletos
         FROM conciertos c
         JOIN artistas a ON a.id_artista = c.id_artista
         LEFT JOIN inventario_boletos i ON i.id_concierto = c.id_concierto
         LEFT JOIN detalle_ventas dv ON dv.id_inventario = i.id_inventario
         GROUP BY c.id_concierto, c.titulo_evento, a.nombre_artistico
         ORDER BY ingresos DESC, c.titulo_evento`,
      );
      return result.rows;
    },

    async porArtista() {
      const result = await database.query(
        `SELECT a.id_artista, a.nombre_artistico,
                COALESCE(SUM(dv.subtotal), 0) AS ingresos,
                COALESCE(SUM(dv.cantidad), 0) AS boletos
         FROM artistas a
         LEFT JOIN conciertos c ON c.id_artista = a.id_artista
         LEFT JOIN inventario_boletos i ON i.id_concierto = c.id_concierto
         LEFT JOIN detalle_ventas dv ON dv.id_inventario = i.id_inventario
         GROUP BY a.id_artista, a.nombre_artistico
         ORDER BY ingresos DESC, a.nombre_artistico`,
      );
      return result.rows;
    },

    // No se filtra por rol: cualquier usuario autenticado puede vender
    // (incluido un administrador probando el punto de venta), y si se
    // filtrara por rol='vendedor' esas ventas quedarían invisibles acá
    // aunque sí sumen en resumen() — los totales dejarían de coincidir.
    async porVendedor() {
      const result = await database.query(
        `SELECT u.id_usuario, u.nombre_completo, u.rol,
                COALESCE(SUM(v.total_venta), 0) AS ingresos,
                COUNT(v.id_venta) AS ventas
         FROM usuarios u
         LEFT JOIN ventas v ON v.id_vendedor = u.id_usuario
         GROUP BY u.id_usuario, u.nombre_completo, u.rol
         ORDER BY ingresos DESC, u.nombre_completo`,
      );
      return result.rows;
    },

    async porDia() {
      const result = await database.query(
        `SELECT TO_CHAR(v.fecha_venta, 'YYYY-MM-DD') AS dia,
                COALESCE(SUM(dv.subtotal), 0) AS ingresos,
                COALESCE(SUM(dv.cantidad), 0) AS boletos
         FROM ventas v
         JOIN detalle_ventas dv ON dv.id_venta = v.id_venta
         GROUP BY dia
         ORDER BY dia`,
      );
      return result.rows;
    },

    async porLocalidad() {
      const result = await database.query(
        `SELECT l.id_localidad, l.nombre, COALESCE(SUM(dv.cantidad), 0) AS boletos
         FROM localidades l
         LEFT JOIN inventario_boletos i ON i.id_localidad = l.id_localidad
         LEFT JOIN detalle_ventas dv ON dv.id_inventario = i.id_inventario
         GROUP BY l.id_localidad, l.nombre
         ORDER BY boletos DESC, l.nombre`,
      );
      return result.rows;
    },

    async ocupacion() {
      const result = await database.query(
        `SELECT c.id_concierto, c.titulo_evento,
                COALESCE(SUM(i.cantidad_total), 0) AS cantidad_total,
                COALESCE(SUM(i.cantidad_total - i.cantidad_disponible), 0) AS vendido
         FROM conciertos c
         LEFT JOIN inventario_boletos i ON i.id_concierto = c.id_concierto
         GROUP BY c.id_concierto, c.titulo_evento
         ORDER BY c.titulo_evento`,
      );
      return result.rows;
    },
  };
}
