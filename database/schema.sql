-- SVB-GUA · Esquema relacional (PostgreSQL)
-- Basado en el documento oficial del proyecto (UMG Programación II).
-- Ejecutar sobre una base vacía:  psql "$DATABASE_URL" -f database/schema.sql

BEGIN;

DROP TABLE IF EXISTS detalle_ventas    CASCADE;
DROP TABLE IF EXISTS ventas            CASCADE;
DROP TABLE IF EXISTS inventario_boletos CASCADE;
DROP TABLE IF EXISTS conciertos        CASCADE;
DROP TABLE IF EXISTS localidades       CASCADE;
DROP TABLE IF EXISTS artistas          CASCADE;
DROP TABLE IF EXISTS usuarios          CASCADE;

-- ---------------------------------------------------------------------------
-- usuarios
-- ---------------------------------------------------------------------------
CREATE TABLE usuarios (
    id_usuario      SERIAL       PRIMARY KEY,
    nombre_usuario  VARCHAR(50)  NOT NULL UNIQUE,
    contrasena_hash VARCHAR(255) NOT NULL,               -- bcrypt
    nombre_completo VARCHAR(100) NOT NULL,
    rol             VARCHAR(20)  NOT NULL
                    CHECK (rol IN ('administrador', 'vendedor')),
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- artistas
-- ---------------------------------------------------------------------------
CREATE TABLE artistas (
    id_artista      SERIAL       PRIMARY KEY,
    nombre_artistico VARCHAR(120) NOT NULL,
    genero_musical  VARCHAR(50)  NOT NULL,
    pais_origen     VARCHAR(50)
);

-- ---------------------------------------------------------------------------
-- conciertos
-- ---------------------------------------------------------------------------
CREATE TABLE conciertos (
    id_concierto    SERIAL       PRIMARY KEY,
    id_artista      INTEGER      NOT NULL REFERENCES artistas (id_artista),
    titulo_evento   VARCHAR(150) NOT NULL,
    fecha_concierto TIMESTAMP    NOT NULL,
    recinto         VARCHAR(120) NOT NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'programado'
                    CHECK (estado IN ('programado', 'activo', 'finalizado', 'cancelado'))
);

-- ---------------------------------------------------------------------------
-- localidades  (catálogo: VIP, Platea, General Norte, ...)
-- ---------------------------------------------------------------------------
CREATE TABLE localidades (
    id_localidad SERIAL      PRIMARY KEY,
    nombre       VARCHAR(50) NOT NULL UNIQUE
);

-- ---------------------------------------------------------------------------
-- inventario_boletos  (aforo y precio por concierto + localidad)
-- ---------------------------------------------------------------------------
CREATE TABLE inventario_boletos (
    id_inventario       SERIAL         PRIMARY KEY,
    id_concierto        INTEGER        NOT NULL REFERENCES conciertos (id_concierto),
    id_localidad        INTEGER        NOT NULL REFERENCES localidades (id_localidad),
    precio              DECIMAL(10, 2) NOT NULL CHECK (precio >= 0),
    cantidad_total      INTEGER        NOT NULL CHECK (cantidad_total > 0),
    cantidad_disponible INTEGER        NOT NULL CHECK (cantidad_disponible >= 0),
    CONSTRAINT uq_inventario_concierto_localidad UNIQUE (id_concierto, id_localidad),
    CONSTRAINT ck_inventario_disponible_le_total CHECK (cantidad_disponible <= cantidad_total)
);

-- ---------------------------------------------------------------------------
-- ventas  (cabecera de la transacción)
-- ---------------------------------------------------------------------------
CREATE TABLE ventas (
    id_venta    SERIAL         PRIMARY KEY,
    id_vendedor INTEGER        NOT NULL REFERENCES usuarios (id_usuario),
    fecha_venta TIMESTAMP      NOT NULL DEFAULT NOW(),
    total_venta DECIMAL(10, 2) NOT NULL CHECK (total_venta >= 0)
);

-- ---------------------------------------------------------------------------
-- detalle_ventas  (ítems: un renglón por localidad comprada)
-- ---------------------------------------------------------------------------
CREATE TABLE detalle_ventas (
    id_detalle      SERIAL         PRIMARY KEY,
    id_venta        INTEGER        NOT NULL REFERENCES ventas (id_venta) ON DELETE CASCADE,
    id_inventario   INTEGER        NOT NULL REFERENCES inventario_boletos (id_inventario),
    cantidad        INTEGER        NOT NULL CHECK (cantidad > 0),
    precio_unitario DECIMAL(10, 2) NOT NULL CHECK (precio_unitario >= 0),
    subtotal        DECIMAL(10, 2) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX ix_conciertos_artista       ON conciertos (id_artista);
CREATE INDEX ix_inventario_concierto     ON inventario_boletos (id_concierto);
CREATE INDEX ix_ventas_vendedor          ON ventas (id_vendedor);
CREATE INDEX ix_detalle_ventas_venta     ON detalle_ventas (id_venta);
CREATE INDEX ix_detalle_ventas_inventario ON detalle_ventas (id_inventario);

COMMIT;
