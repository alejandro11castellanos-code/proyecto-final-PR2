import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import QRCode from 'qrcode';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { generarCodigoBoleto } from '../src/services/qrcode.js';

const require = createRequire(import.meta.url);
const jsQR = require('jsqr');
const app = createApp({ jwtSecret: 'secreto-de-prueba' });

test('GET /acceso/ sirve la página del escáner con una política de seguridad estricta', async () => {
  const response = await request(app).get('/acceso/');

  assert.equal(response.status, 200);
  assert.match(response.headers['content-type'], /text\/html/);
  assert.match(response.text, /Control de acceso/);
  const csp = response.headers['content-security-policy'];
  assert.match(csp, /script-src 'self'/);
  assert.doesNotMatch(csp, /unsafe-inline|unsafe-eval/);
  assert.equal(response.headers['permissions-policy'], 'camera=(self)');
  assert.equal(response.headers['x-content-type-options'], 'nosniff');
});

test('la página no usa scripts ni estilos en línea (los bloquearía su propia política)', async () => {
  const { text } = await request(app).get('/acceso/');
  assert.doesNotMatch(text, /<script(?![^>]*\bsrc=)[^>]*>/i);
  assert.doesNotMatch(text, /\sstyle\s*=/i);
  assert.doesNotMatch(text, /\son[a-z]+\s*=/i);
});

for (const [ruta, tipo, contiene] of [
  ['/acceso/scanner.js', /javascript/, /\/accesos/],
  ['/acceso/nucleo.js', /javascript/, /normalizarCodigo/],
  ['/acceso/scanner.css', /css/, /\./],
  ['/acceso/jsqr.js', /javascript/, /jsQR/],
]) {
  test(`GET ${ruta} se sirve desde el propio servidor`, async () => {
    const response = await request(app).get(ruta);
    assert.equal(response.status, 200);
    assert.match(response.headers['content-type'], tipo);
    assert.match(response.text, contiene);
  });
}

test('GET /acceso/enlace.png devuelve un PNG con el QR de la propia página', async () => {
  const response = await request(app).get('/acceso/enlace.png').buffer(true).parse((res, cb) => {
    const partes = [];
    res.on('data', (parte) => partes.push(parte));
    res.on('end', () => cb(null, Buffer.concat(partes)));
  });
  assert.equal(response.status, 200);
  assert.match(response.headers['content-type'], /image\/png/);
  assert.equal(response.body.subarray(1, 4).toString(), 'PNG');
});

test('la librería de lectura decodifica el QR de un boleto tal como lo genera el sistema', () => {
  const codigo = generarCodigoBoleto(12, 34, 2);
  const qr = QRCode.create(codigo, { errorCorrectionLevel: 'M' });
  const escala = 6;
  const margen = 4;
  const lado = (qr.modules.size + margen * 2) * escala;
  const pixeles = new Uint8ClampedArray(lado * lado * 4).fill(255);
  for (let fila = 0; fila < qr.modules.size; fila += 1) {
    for (let columna = 0; columna < qr.modules.size; columna += 1) {
      if (!qr.modules.get(fila, columna)) continue;
      for (let dy = 0; dy < escala; dy += 1) {
        for (let dx = 0; dx < escala; dx += 1) {
          const indice = (((fila + margen) * escala + dy) * lado + (columna + margen) * escala + dx) * 4;
          pixeles[indice] = pixeles[indice + 1] = pixeles[indice + 2] = 0;
        }
      }
    }
  }
  const resultado = jsQR(pixeles, lado, lado);
  assert.equal(resultado?.data, codigo);
});

test('el módulo del escáner no depende del navegador (se puede probar en Node)', () => {
  const fuente = readFileSync(new URL('../public/acceso/nucleo.js', import.meta.url), 'utf8');
  assert.doesNotMatch(fuente, /\bdocument\b|\bwindow\b|\bnavigator\b/);
});
