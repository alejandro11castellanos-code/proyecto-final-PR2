export function createUserRepository(database) {
  return {
    async findByUsername(username) {
      const result = await database.query(
        `SELECT id_usuario, nombre_usuario, contrasena_hash, nombre_completo, rol
         FROM usuarios
         WHERE nombre_usuario = $1`,
        [username],
      );
      return result.rows[0] ?? null;
    },

    async findPublicById(id) {
      const result = await database.query(
        `SELECT id_usuario, nombre_usuario, nombre_completo, rol
         FROM usuarios
         WHERE id_usuario = $1`,
        [id],
      );
      return result.rows[0] ?? null;
    },
  };
}
