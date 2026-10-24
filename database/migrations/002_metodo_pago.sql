-- Migración para una base ya creada con la versión anterior de schema.sql.
-- (Una base nueva no la necesita: schema.sql ya incluye todo esto.)
--   Agrega ventas.metodo_pago ('efectivo' | 'tarjeta') para registrar cómo se cobró cada venta.
--   Las ventas existentes quedan como 'tarjeta': hasta ahora el único cobro era el simulador de tarjeta.
-- Se puede ejecutar más de una vez sin efecto adicional.
BEGIN;

ALTER TABLE ventas
    ADD COLUMN IF NOT EXISTS metodo_pago VARCHAR(20) NOT NULL DEFAULT 'tarjeta'
        CHECK (metodo_pago IN ('efectivo', 'tarjeta'));

COMMIT;
