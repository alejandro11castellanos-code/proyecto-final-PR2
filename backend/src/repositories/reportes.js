/**
 * Consultas agregadas para el dashboard de reportería. Todas devuelven
 * arreglos vacíos/ceros con datos limpios (sin filas) en vez de fallar —
 * un proyecto recién sembrado no tiene por qué romper esta pantalla.
 *
 * Cada método recibe `filtros` ({ desde, hasta, idArtista, idConcierto,
 * idVendedor, idLocalidad }, todos opcionales/null). Las ventas se filtran
 * en el CTE `hechos` (un renglón por línea vendida); las tablas de
 * dimensión (conciertos, artistas...) se le unen con LEFT JOIN, de modo que
 * un concierto sin ventas en el rango sigue apareciendo con ceros.
 */
const SIN_FILTROS = {
  desde: null, hasta: null, idArtista: null, idConcierto: null, idVendedor: null, idLocalidad: null,
};

// Acumula los valores y devuelve el marcador $n correspondiente, para que
// nunca se concatene un valor del usuario dentro del SQL.
function crearParametros() {
  const valores = [];
  return {
    valores,
    p(valor) {
      valores.push(valor);
      return `$${valores.length}`;
    },
  };
}

function cteHechos(filtros, { p }) {
  const condiciones = [];
  // fecha_venta es TIMESTAMP: se compara por día para que `hasta` incluya
  // todo ese día, no solo la medianoche.
  if (filtros.desde) condiciones.push(`v.fecha_venta::date >= ${p(filtros.desde)}::date`);
  if (filtros.hasta) condiciones.push(`v.fecha_venta::date <= ${p(filtros.hasta)}::date`);
  if (filtros.idVendedor) condiciones.push(`v.id_vendedor = ${p(filtros.idVendedor)}`);
  if (filtros.idLocalidad) condiciones.push(`i.id_localidad = ${p(filtros.idLocalidad)}`);
  if (filtros.idConcierto) condiciones.push(`i.id_concierto = ${p(filtros.idConcierto)}`);
  if (filtros.idArtista) condiciones.push(`c.id_artista = ${p(filtros.idArtista)}`);

  return `hechos AS (
    SELECT dv.subtotal, dv.cantidad, v.id_venta, v.id_vendedor, v.fecha_venta,
           i.id_localidad, i.id_concierto, c.id_artista
    FROM detalle_ventas dv
    JOIN ventas v ON v.id_venta = dv.id_venta
    JOIN inventario_boletos i ON i.id_inventario = dv.id_inventario
    JOIN conciertos c ON c.id_concierto = i.id_concierto
    ${condiciones.length ? `WHERE ${condiciones.join(' AND ')}` : ''}
  )`;
}

function donde(condiciones) {
  return condiciones.length ? `WHERE ${condiciones.join(' AND ')}` : '';
}

export function createReporteRepository(database) {
  async function consultar(filtros, armar) {
    const ctx = crearParametros();
    const hechos = cteHechos(filtros, ctx);
    const sql = `WITH ${hechos} ${armar(ctx)}`;
    return database.query(sql, ctx.valores);
  }

  return {
    async resumen(filtros = SIN_FILTROS) {
      const result = await consultar(
        filtros,
        () => `SELECT COALESCE(SUM(subtotal), 0) AS ingresos_totales,
                      COALESCE(SUM(cantidad), 0) AS boletos_vendidos
               FROM hechos`,
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

    async porConcierto(filtros = SIN_FILTROS) {
      const result = await consultar(filtros, ({ p }) => {
        const condiciones = [];
        if (filtros.idConcierto) condiciones.push(`c.id_concierto = ${p(filtros.idConcierto)}`);
        if (filtros.idArtista) condiciones.push(`c.id_artista = ${p(filtros.idArtista)}`);
        return `SELECT c.id_concierto, c.titulo_evento, a.nombre_artistico,
                       COALESCE(SUM(h.subtotal), 0) AS ingresos,
                       COALESCE(SUM(h.cantidad), 0) AS boletos
                FROM conciertos c
                JOIN artistas a ON a.id_artista = c.id_artista
                LEFT JOIN hechos h ON h.id_concierto = c.id_concierto
                ${donde(condiciones)}
                GROUP BY c.id_concierto, c.titulo_evento, a.nombre_artistico
                ORDER BY ingresos DESC, c.titulo_evento`;
      });
      return result.rows;
    },

    async porArtista(filtros = SIN_FILTROS) {
      const result = await consultar(filtros, ({ p }) => {
        const condiciones = [];
        if (filtros.idArtista) condiciones.push(`a.id_artista = ${p(filtros.idArtista)}`);
        if (filtros.idConcierto) {
          condiciones.push(`EXISTS (SELECT 1 FROM conciertos cx
                                    WHERE cx.id_artista = a.id_artista
                                      AND cx.id_concierto = ${p(filtros.idConcierto)})`);
        }
        return `SELECT a.id_artista, a.nombre_artistico,
                       COALESCE(SUM(h.subtotal), 0) AS ingresos,
                       COALESCE(SUM(h.cantidad), 0) AS boletos
                FROM artistas a
                LEFT JOIN hechos h ON h.id_artista = a.id_artista
                ${donde(condiciones)}
                GROUP BY a.id_artista, a.nombre_artistico
                ORDER BY ingresos DESC, a.nombre_artistico`;
      });
      return result.rows;
    },

    // No se filtra por rol: cualquier usuario autenticado puede vender
    // (incluido un administrador probando el punto de venta), y si se
    // filtrara por rol='vendedor' esas ventas quedarían invisibles acá
    // aunque sí sumen en resumen() — los totales dejarían de coincidir.
    async porVendedor(filtros = SIN_FILTROS) {
      const result = await consultar(filtros, ({ p }) => {
        const condiciones = [];
        if (filtros.idVendedor) condiciones.push(`u.id_usuario = ${p(filtros.idVendedor)}`);
        return `SELECT u.id_usuario, u.nombre_completo, u.rol,
                       COALESCE(SUM(h.subtotal), 0) AS ingresos,
                       COUNT(DISTINCT h.id_venta) AS ventas
                FROM usuarios u
                LEFT JOIN hechos h ON h.id_vendedor = u.id_usuario
                ${donde(condiciones)}
                GROUP BY u.id_usuario, u.nombre_completo, u.rol
                ORDER BY ingresos DESC, u.nombre_completo`;
      });
      return result.rows;
    },

    async porDia(filtros = SIN_FILTROS) {
      const result = await consultar(
        filtros,
        () => `SELECT TO_CHAR(fecha_venta, 'YYYY-MM-DD') AS dia,
                      COALESCE(SUM(subtotal), 0) AS ingresos,
                      COALESCE(SUM(cantidad), 0) AS boletos
               FROM hechos
               GROUP BY dia
               ORDER BY dia`,
      );
      return result.rows;
    },

    async porLocalidad(filtros = SIN_FILTROS) {
      const result = await consultar(filtros, ({ p }) => {
        const condiciones = [];
        if (filtros.idLocalidad) condiciones.push(`l.id_localidad = ${p(filtros.idLocalidad)}`);
        return `SELECT l.id_localidad, l.nombre, COALESCE(SUM(h.cantidad), 0) AS boletos
                FROM localidades l
                LEFT JOIN hechos h ON h.id_localidad = l.id_localidad
                ${donde(condiciones)}
                GROUP BY l.id_localidad, l.nombre
                ORDER BY boletos DESC, l.nombre`;
      });
      return result.rows;
    },

    // La ocupación es una foto del inventario, no un acumulado de ventas:
    // por eso ignora fecha y vendedor y solo responde a artista, concierto
    // y localidad.
    async ocupacion(filtros = SIN_FILTROS) {
      const result = await consultar(filtros, ({ p }) => {
        const condiciones = [];
        if (filtros.idConcierto) condiciones.push(`c.id_concierto = ${p(filtros.idConcierto)}`);
        if (filtros.idArtista) condiciones.push(`c.id_artista = ${p(filtros.idArtista)}`);
        const enLocalidad = filtros.idLocalidad ? `AND i.id_localidad = ${p(filtros.idLocalidad)}` : '';
        return `SELECT c.id_concierto, c.titulo_evento,
                       COALESCE(SUM(i.cantidad_total), 0) AS cantidad_total,
                       COALESCE(SUM(i.cantidad_total - i.cantidad_disponible), 0) AS vendido
                FROM conciertos c
                LEFT JOIN inventario_boletos i ON i.id_concierto = c.id_concierto ${enLocalidad}
                ${donde(condiciones)}
                GROUP BY c.id_concierto, c.titulo_evento
                ORDER BY c.titulo_evento`;
      });
      return result.rows;
    },
  };
}
