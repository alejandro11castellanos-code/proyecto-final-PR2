import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';

const jwtSecret = 'secreto-de-prueba';
const adminToken = jwt.sign({ id_usuario: 1, rol: 'administrador' }, jwtSecret);
const vendedorToken = jwt.sign({ id_usuario: 2, rol: 'vendedor' }, jwtSecret);
const porteroToken = jwt.sign({ id_usuario: 4, rol: 'acceso' }, jwtSecret);

const boletoValido = {
  id_boleto: 7,
  codigo: 'SVBGUA-V1-D10-B1-ABCD2345',
  numero: 1,
  cantidad: 2,
  fecha_ingreso: null,
  id_concierto: 1,
  titulo_evento: 'Gira Aniversario',
  nombre_artistico: 'Los Miserables',
  nombre_localidad: 'Platea',
  estado_concierto: 'activo',
};

function fakeAccesoRepository({ boleto = boletoValido, marcar } = {}) {
  const llamadas = { marcar: [] };
  return {
    llamadas,
    async buscarPorCodigo(codigo) {
      return boleto && codigo === boleto.codigo ? { ...boleto } : null;
    },
    async marcarIngreso(args) {
      llamadas.marcar.push(args);
      if (marcar) return marcar(args);
      return { ...boleto, fecha_ingreso: '2026-10-15T20:14:00' };
    },
    async resumenPorConcierto() {
      return [{ id_concierto: 1, titulo_evento: 'Gira', nombre_artistico: 'X', vendidos: 10, ingresados: 4 }];
    },
    async ingresosRecientes(filtros) {
      llamadas.recientes = filtros;
      return [{ id_boleto: 7, codigo: boletoValido.codigo, fecha_ingreso: '2026-10-15T20:14:00' }];
    },
  };
}

function buildApp(opciones) {
  const accesoRepository = fakeAccesoRepository(opciones);
  return { app: createApp({ accesoRepository, jwtSecret }), accesoRepository };
}

const escanear = (app, token, cuerpo) => request(app).post('/accesos').set('Authorization', `Bearer ${token}`).send(cuerpo);

test('POST /accesos exige autenticación', async () => {
  const { app } = buildApp();
  const response = await request(app).post('/accesos').send({ codigo: boletoValido.codigo });
  assert.equal(response.status, 401);
});

test('POST /accesos rechaza a un vendedor (solo administrador y control de acceso)', async () => {
  const { app, accesoRepository } = buildApp();
  const response = await escanear(app, vendedorToken, { codigo: boletoValido.codigo });
  assert.equal(response.status, 403);
  assert.equal(accesoRepository.llamadas.marcar.length, 0);
});

for (const [descripcion, cuerpo] of [['sin código', {}], ['con código vacío', { codigo: '   ' }], ['con código no textual', { codigo: 42 }]]) {
  test(`POST /accesos responde 400 ${descripcion}`, async () => {
    const { app } = buildApp();
    const response = await escanear(app, porteroToken, cuerpo);
    assert.equal(response.status, 400);
  });
}

test('POST /accesos responde 404 ante un código que no existe', async () => {
  const { app, accesoRepository } = buildApp();
  const response = await escanear(app, porteroToken, { codigo: 'SVBGUA-V9-D9-B9-ZZZZZZZZ' });
  assert.equal(response.status, 404);
  assert.match(response.body.error, /no es válido/i);
  assert.equal(accesoRepository.llamadas.marcar.length, 0);
});

test('POST /accesos registra el ingreso de un boleto válido con el id del validador', async () => {
  const { app, accesoRepository } = buildApp();
  const response = await escanear(app, porteroToken, { codigo: boletoValido.codigo });

  assert.equal(response.status, 200);
  assert.equal(response.body.codigo, boletoValido.codigo);
  assert.equal(response.body.nombre_localidad, 'Platea');
  assert.equal(response.body.numero, 1);
  assert.equal(response.body.cantidad, 2);
  assert.equal(response.body.fecha_ingreso, '2026-10-15T20:14:00');
  assert.deepEqual(accesoRepository.llamadas.marcar, [{ codigo: boletoValido.codigo, idValidador: 4 }]);
});

test('POST /accesos acepta el código con espacios y en minúsculas (lo que escribe una persona)', async () => {
  const { app } = buildApp();
  const response = await escanear(app, adminToken, { codigo: `  ${boletoValido.codigo.toLowerCase()} ` });
  assert.equal(response.status, 200);
});

test('POST /accesos responde 409 si el boleto ya ingresó, indicando cuándo, y no lo marca otra vez', async () => {
  const { app, accesoRepository } = buildApp({ boleto: { ...boletoValido, fecha_ingreso: '2026-10-15T19:05:00' } });
  const response = await escanear(app, porteroToken, { codigo: boletoValido.codigo });

  assert.equal(response.status, 409);
  assert.match(response.body.error, /ya ingresó/i);
  assert.match(response.body.error, /15\/10\/2026 19:05/);
  assert.equal(accesoRepository.llamadas.marcar.length, 0);
});

test('POST /accesos responde 409 si otro escáner ganó la carrera (marcarIngreso devuelve null)', async () => {
  const repo = fakeAccesoRepository({ marcar: async () => null });
  const buscar = repo.buscarPorCodigo;
  let consultas = 0;
  repo.buscarPorCodigo = async (codigo) => {
    consultas += 1;
    const boleto = await buscar(codigo);
    // Tras perder la carrera, la segunda consulta ya ve el ingreso del otro escáner.
    return consultas > 1 ? { ...boleto, fecha_ingreso: '2026-10-15T20:00:00' } : boleto;
  };

  const response = await escanear(createApp({ accesoRepository: repo, jwtSecret }), porteroToken, { codigo: boletoValido.codigo });
  assert.equal(response.status, 409);
  assert.match(response.body.error, /ya ingresó/i);
});

for (const estado of ['cancelado', 'finalizado']) {
  test(`POST /accesos responde 409 si el concierto está ${estado} y no marca el boleto`, async () => {
    const { app, accesoRepository } = buildApp({ boleto: { ...boletoValido, estado_concierto: estado } });
    const response = await escanear(app, porteroToken, { codigo: boletoValido.codigo });
    assert.equal(response.status, 409);
    assert.match(response.body.error, new RegExp(estado));
    assert.equal(accesoRepository.llamadas.marcar.length, 0);
  });
}

test('GET /accesos/resumen lo ve el administrador y el control de acceso, no el vendedor', async () => {
  const { app } = buildApp();
  for (const [token, esperado] of [[adminToken, 200], [porteroToken, 200], [vendedorToken, 403]]) {
    const response = await request(app).get('/accesos/resumen').set('Authorization', `Bearer ${token}`);
    assert.equal(response.status, esperado);
  }
  const ok = await request(app).get('/accesos/resumen').set('Authorization', `Bearer ${adminToken}`);
  assert.deepEqual(ok.body[0], { id_concierto: 1, titulo_evento: 'Gira', nombre_artistico: 'X', vendidos: 10, ingresados: 4 });
});

test('GET /accesos lista los ingresos recientes y valida el filtro por concierto', async () => {
  const { app, accesoRepository } = buildApp();
  const ok = await request(app).get('/accesos?concierto=3').set('Authorization', `Bearer ${porteroToken}`);
  assert.equal(ok.status, 200);
  assert.equal(accesoRepository.llamadas.recientes.idConcierto, 3);

  const mal = await request(app).get('/accesos?concierto=abc').set('Authorization', `Bearer ${porteroToken}`);
  assert.equal(mal.status, 400);
});
