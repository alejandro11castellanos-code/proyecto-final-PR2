export function createLocalidadRepository(database) {
  return {
    async findAll() {
      const result = await database.query(
        'SELECT id_localidad, nombre FROM localidades ORDER BY nombre',
      );
      return result.rows;
    },

    async findById(id) {
      const result = await database.query(
        'SELECT id_localidad, nombre FROM localidades WHERE id_localidad = $1',
        [id],
      );
      return result.rows[0] ?? null;
    },

    async create({ nombre }) {
      const result = await database.query(
        `INSERT INTO localidades (nombre)
         VALUES ($1)
         RETURNING id_localidad, nombre`,
        [nombre],
      );
      return result.rows[0];
    },

    async update(id, { nombre }) {
      const result = await database.query(
        `UPDATE localidades
         SET nombre = $2
         WHERE id_localidad = $1
         RETURNING id_localidad, nombre`,
        [id, nombre],
      );
      return result.rows[0] ?? null;
    },

    async remove(id) {
      const result = await database.query('DELETE FROM localidades WHERE id_localidad = $1', [id]);
      return result.rowCount > 0;
    },
  };
}
