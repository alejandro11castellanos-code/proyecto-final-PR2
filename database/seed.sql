-- SVB-GUA · Datos de prueba
-- Ejecutar despues de schema.sql:  psql "$DATABASE_URL" -f database/seed.sql
-- Las contrasenas de prueba son 'admin123' y 'vendedor123' (hash bcrypt, coste 10).
-- Regenerar hashes:  node backend/scripts/hash.js <password>

BEGIN;

TRUNCATE detalle_ventas, ventas, inventario_boletos, conciertos, localidades, artistas, usuarios
    RESTART IDENTITY CASCADE;

INSERT INTO usuarios (nombre_usuario, contrasena_hash, nombre_completo, rol) VALUES
    ('admin',     '__BCRYPT_ADMIN__',    'Administrador General', 'administrador'),
    ('vendedor1', '__BCRYPT_VENDEDOR__', 'Ana Lopez',             'vendedor'),
    ('vendedor2', '__BCRYPT_VENDEDOR__', 'Carlos Perez',          'vendedor');

INSERT INTO artistas (nombre_artistico, genero_musical, pais_origen) VALUES
    ('Los Miserables',   'Rock',        'Guatemala'),
    ('Gaby Moreno',      'Folk',        'Guatemala'),
    ('Bohemia Suburbana','Rock Alterno','Guatemala');

INSERT INTO conciertos (id_artista, titulo_evento, fecha_concierto, recinto, estado) VALUES
    (1, 'Gira Aniversario 2026',  '2026-11-15 20:00', 'Gran Sala Efrain Recinos', 'activo'),
    (2, 'Noche Acustica',         '2026-12-05 19:30', 'Teatro Nacional',          'programado');

INSERT INTO localidades (nombre) VALUES
    ('VIP'),
    ('Platea'),
    ('General Norte'),
    ('General Sur');

-- Inventario del concierto 1 (id_concierto = 1)
INSERT INTO inventario_boletos (id_concierto, id_localidad, precio, cantidad_total, cantidad_disponible) VALUES
    (1, 1, 750.00, 100,  100),
    (1, 2, 450.00, 300,  300),
    (1, 3, 200.00, 500,  500),
    (1, 4, 200.00, 500,  500);

COMMIT;
