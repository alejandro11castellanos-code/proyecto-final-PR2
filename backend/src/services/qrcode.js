import { randomInt } from 'node:crypto';
import QRCode from 'qrcode';

// Sin 0, O, 1, I ni L: se confunden al leerlos o teclearlos a mano en la puerta.
const ALFABETO = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const LONGITUD_TOKEN = 8;

/**
 * Código de UN boleto (una persona). Lleva venta, renglón y número para poder
 * ubicarlo a simple vista, y un token aleatorio: sin él, bastaría conocer un
 * código para deducir los demás (V30-D34, V30-D35...) y colarse con uno inventado.
 */
export function generarCodigoBoleto(idVenta, idDetalle, numero) {
  let token = '';
  for (let i = 0; i < LONGITUD_TOKEN; i += 1) {
    token += ALFABETO[randomInt(ALFABETO.length)];
  }
  return `SVBGUA-V${idVenta}-D${idDetalle}-B${numero}-${token}`;
}

/**
 * Genera el QR de un código como data URL (`data:image/png;base64,...`), listo
 * para incrustar en un `<img>` o decodificar del lado del cliente de escritorio.
 */
export function createQrProvider() {
  return {
    async generar(texto) {
      return QRCode.toDataURL(texto, { margin: 1, width: 240 });
    },
  };
}
