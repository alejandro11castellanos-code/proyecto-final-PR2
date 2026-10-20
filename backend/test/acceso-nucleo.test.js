import test from 'node:test';
import assert from 'node:assert/strict';
import {
  crearAntirrebote, describirResultado, normalizarCodigo, pareceBoleto, porcentaje, puedeEscanear,
} from '../public/acceso/nucleo.js';

test('normalizarCodigo quita espacios y pasa a mayúsculas', () => {
  assert.equal(normalizarCodigo('  svbgua-v1-d2-b1-abcd2345 \n'), 'SVBGUA-V1-D2-B1-ABCD2345');
  assert.equal(normalizarCodigo(null), '');
});

test('solo administrador y control de acceso pueden registrar ingresos', () => {
  assert.equal(puedeEscanear('administrador'), true);
  assert.equal(puedeEscanear('acceso'), true);
  assert.equal(puedeEscanear('vendedor'), false);
  assert.equal(puedeEscanear(undefined), false);
});

test('el antirrebote ignora el mismo código dentro de la ventana y lo deja pasar después', () => {
  const antirrebote = crearAntirrebote(5000);
  assert.equal(antirrebote.permitir('A', 0), true);
  assert.equal(antirrebote.permitir('A', 1000), false);
  assert.equal(antirrebote.permitir('B', 1000), true);
  assert.equal(antirrebote.permitir('A', 4999), false);
  assert.equal(antirrebote.permitir('A', 5001), true);
});

test('cada código tiene su propia ventana (un QR que parpadea no se registra dos veces)', () => {
  const antirrebote = crearAntirrebote(5000);
  antirrebote.permitir('A', 0);
  antirrebote.permitir('B', 100);
  assert.equal(antirrebote.permitir('A', 200), false);
});

test('describirResultado: ingreso correcto', () => {
  const r = describirResultado(200, {
    titulo_evento: 'Gira', nombre_localidad: 'Platea', numero: 2, cantidad: 3,
  });
  assert.equal(r.tipo, 'ok');
  assert.equal(r.titulo, 'Ingreso registrado');
  assert.match(r.detalle, /Gira/);
  assert.match(r.detalle, /Platea/);
  assert.match(r.detalle, /boleto 2 de 3/);
});

test('describirResultado: un boleto suelto no dice "1 de 1"', () => {
  const r = describirResultado(200, { titulo_evento: 'Gira', nombre_localidad: 'VIP', numero: 1, cantidad: 1 });
  assert.doesNotMatch(r.detalle, /boleto 1 de 1/);
});

test('describirResultado: rechazos con el motivo del servidor', () => {
  const ya = describirResultado(409, { error: 'Este boleto ya ingresó el 15/10/2026 21:52.' });
  assert.equal(ya.tipo, 'error');
  assert.equal(ya.titulo, 'Acceso denegado');
  assert.equal(ya.detalle, 'Este boleto ya ingresó el 15/10/2026 21:52.');

  assert.equal(describirResultado(404, { error: 'El código no es válido.' }).titulo, 'Código no válido');
  assert.equal(describirResultado(429, {}).titulo, 'Demasiados intentos');
  assert.equal(describirResultado(403, {}).titulo, 'Sin permiso');
  assert.equal(describirResultado(500, {}).titulo, 'Error del servidor');
  assert.equal(describirResultado(0, null).titulo, 'Sin conexión');
});

test('porcentaje redondea y no divide por cero', () => {
  assert.equal(porcentaje(3, 69), 4);
  assert.equal(porcentaje(0, 0), 0);
  assert.equal(porcentaje(5, 5), 100);
});

test('pareceBoleto reconoce el formato de los códigos y descarta otros QR', () => {
  assert.equal(pareceBoleto('SVBGUA-V12-D34-B2-ABCD2345'), true);
  assert.equal(pareceBoleto('https://ejemplo.com/entrada'), false);
  assert.equal(pareceBoleto('SVBGUA-V12-D34'), false);
  assert.equal(pareceBoleto('SVBGUA-V12-D34-B2-ABCD234'), false);
  assert.equal(pareceBoleto('SVBGUA-V12-D34-B2-ABCD0OIL'), false);
  assert.equal(pareceBoleto(''), false);
});
