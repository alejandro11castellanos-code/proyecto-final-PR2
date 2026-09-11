export function createInventarioRepository(database) {
  return {
    async findAllByConcierto(idConcierto) {
      const result = await database.query(
        `SELECT i.id_inventario, i.id_concierto, i.id_localidad, l.nombre AS nombre_localidad,
                i.precio, i.cantidad_total, i.cantidad_disponible
         FROM inventario_boletos i
         JOIN localidades l ON l.id_localidad = i.id_localidad
         WHERE i.id_concierto = $1
         ORDER BY l.nombre`,
        [idConcierto],
      );
      return result.rows;
    },

    async findById(idInventario) {
      const result = await database.query(
        `SELECT i.id_inventario, i.id_concierto, i.id_localidad, l.nombre AS nombre_localidad,
                i.precio, i.cantidad_total, i.cantidad_disponible
         FROM inventario_boletos i
         JOIN localidades l ON l.id_localidad = i.id_localidad
         WHERE i.id_inventario = $1`,
        [idInventario],
      );
      return result.rows[0] ?? null;
    },

    async create({ id_concierto, id_localidad, precio, cantidad_total }) {
      const result = await database.query(
        `INSERT INTO inventario_boletos (id_concierto, id_localidad, precio, cantidad_total, cantidad_disponible)
         VALUES ($1, $2, $3, $4, $4)
         RETURNING id_inventario, id_concierto, id_localidad, precio, cantidad_total, cantidad_disponible`,
        [id_concierto, id_localidad, precio, cantidad_total],
      );
      return result.rows[0];
    },

    // El nuevo cantidad_total se combina con el disponible actual en una sola
    // sentencia: en un UPDATE de Postgres todas las expresiones del SET leen
    // la fila previa a la vez, así que "cantidad_total" del lado derecho es
    // todavía el valor viejo. Si el nuevo aforo cae por debajo de lo ya
    // vendido, el CHECK de la tabla rechaza la fila (se traduce a 400 arriba).
    async updatePrecioYAforo(idInventario, { precio, cantidad_total }) {
      const result = await database.query(
        `UPDATE inventario_boletos
         SET precio = $2,
             cantidad_disponible = cantidad_disponible + ($3 - cantidad_total),
             cantidad_total = $3
         WHERE id_inventario = $1
         RETURNING id_inventario, id_concierto, id_localidad, precio, cantidad_total, cantidad_disponible`,
        [idInventario, precio, cantidad_total],
      );
      return result.rows[0] ?? null;
    },

    async remove(idInventario) {
      const result = await database.query(
        'DELETE FROM inventario_boletos WHERE id_inventario = $1',
        [idInventario],
      );
      return result.rowCount > 0;
    },
  };
}
