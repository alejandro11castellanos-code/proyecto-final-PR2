import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { InventarioNoEncontradoError, DisponibilidadInsuficienteError } from '../src/repositories/ventas.js';
import { MailerError } from '../src/services/mailer.js';

const jwtSecret = 'secreto-de-prueba';
const vendedorToken = jwt.sign({ id_usuario: 2, rol: 'vendedor' }, jwtSecret);
const adminToken = jwt.sign({ id_usuario: 1, rol: 'administrador' }, jwtSecret);

const ventaCreada = {
  id_venta: 1,
  id_vendedor: 2,
  fecha_venta: '2026-09-11T00:00:00.000Z',
  total_venta: '900.00',
  items: [{ id_inventario: 1, cantidad: 2, precio_unitario: 450, subtotal: 900 }],
};

// Forma real de findById: con los joins de localidad/concierto que necesita
// la generación de boletos (a diferencia de lo que devuelve "crear").
const ventaConDetalle = {
  id_venta: 1,
  id_vendedor: 2,
  fecha_venta: '2026-09-11T00:00:00.000Z',
  total_venta: '900.00',
  items: [{
    id_detalle: 10,
    id_inventario: 1,
    cantidad: 2,
    precio_unitario: '450.00',
    subtotal: '900.00',
    nombre_localidad: 'Platea',
    id_concierto: 1,
    titulo_evento: 'Gira Aniversario',
    nombre_artistico: 'Los Miserables',
    boletos: [
      { id_boleto: 1, numero: 1, codigo: 'SVBGUA-V1-D10-B1-ABCD2345', fecha_ingreso: null },
      { id_boleto: 2, numero: 2, codigo: 'SVBGUA-V1-D10-B2-WXYZ6789', fecha_ingreso: '2026-10-15T20:14:00' },
    ],
  }],
};

function fakeVentaRepository({ crear, findById, listar } = {}) {
  return {
    crear: crear ?? (async () => ventaCreada),
    findById: findById ?? (async () => null),
    listar: listar ?? (async () => []),
  };
}

function fakeQrProvider() {
  return {
    async generar(texto) {
      return `data:image/png;base64,${Buffer.from(texto).toString('base64')}`;
    },
  };
}

function buildApp(repoOverrides, { qrProvider, mailer } = {}) {
  return createApp({
    ventaRepository: fakeVentaRepository(repoOverrides),
    qrProvider: qrProvider ?? fakeQrProvider(),
    mailer: mailer ?? { enviarBoletos: async () => {} },
    jwtSecret,
  });
}

test('POST /ventas exige autenticación', async () => {
  const response = await request(buildApp()).post('/ventas').send({ items: [{ id_inventario: 1, cantidad: 1 }] });
  assert.equal(response.status, 401);
});

test('POST /ventas rechaza una lista de items vacía o ausente', async () => {
  const app = buildApp();
  for (const body of [{}, { items: [] }, { items: 'no-es-lista' }]) {
    const response = await request(app).post('/ventas').set('Authorization', `Bearer ${vendedorToken}`).send(body);
    assert.equal(response.status, 400);
  }
});

test('POST /ventas rechaza items con id_inventario o cantidad inválidos', async () => {
  const app = buildApp();
  for (const items of [
    [{ id_inventario: 0, cantidad: 1 }],
    [{ id_inventario: 1, cantidad: 0 }],
    [{ id_inventario: 1, cantidad: -3 }],
    [{ cantidad: 1 }],
    [{ id_inventario: 1 }],
  ]) {
    const response = await request(app).post('/ventas').set('Authorization', `Bearer ${vendedorToken}`).send({ items });
    assert.equal(response.status, 400);
  }
});

test('POST /ventas rechaza items repetidos', async () => {
  const app = buildApp();
  const response = await request(app)
    .post('/ventas')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ items: [{ id_inventario: 1, cantidad: 1 }, { id_inventario: 1, cantidad: 2 }] });
  assert.equal(response.status, 400);
});

test('POST /ventas crea la venta y usa el id del token como vendedor', async () => {
  let idVendedorRecibido;
  const app = buildApp({
    crear: async ({ idVendedor }) => {
      idVendedorRecibido = idVendedor;
      return ventaCreada;
    },
  });

  const response = await request(app)
    .post('/ventas')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ items: [{ id_inventario: 1, cantidad: 2 }] });

  assert.equal(response.status, 201);
  assert.deepEqual(response.body, ventaCreada);
  assert.equal(idVendedorRecibido, 2);
});

test('POST /ventas responde 404 si algún inventario no existe', async () => {
  const app = buildApp({
    crear: async () => {
      throw new InventarioNoEncontradoError(99);
    },
  });

  const response = await request(app)
    .post('/ventas')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ items: [{ id_inventario: 99, cantidad: 1 }] });

  assert.equal(response.status, 404);
});

test('POST /ventas responde 409 si no hay disponibilidad suficiente', async () => {
  const app = buildApp({
    crear: async () => {
      throw new DisponibilidadInsuficienteError(1, 3, 10);
    },
  });

  const response = await request(app)
    .post('/ventas')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ items: [{ id_inventario: 1, cantidad: 10 }] });

  assert.equal(response.status, 409);
});

test('GET /ventas exige autenticación', async () => {
  const response = await request(buildApp()).get('/ventas');
  assert.equal(response.status, 401);
});

test('GET /ventas rechaza que un vendedor filtre por otro vendedor', async () => {
  let recibido;
  const app = buildApp({
    listar: async (filtros) => {
      recibido = filtros;
      return [];
    },
  });

  const response = await request(app)
    .get('/ventas?vendedor=99')
    .set('Authorization', `Bearer ${vendedorToken}`);

  assert.equal(response.status, 400);
  assert.equal(recibido, undefined);
});

test('GET /ventas (vendedor) sin filtro trae su propio historial', async () => {
  let recibido;
  const app = buildApp({
    listar: async (filtros) => {
      recibido = filtros;
      return [{ id_venta: 1, id_vendedor: 2, nombre_vendedor: 'Ana', total_venta: '100.00', boletos: 2 }];
    },
  });

  const response = await request(app).get('/ventas').set('Authorization', `Bearer ${vendedorToken}`);

  assert.equal(response.status, 200);
  assert.equal(response.body.length, 1);
  assert.deepEqual(recibido, { idVendedor: 2, idConcierto: null });
});

test('GET /ventas (administrador) puede filtrar por vendedor y por concierto', async () => {
  let recibido;
  const app = buildApp({
    listar: async (filtros) => {
      recibido = filtros;
      return [];
    },
  });

  const response = await request(app)
    .get('/ventas?vendedor=3&concierto=1')
    .set('Authorization', `Bearer ${adminToken}`);

  assert.equal(response.status, 200);
  assert.deepEqual(recibido, { idVendedor: 3, idConcierto: 1 });
});

test('GET /ventas rechaza un filtro no numérico', async () => {
  const response = await request(buildApp())
    .get('/ventas?concierto=abc')
    .set('Authorization', `Bearer ${adminToken}`);
  assert.equal(response.status, 400);
});

test('GET /ventas/:id responde 404 si no existe', async () => {
  const response = await request(buildApp())
    .get('/ventas/999')
    .set('Authorization', `Bearer ${vendedorToken}`);
  assert.equal(response.status, 404);
});

test('GET /ventas/:id devuelve la venta con sus ítems', async () => {
  const app = buildApp({ findById: async (id) => (Number(id) === 1 ? ventaCreada : null) });

  const response = await request(app).get('/ventas/1').set('Authorization', `Bearer ${vendedorToken}`);

  assert.equal(response.status, 200);
  assert.deepEqual(response.body, ventaCreada);
});

test('GET /ventas/:id/boletos responde 404 si la venta no existe', async () => {
  const response = await request(buildApp())
    .get('/ventas/999/boletos')
    .set('Authorization', `Bearer ${vendedorToken}`);
  assert.equal(response.status, 404);
});

test('GET /ventas/:id/boletos genera un QR por cada persona (no por renglón)', async () => {
  const app = buildApp({ findById: async (id) => (Number(id) === 1 ? ventaConDetalle : null) });

  const response = await request(app).get('/ventas/1/boletos').set('Authorization', `Bearer ${vendedorToken}`);

  assert.equal(response.status, 200);
  assert.equal(response.body.id_venta, 1);
  assert.equal(response.body.boletos.length, 2);
  const [primero, segundo] = response.body.boletos;
  assert.equal(primero.codigo, 'SVBGUA-V1-D10-B1-ABCD2345');
  assert.equal(segundo.codigo, 'SVBGUA-V1-D10-B2-WXYZ6789');
  assert.equal(primero.nombre_localidad, 'Platea');
  assert.equal(primero.numero, 1);
  assert.equal(primero.cantidad, 2);
  assert.equal(primero.ingresado, false);
  assert.equal(segundo.ingresado, true);
  assert.match(primero.qr, /^data:image\/png;base64,/);
  // El QR codifica exactamente el código del boleto.
  assert.equal(Buffer.from(primero.qr.split(',')[1], 'base64').toString(), primero.codigo);
});

test('el rol de control de acceso no puede vender ni consultar ventas', async () => {
  const porteroToken = jwt.sign({ id_usuario: 4, rol: 'acceso' }, jwtSecret);
  const app = buildApp({ findById: async () => ventaConDetalle });
  for (const [metodo, ruta] of [['post', '/ventas'], ['get', '/ventas'], ['get', '/ventas/1'], ['get', '/ventas/1/boletos'], ['post', '/ventas/1/enviar']]) {
    const response = await request(app)[metodo](ruta).set('Authorization', `Bearer ${porteroToken}`).send({});
    assert.equal(response.status, 403, `${metodo.toUpperCase()} ${ruta}`);
  }
});

test('POST /ventas/:id/enviar valida el formato del email', async () => {
  const app = buildApp({ findById: async () => ventaConDetalle });

  const response = await request(app)
    .post('/ventas/1/enviar')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ email: 'esto-no-es-un-email' });

  assert.equal(response.status, 400);
});

test('POST /ventas/:id/enviar responde 404 si la venta no existe', async () => {
  const response = await request(buildApp())
    .post('/ventas/999/enviar')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ email: 'ana@example.com' });
  assert.equal(response.status, 404);
});

test('POST /ventas/:id/enviar arma los boletos y se los pasa al mailer', async () => {
  let recibido;
  const mailer = {
    async enviarBoletos(args) {
      recibido = args;
    },
  };
  const app = buildApp({ findById: async () => ventaConDetalle }, { mailer });

  const response = await request(app)
    .post('/ventas/1/enviar')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ email: 'ana@example.com' });

  assert.equal(response.status, 200);
  assert.deepEqual(response.body, { enviado: true, destinatario: 'ana@example.com' });
  assert.equal(recibido.destinatario, 'ana@example.com');
  assert.equal(recibido.boletos.length, 2);
  assert.equal(recibido.boletos[0].codigo, 'SVBGUA-V1-D10-B1-ABCD2345');
});

test('POST /ventas/:id/enviar responde 502 si el correo no se pudo enviar', async () => {
  const mailer = {
    async enviarBoletos() {
      throw new MailerError('El servidor de correo no está configurado (SMTP_HOST).');
    },
  };
  const app = buildApp({ findById: async () => ventaConDetalle }, { mailer });

  const response = await request(app)
    .post('/ventas/1/enviar')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ email: 'ana@example.com' });

  assert.equal(response.status, 502);
});

// --- Una compra puede mezclar boletos de varios conciertos ---

test('POST /ventas acepta renglones de inventarios (conciertos) distintos en una sola venta', async () => {
  let itemsRecibidos;
  const app = buildApp({
    crear: async ({ items }) => {
      itemsRecibidos = items;
      return ventaCreada;
    },
  });

  const response = await request(app)
    .post('/ventas')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ items: [{ id_inventario: 6, cantidad: 2 }, { id_inventario: 9, cantidad: 1 }] });

  assert.equal(response.status, 201);
  assert.deepEqual(itemsRecibidos, [
    { idInventario: 6, cantidad: 2 },
    { idInventario: 9, cantidad: 1 },
  ]);
});

test('GET /ventas/:id/boletos conserva el concierto de cada boleto cuando la venta mezcla conciertos', async () => {
  const [primerRenglon] = ventaConDetalle.items;
  const mixta = {
    ...ventaConDetalle,
    items: [
      { ...primerRenglon, id_detalle: 10, titulo_evento: 'RHCP', boletos: [primerRenglon.boletos[0]] },
      {
        ...primerRenglon,
        id_detalle: 11,
        id_inventario: 9,
        id_concierto: 4,
        titulo_evento: 'Concierto de prueba',
        boletos: [{ id_boleto: 3, numero: 1, codigo: 'SVBGUA-V1-D11-B1-QRST2345', fecha_ingreso: null }],
      },
    ],
  };
  const app = buildApp({ findById: async () => mixta });

  const response = await request(app).get('/ventas/1/boletos').set('Authorization', `Bearer ${vendedorToken}`);

  assert.equal(response.status, 200);
  assert.deepEqual(response.body.boletos.map((boleto) => boleto.titulo_evento), ['RHCP', 'Concierto de prueba']);
});

test('DisponibilidadInsuficienteError nombra el concierto y la localidad cuando se conocen', () => {
  const error = new DisponibilidadInsuficienteError(6, 5, 9, 'RHCP — General Norte');
  assert.match(error.message, /RHCP — General Norte/);
  assert.match(error.message, /pidieron 9 y hay 5/);
  assert.equal(error.idInventario, 6);
});

test('DisponibilidadInsuficienteError sin nombres conserva el mensaje con el id de inventario', () => {
  const error = new DisponibilidadInsuficienteError(6, 5, 9);
  assert.match(error.message, /inventario 6/);
});

// --- Método de pago (efectivo / tarjeta) ---

const unItem = { items: [{ id_inventario: 1, cantidad: 2 }] };

async function postVenta(body, crear) {
  const app = buildApp({ crear });
  return request(app).post('/ventas').set('Authorization', `Bearer ${vendedorToken}`).send(body);
}

test('POST /ventas sin metodo_pago asume tarjeta (clientes anteriores solo tenían el simulador de tarjeta)', async () => {
  let recibido;
  const response = await postVenta(unItem, async (datos) => {
    recibido = datos;
    return ventaCreada;
  });

  assert.equal(response.status, 201);
  assert.equal(recibido.metodoPago, 'tarjeta');
});

test('POST /ventas guarda el método de pago pedido (efectivo)', async () => {
  let recibido;
  const response = await postVenta({ ...unItem, metodo_pago: 'efectivo' }, async (datos) => {
    recibido = datos;
    return { ...ventaCreada, metodo_pago: 'efectivo' };
  });

  assert.equal(response.status, 201);
  assert.equal(recibido.metodoPago, 'efectivo');
  assert.equal(response.body.metodo_pago, 'efectivo');
});

for (const [descripcion, valor] of [
  ['un método desconocido', 'cheque'],
  ['un método vacío', ''],
  ['un método que no es texto', 7],
  ['una variante en mayúsculas', 'Efectivo'],
]) {
  test(`POST /ventas responde 400 ante ${descripcion}`, async () => {
    let llamadas = 0;
    const response = await postVenta({ ...unItem, metodo_pago: valor }, async () => {
      llamadas += 1;
      return ventaCreada;
    });

    assert.equal(response.status, 400);
    assert.match(response.body.error, /metodo_pago/);
    assert.equal(llamadas, 0, 'no debe tocar la base con un método inválido');
  });
}
