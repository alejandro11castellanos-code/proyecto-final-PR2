export class InventarioNoEncontradoError extends Error {
  constructor(idInventario) {
    super(`El inventario ${idInventario} no existe.`);
    this.name = 'InventarioNoEncontradoError';
    this.idInventario = idInventario;
  }
}

export class DisponibilidadInsuficienteError extends Error {
  constructor(idInventario, disponible, solicitado) {
    super(
      `Disponibilidad insuficiente para el inventario ${idInventario}: `
        + `pidieron ${solicitado} y hay ${disponible}.`,
    );
    this.name = 'DisponibilidadInsuficienteError';
    this.idInventario = idInventario;
    this.disponible = disponible;
    this.solicitado = solicitado;
  }
}

/**
 * Repositorio de ventas. `crear` es la operación central del sistema: bloquea
 * cada fila de inventario involucrada, valida disponibilidad y descuenta el
 * aforo dentro de una sola transacción — si cualquier ítem falla, no se
 * aplica ninguno.
 */
export function createVentaRepository(pool) {
  return {
    async crear({ idVendedor, items }) {
      const client = await pool.connect();
      try {
        await client.query('BEGIN');

        // Orden estable al bloquear filas: evita deadlocks cuando dos ventas
        // concurrentes comparten localidades pero las piden en distinto orden.
        const ids = [...new Set(items.map((item) => item.idInventario))].sort((a, b) => a - b);
        const { rows: filas } = await client.query(
          `SELECT id_inventario, precio, cantidad_disponible
           FROM inventario_boletos
           WHERE id_inventario = ANY($1::int[])
           ORDER BY id_inventario
           FOR UPDATE`,
          [ids],
        );
        const porId = new Map(filas.map((fila) => [fila.id_inventario, fila]));

        // Se valida todo antes de tocar nada: o se aplica la venta completa,
        // o no se aplica ningún ítem.
        for (const item of items) {
          const fila = porId.get(item.idInventario);
          if (!fila) {
            throw new InventarioNoEncontradoError(item.idInventario);
          }
          if (item.cantidad > fila.cantidad_disponible) {
            throw new DisponibilidadInsuficienteError(
              item.idInventario, fila.cantidad_disponible, item.cantidad);
          }
        }

        const detalles = items.map((item) => {
          const precioUnitario = Number(porId.get(item.idInventario).precio);
          return {
            id_inventario: item.idInventario,
            cantidad: item.cantidad,
            precio_unitario: precioUnitario,
            subtotal: Math.round(precioUnitario * item.cantidad * 100) / 100,
          };
        });
        const total = Math.round(detalles.reduce((sum, d) => sum + d.subtotal, 0) * 100) / 100;

        for (const detalle of detalles) {
          await client.query(
            `UPDATE inventario_boletos
             SET cantidad_disponible = cantidad_disponible - $2
             WHERE id_inventario = $1`,
            [detalle.id_inventario, detalle.cantidad],
          );
        }

        const ventaResult = await client.query(
          `INSERT INTO ventas (id_vendedor, total_venta)
           VALUES ($1, $2)
           RETURNING id_venta, id_vendedor, fecha_venta, total_venta`,
          [idVendedor, total],
        );
        const venta = ventaResult.rows[0];

        for (const detalle of detalles) {
          await client.query(
            `INSERT INTO detalle_ventas (id_venta, id_inventario, cantidad, precio_unitario, subtotal)
             VALUES ($1, $2, $3, $4, $5)`,
            [venta.id_venta, detalle.id_inventario, detalle.cantidad, detalle.precio_unitario, detalle.subtotal],
          );
        }

        await client.query('COMMIT');
        return { ...venta, items: detalles };
      } catch (error) {
        await client.query('ROLLBACK');
        throw error;
      } finally {
        client.release();
      }
    },

    async findById(idVenta) {
      const ventaResult = await pool.query(
        'SELECT id_venta, id_vendedor, fecha_venta, total_venta FROM ventas WHERE id_venta = $1',
        [idVenta],
      );
      const venta = ventaResult.rows[0];
      if (!venta) {
        return null;
      }

      const itemsResult = await pool.query(
        `SELECT d.id_detalle, d.id_inventario, d.cantidad, d.precio_unitario, d.subtotal,
                l.nombre AS nombre_localidad, c.id_concierto, c.titulo_evento, a.nombre_artistico
         FROM detalle_ventas d
         JOIN inventario_boletos i ON i.id_inventario = d.id_inventario
         JOIN localidades l ON l.id_localidad = i.id_localidad
         JOIN conciertos c ON c.id_concierto = i.id_concierto
         JOIN artistas a ON a.id_artista = c.id_artista
         WHERE d.id_venta = $1
         ORDER BY d.id_detalle`,
        [idVenta],
      );

      return { ...venta, items: itemsResult.rows };
    },

    // Lista liviana para el historial: sin el detalle por ítem (eso lo trae
    // findById cuando se abre una venta puntual). idVendedor/idConcierto en
    // null significan "sin filtrar por ese campo" — la ruta decide qué
    // filtros aplicar según el rol de quien pregunta.
    async listar({ idVendedor = null, idConcierto = null } = {}) {
      const result = await pool.query(
        `SELECT v.id_venta, v.fecha_venta, v.id_vendedor, u.nombre_completo AS nombre_vendedor,
                v.total_venta, COALESCE(SUM(d.cantidad), 0) AS boletos
         FROM ventas v
         JOIN usuarios u ON u.id_usuario = v.id_vendedor
         LEFT JOIN detalle_ventas d ON d.id_venta = v.id_venta
         WHERE ($1::int IS NULL OR v.id_vendedor = $1)
           AND ($2::int IS NULL OR EXISTS (
                 SELECT 1
                 FROM detalle_ventas d2
                 JOIN inventario_boletos i2 ON i2.id_inventario = d2.id_inventario
                 WHERE d2.id_venta = v.id_venta AND i2.id_concierto = $2
               ))
         GROUP BY v.id_venta, v.fecha_venta, v.id_vendedor, u.nombre_completo, v.total_venta
         ORDER BY v.fecha_venta DESC
         LIMIT 200`,
        [idVendedor, idConcierto],
      );
      return result.rows;
    },
  };
}
