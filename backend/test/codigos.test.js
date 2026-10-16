import test from 'node:test';
import assert from 'node:assert/strict';
import { generarCodigoBoleto } from '../src/services/qrcode.js';

test('el código lleva venta, renglón, número y un token aleatorio', () => {
  assert.match(generarCodigoBoleto(12, 34, 2), /^SVBGUA-V12-D34-B2-[A-Z2-9]{8}$/);
});

test('el token no usa caracteres que se confunden al leerlos (0, O, 1, I, L)', () => {
  for (let i = 0; i < 300; i += 1) {
    const token = generarCodigoBoleto(1, 1, 1).split('-').pop();
    assert.doesNotMatch(token, /[01OIL]/);
  }
});

test('dos boletos del mismo renglón no comparten código (no son adivinables)', () => {
  const codigos = new Set();
  for (let i = 0; i < 2000; i += 1) codigos.add(generarCodigoBoleto(1, 1, 1));
  assert.equal(codigos.size, 2000);
});
