-- Migración para una base ya creada con la versión anterior de schema.sql.
-- (Una base nueva no la necesita: schema.sql ya incluye todo esto.)
--   1. Rol 'acceso' (control de acceso / portero).
--   2. Tabla boletos: un boleto por persona, con su código y su hora de ingreso.
--   3. Boletos de las ventas ya existentes.
-- Se puede ejecutar más de una vez sin duplicar datos.
BEGIN;

DO $$
DECLARE restriccion text;
BEGIN
    SELECT conname INTO restriccion
    FROM pg_constraint
    WHERE conrelid = 'usuarios'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%rol%';
    IF restriccion IS NOT NULL THEN
        EXECUTE format('ALTER TABLE usuarios DROP CONSTRAINT %I', restriccion);
    END IF;
END $$;

ALTER TABLE usuarios
    ADD CONSTRAINT usuarios_rol_check CHECK (rol IN ('administrador', 'vendedor', 'acceso'));

CREATE TABLE IF NOT EXISTS boletos (
    id_boleto     SERIAL      PRIMARY KEY,
    id_detalle    INTEGER     NOT NULL REFERENCES detalle_ventas (id_detalle) ON DELETE CASCADE,
    numero        INTEGER     NOT NULL CHECK (numero > 0),
    codigo        VARCHAR(40) NOT NULL UNIQUE,
    fecha_ingreso TIMESTAMP,
    id_validador  INTEGER     REFERENCES usuarios (id_usuario),
    CONSTRAINT uq_boleto_detalle_numero UNIQUE (id_detalle, numero)
);

CREATE INDEX IF NOT EXISTS ix_boletos_detalle ON boletos (id_detalle);

-- Ventas anteriores: un boleto por cada unidad vendida, con token aleatorio.
INSERT INTO boletos (id_detalle, numero, codigo)
SELECT d.id_detalle, n,
       'SVBGUA-V' || d.id_venta || '-D' || d.id_detalle || '-B' || n || '-'
       || upper(substr(md5(random()::text || clock_timestamp()::text || d.id_detalle || '-' || n), 1, 8))
FROM detalle_ventas d
CROSS JOIN LATERAL generate_series(1, d.cantidad) AS n
WHERE NOT EXISTS (SELECT 1 FROM boletos b WHERE b.id_detalle = d.id_detalle);

COMMIT;
