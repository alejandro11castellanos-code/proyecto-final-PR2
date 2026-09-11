export function createArtistaRepository(database) {
  return {
    async findAll() {
      const result = await database.query(
        `SELECT id_artista, nombre_artistico, genero_musical, pais_origen
         FROM artistas
         ORDER BY nombre_artistico`,
      );
      return result.rows;
    },

    async findById(id) {
      const result = await database.query(
        `SELECT id_artista, nombre_artistico, genero_musical, pais_origen
         FROM artistas
         WHERE id_artista = $1`,
        [id],
      );
      return result.rows[0] ?? null;
    },

    async create({ nombre_artistico, genero_musical, pais_origen }) {
      const result = await database.query(
        `INSERT INTO artistas (nombre_artistico, genero_musical, pais_origen)
         VALUES ($1, $2, $3)
         RETURNING id_artista, nombre_artistico, genero_musical, pais_origen`,
        [nombre_artistico, genero_musical, pais_origen ?? null],
      );
      return result.rows[0];
    },

    async update(id, { nombre_artistico, genero_musical, pais_origen }) {
      const result = await database.query(
        `UPDATE artistas
         SET nombre_artistico = $2, genero_musical = $3, pais_origen = $4
         WHERE id_artista = $1
         RETURNING id_artista, nombre_artistico, genero_musical, pais_origen`,
        [id, nombre_artistico, genero_musical, pais_origen ?? null],
      );
      return result.rows[0] ?? null;
    },

    async remove(id) {
      const result = await database.query('DELETE FROM artistas WHERE id_artista = $1', [id]);
      return result.rowCount > 0;
    },
  };
}
