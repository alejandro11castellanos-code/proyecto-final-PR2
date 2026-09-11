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

    async findAll() {
      const result = await database.query(
        `SELECT id_usuario, nombre_usuario, nombre_completo, rol, fecha_creacion
         FROM usuarios
         ORDER BY nombre_completo`,
      );
      return result.rows;
    },

    async create({ nombre_usuario, contrasena_hash, nombre_completo, rol }) {
      const result = await database.query(
        `INSERT INTO usuarios (nombre_usuario, contrasena_hash, nombre_completo, rol)
         VALUES ($1, $2, $3, $4)
         RETURNING id_usuario, nombre_usuario, nombre_completo, rol, fecha_creacion`,
        [nombre_usuario, contrasena_hash, nombre_completo, rol],
      );
      return result.rows[0];
    },

    async update(id, { nombre_completo, rol }) {
      const result = await database.query(
        `UPDATE usuarios
         SET nombre_completo = $2, rol = $3
         WHERE id_usuario = $1
         RETURNING id_usuario, nombre_usuario, nombre_completo, rol, fecha_creacion`,
        [id, nombre_completo, rol],
      );
      return result.rows[0] ?? null;
    },

    async updatePassword(id, contrasenaHash) {
      const result = await database.query(
        'UPDATE usuarios SET contrasena_hash = $2 WHERE id_usuario = $1',
        [id, contrasenaHash],
      );
      return result.rowCount > 0;
    },

    async remove(id) {
      const result = await database.query('DELETE FROM usuarios WHERE id_usuario = $1', [id]);
      return result.rowCount > 0;
    },
  };
}
