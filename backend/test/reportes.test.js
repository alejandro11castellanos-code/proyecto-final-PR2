import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';

const jwtSecret = 'secreto-de-prueba';
const adminToken = jwt.sign({ id_usuario: 1, rol: 'administrador' }, jwtSecret);
const vendedorToken = jwt.sign({ id_usuario: 2, rol: 'vendedor' }, jwtSecret);

const resumen = { ingresos_totales: '900.00', boletos_vendidos: 3, precio_promedio: '300.00' };
const porConcierto = [{ id_concierto: 1, titulo_evento: 'Gira', nombre_artistico: 'X', ingresos: '900.00', boletos: 3 }];
const porArtista = [{ id_artista: 1, nombre_artistico: 'X', ingresos: '900.00', boletos: 3 }];
const porVendedor = [{ id_usuario: 2, nombre_completo: 'Ana', rol: 'vendedor', ingresos: '900.00', ventas: 1 }];
const porDia = [{ dia: '2026-09-20', ingresos: '900.00', boletos: 3 }];
const porLocalidad = [{ id_localidad: 2, nombre: 'Platea', boletos: 3 }];
const ocupacion = [{ id_concierto: 1, titulo_evento: 'Gira', cantidad_total: 300, vendido: 3 }];

function fakeReporteRepository() {
  return {
    resumen: async () => resumen,
    porConcierto: async () => porConcierto,
    porArtista: async () => porArtista,
    porVendedor: async () => porVendedor,
    porDia: async () => porDia,
    porLocalidad: async () => porLocalidad,
    ocupacion: async () => ocupacion,
  };
}

function buildApp() {
  return createApp({ reporteRepository: fakeReporteRepository(), jwtSecret });
}

test('GET /reportes/dashboard exige autenticación', async () => {
  const response = await request(buildApp()).get('/reportes/dashboard');
  assert.equal(response.status, 401);
});

test('GET /reportes/dashboard rechaza a un vendedor', async () => {
  const response = await request(buildApp())
    .get('/reportes/dashboard')
    .set('Authorization', `Bearer ${vendedorToken}`);
  assert.equal(response.status, 403);
});

test('GET /reportes/dashboard devuelve las siete secciones para un administrador', async () => {
  const response = await request(buildApp())
    .get('/reportes/dashboard')
    .set('Authorization', `Bearer ${adminToken}`);

  assert.equal(response.status, 200);
  assert.deepEqual(response.body, {
    resumen,
    por_concierto: porConcierto,
    por_artista: porArtista,
    por_vendedor: porVendedor,
    por_dia: porDia,
    por_localidad: porLocalidad,
    ocupacion,
  });
});

test('GET /reportes/dashboard propaga un fallo del repositorio como 500', async () => {
  const repo = fakeReporteRepository();
  repo.resumen = async () => {
    throw new Error('boom');
  };
  const app = createApp({ reporteRepository: repo, jwtSecret });

  const response = await request(app).get('/reportes/dashboard').set('Authorization', `Bearer ${adminToken}`);
  assert.equal(response.status, 500);
});

const sinFiltros = { desde: null, hasta: null, idArtista: null, idConcierto: null, idVendedor: null, idLocalidad: null };

function fakeRepositoryQueGrabaFiltros() {
  const recibidos = [];
  const repo = fakeReporteRepository();
  for (const nombre of Object.keys(repo)) {
    repo[nombre] = async (filtros) => {
      recibidos.push({ nombre, filtros });
      return [];
    };
  }
  return { repo, recibidos };
}

test('GET /reportes/dashboard sin query pasa filtros vacíos a cada consulta', async () => {
  const { repo, recibidos } = fakeRepositoryQueGrabaFiltros();
  const app = createApp({ reporteRepository: repo, jwtSecret });

  const response = await request(app).get('/reportes/dashboard').set('Authorization', `Bearer ${adminToken}`);

  assert.equal(response.status, 200);
  assert.equal(recibidos.length, 7);
  for (const { filtros } of recibidos) assert.deepEqual(filtros, sinFiltros);
});

test('GET /reportes/dashboard traduce la query a filtros tipados', async () => {
  const { repo, recibidos } = fakeRepositoryQueGrabaFiltros();
  const app = createApp({ reporteRepository: repo, jwtSecret });

  const response = await request(app)
    .get('/reportes/dashboard?desde=2026-09-01&hasta=2026-09-30&artista=3&concierto=4&vendedor=5&localidad=6')
    .set('Authorization', `Bearer ${adminToken}`);

  assert.equal(response.status, 200);
  const esperado = {
    desde: '2026-09-01', hasta: '2026-09-30', idArtista: 3, idConcierto: 4, idVendedor: 5, idLocalidad: 6,
  };
  for (const { filtros } of recibidos) assert.deepEqual(filtros, esperado);
});

test('GET /reportes/dashboard acepta un filtro parcial (solo desde)', async () => {
  const { repo, recibidos } = fakeRepositoryQueGrabaFiltros();
  const app = createApp({ reporteRepository: repo, jwtSecret });

  await request(app).get('/reportes/dashboard?desde=2026-09-01').set('Authorization', `Bearer ${adminToken}`);

  for (const { filtros } of recibidos) assert.deepEqual(filtros, { ...sinFiltros, desde: '2026-09-01' });
});

for (const [descripcion, query] of [
  ['una fecha mal formada', 'desde=01/09/2026'],
  ['una fecha inexistente', 'hasta=2026-02-31'],
  ['un rango invertido', 'desde=2026-09-30&hasta=2026-09-01'],
  ['un id no numérico', 'artista=abc'],
  ['un id cero o negativo', 'concierto=0'],
  ['un id decimal', 'vendedor=1.5'],
  ['un id vacío', 'localidad='],
]) {
  test(`GET /reportes/dashboard responde 400 ante ${descripcion}`, async () => {
    const { repo, recibidos } = fakeRepositoryQueGrabaFiltros();
    const app = createApp({ reporteRepository: repo, jwtSecret });

    const response = await request(app).get(`/reportes/dashboard?${query}`).set('Authorization', `Bearer ${adminToken}`);

    assert.equal(response.status, 400);
    assert.ok(response.body.error);
    assert.equal(recibidos.length, 0, 'no debe consultar la BD con filtros inválidos');
  });
}
