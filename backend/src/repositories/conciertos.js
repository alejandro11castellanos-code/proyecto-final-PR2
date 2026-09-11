export const ESTADOS_VALIDOS = ['programado', 'activo', 'finalizado', 'cancelado'];

export function createConciertoRepository(database) {
  return {
    async findAll() {
      const result = await database.query(
        `SELECT c.id_concierto, c.id_artista, a.nombre_artistico, c.titulo_evento,
                c.fecha_concierto, c.recinto, c.estado
         FROM conciertos c
         JOIN artistas a ON a.id_artista = c.id_artista
         ORDER BY c.fecha_concierto`,
      );
      return result.rows;
    },

    async findById(id) {
      const result = await database.query(
        `SELECT c.id_concierto, c.id_artista, a.nombre_artistico, c.titulo_evento,
                c.fecha_concierto, c.recinto, c.estado
         FROM conciertos c
         JOIN artistas a ON a.id_artista = c.id_artista
         WHERE c.id_concierto = $1`,
        [id],
      );
      return result.rows[0] ?? null;
    },

    async create({ id_artista, titulo_evento, fecha_concierto, recinto, estado }) {
      const result = await database.query(
        `INSERT INTO conciertos (id_artista, titulo_evento, fecha_concierto, recinto, estado)
         VALUES ($1, $2, $3, $4, COALESCE($5, 'programado'))
         RETURNING id_concierto, id_artista, titulo_evento, fecha_concierto, recinto, estado`,
        [id_artista, titulo_evento, fecha_concierto, recinto, estado ?? null],
      );
      return result.rows[0];
    },

    async update(id, { id_artista, titulo_evento, fecha_concierto, recinto, estado }) {
      const result = await database.query(
        `UPDATE conciertos
         SET id_artista = $2, titulo_evento = $3, fecha_concierto = $4, recinto = $5, estado = $6
         WHERE id_concierto = $1
         RETURNING id_concierto, id_artista, titulo_evento, fecha_concierto, recinto, estado`,
        [id, id_artista, titulo_evento, fecha_concierto, recinto, estado],
      );
      return result.rows[0] ?? null;
    },

    async remove(id) {
      const result = await database.query('DELETE FROM conciertos WHERE id_concierto = $1', [id]);
      return result.rowCount > 0;
    },
  };
}
