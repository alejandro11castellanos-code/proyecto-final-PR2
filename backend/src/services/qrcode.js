import QRCode from 'qrcode';

/** Código legible impreso/escaneado en la puerta para un ítem de una venta. */
export function codigoBoleto(idVenta, idDetalle) {
  return `SVBGUA-V${idVenta}-D${idDetalle}`;
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
