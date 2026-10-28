import PDFDocument from 'pdfkit';

// IVA de Guatemala: 12 %, ya incluido en el precio de los boletos.
const IVA = 0.12;
const FORMATO_MONTO = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

// Todas las cuentas se hacen en centavos enteros: con decimales, base + IVA podría no sumar el total.
const aCentavos = (valor) => Math.round(Number(valor) * 100);
const quetzales = (centavos) => `Q ${FORMATO_MONTO.format(centavos / 100)}`;

// "2026-10-24T10:05:00" (hora local de Guatemala, sin zona) -> "24/10/2026 10:05".
function formatearFecha(iso) {
  const coincide = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(String(iso));
  return coincide ? `${coincide[3]}/${coincide[2]}/${coincide[1]} ${coincide[4]}:${coincide[5]}` : String(iso);
}

/**
 * NIT en su forma canónica (sin guion ni espacios, en mayúsculas): "1234567-8" -> "12345678"; "CF" y "C/F" -> "CF".
 * Devuelve null si no tiene forma de NIT (dígitos con un dígito verificador, que puede ser K). Solo valida el formato:
 * no consulta a la SAT ni comprueba el dígito verificador.
 */
export function normalizarNit(texto) {
  if (typeof texto !== 'string') return null;
  const limpio = texto.replace(/[\s\-/]/g, '').toUpperCase();
  if (limpio === 'CF') return 'CF';
  return /^\d{1,12}[\dK]$/.test(limpio) ? limpio : null;
}

// "12345678" -> "1234567-8" (el último carácter es el dígito verificador).
const formatearNit = (nit) => (nit === 'CF' ? 'CF' : `${nit.slice(0, -1)}-${nit.slice(-1)}`);

const numeroDeFactura = (idVenta) => `A-${String(idVenta).padStart(6, '0')}`;

/**
 * Datos de la factura ya formateados, sin nada de PDF: se prueban a mano y el dibujo solo los coloca.
 * `venta` es la que devuelve `findById` (con sus renglones). Si no se dan nombre y NIT del cliente, la factura
 * sale a nombre de "Consumidor final / CF".
 */
export function armarFactura(venta, { email, emisor, cliente } = {}) {
  const nit = normalizarNit(cliente?.nit ?? '');
  const total = aCentavos(venta.total_venta);
  const base = Math.round(total / (1 + IVA));

  return {
    numero: numeroDeFactura(venta.id_venta),
    fecha: formatearFecha(venta.fecha_venta),
    metodoPago: venta.metodo_pago === 'efectivo' ? 'Efectivo' : 'Tarjeta',
    vendedor: venta.nombre_vendedor ?? null,
    emisor: emisor ?? { nombre: 'SVB-GUA', nit: null, direccion: null },
    cliente: {
      nombre: (cliente?.nombre ?? '').trim() || 'Consumidor final',
      nit: nit ? formatearNit(nit) : 'CF',
      email: email ?? null,
    },
    lineas: venta.items.map((item) => ({
      descripcion: `${item.titulo_evento}${item.nombre_artistico ? ` (${item.nombre_artistico})` : ''} — ${item.nombre_localidad}`,
      cantidad: item.cantidad,
      precioUnitario: quetzales(aCentavos(item.precio_unitario)),
      subtotal: quetzales(aCentavos(item.subtotal)),
    })),
    base: quetzales(base),
    iva: quetzales(total - base),
    total: quetzales(total),
  };
}

const OSCURO = '#17133F';
const INDIGO = '#4338CA';
const GRIS = '#6B6F94';
const ZEBRA = '#F3F4FA';
const MARGEN = 40;
const ANCHO = 595.28 - 2 * MARGEN;
// Columnas de la tabla de renglones: x, ancho.
const COL = { desc: [MARGEN, 285], cant: [330, 40], precio: [375, 80], subtotal: [460, ANCHO + MARGEN - 460] };

function encabezado(doc, factura) {
  doc.rect(0, 0, doc.page.width, 92).fill(OSCURO);
  doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(24).text('SVB-GUA', MARGEN, 28);
  doc.font('Helvetica').fontSize(9).fillColor('#C9C5F5').text('Sistema de Venta Local de Boletos', MARGEN, 58);
  doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(22).text('FACTURA', MARGEN, 24, { width: ANCHO, align: 'right' });
  doc.font('Helvetica').fontSize(11).text(`No. ${factura.numero}`, MARGEN, 54, { width: ANCHO, align: 'right' });
}

function datosGenerales(doc, factura) {
  const y = 112;
  doc.fillColor(OSCURO).font('Helvetica-Bold').fontSize(11).text(factura.emisor.nombre, MARGEN, y);
  doc.font('Helvetica').fontSize(9).fillColor(GRIS);
  if (factura.emisor.nit) doc.text(`NIT: ${factura.emisor.nit}`);
  if (factura.emisor.direccion) doc.text(factura.emisor.direccion, { width: 250 });

  const lineas = [['Fecha', factura.fecha], ['Pago', factura.metodoPago]];
  if (factura.vendedor) lineas.push(['Atendió', factura.vendedor]);
  doc.font('Helvetica').fontSize(9).fillColor(GRIS);
  lineas.forEach(([etiqueta, valor], i) => {
    doc.text(`${etiqueta}:`, 340, y + i * 14, { width: 60 });
    doc.fillColor(OSCURO).font('Helvetica-Bold').text(valor, 400, y + i * 14, { width: ANCHO + MARGEN - 400 });
    doc.fillColor(GRIS).font('Helvetica');
  });

  // Cliente
  const yc = 170;
  doc.roundedRect(MARGEN, yc, ANCHO, 52, 6).fill(ZEBRA);
  doc.fillColor(GRIS).font('Helvetica-Bold').fontSize(8).text('CLIENTE', MARGEN + 12, yc + 9);
  doc.fillColor(OSCURO).font('Helvetica').fontSize(10);
  doc.text(`Nombre: ${factura.cliente.nombre}`, MARGEN + 12, yc + 23);
  doc.text(`NIT: ${factura.cliente.nit}`, MARGEN + 230, yc + 23);
  if (factura.cliente.email) doc.text(`Correo: ${factura.cliente.email}`, MARGEN + 12, yc + 37);
  return yc + 52 + 20;
}

function cabeceraDeTabla(doc, y) {
  doc.rect(MARGEN, y, ANCHO, 20).fill(INDIGO);
  doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(9);
  doc.text('Descripción', COL.desc[0] + 8, y + 6, { width: COL.desc[1] });
  doc.text('Cant.', COL.cant[0], y + 6, { width: COL.cant[1], align: 'right' });
  doc.text('Precio unit.', COL.precio[0], y + 6, { width: COL.precio[1], align: 'right' });
  doc.text('Subtotal', COL.subtotal[0], y + 6, { width: COL.subtotal[1] - 8, align: 'right' });
  return y + 20;
}

function tablaDeLineas(doc, factura, yInicial) {
  let y = cabeceraDeTabla(doc, yInicial);
  doc.font('Helvetica').fontSize(9.5);
  factura.lineas.forEach((linea, i) => {
    const alto = Math.max(doc.heightOfString(linea.descripcion, { width: COL.desc[1] - 8 }), 12) + 12;
    if (y + alto > doc.page.height - 190) {
      doc.addPage();
      y = cabeceraDeTabla(doc, MARGEN);
      doc.font('Helvetica').fontSize(9.5);
    }
    if (i % 2 === 1) doc.rect(MARGEN, y, ANCHO, alto).fill(ZEBRA);
    doc.fillColor(OSCURO);
    doc.text(linea.descripcion, COL.desc[0] + 8, y + 6, { width: COL.desc[1] - 8 });
    doc.text(String(linea.cantidad), COL.cant[0], y + 6, { width: COL.cant[1], align: 'right' });
    doc.text(linea.precioUnitario, COL.precio[0], y + 6, { width: COL.precio[1], align: 'right' });
    doc.text(linea.subtotal, COL.subtotal[0], y + 6, { width: COL.subtotal[1] - 8, align: 'right' });
    y += alto;
  });
  doc.moveTo(MARGEN, y).lineTo(MARGEN + ANCHO, y).lineWidth(0.5).strokeColor('#D8DCF0').stroke();
  return y;
}

function totales(doc, factura, yTabla) {
  let y = yTabla + 14;
  if (y + 90 > doc.page.height - 60) {
    doc.addPage();
    y = MARGEN;
  }
  const fila = (etiqueta, valor, negrita = false) => {
    doc.fillColor(negrita ? OSCURO : GRIS).font(negrita ? 'Helvetica-Bold' : 'Helvetica').fontSize(negrita ? 13 : 9.5);
    doc.text(etiqueta, 330, y, { width: 120 });
    doc.text(valor, 440, y, { width: ANCHO + MARGEN - 448, align: 'right' });
    y += negrita ? 22 : 16;
  };
  fila('Subtotal (sin IVA)', factura.base);
  fila('IVA (12 %)', factura.iva);
  doc.moveTo(330, y).lineTo(MARGEN + ANCHO, y).lineWidth(0.7).strokeColor(INDIGO).stroke();
  y += 6;
  fila('TOTAL', factura.total, true);
  doc.fillColor(GRIS).font('Helvetica').fontSize(8).text('El total incluye el IVA.', 330, y - 2, { width: ANCHO + MARGEN - 330, align: 'right' });
  return y + 12;
}

function pie(doc) {
  const y = doc.page.height - 78;
  doc.moveTo(MARGEN, y).lineTo(MARGEN + ANCHO, y).lineWidth(0.5).strokeColor('#D8DCF0').stroke();
  doc.fillColor(OSCURO).font('Helvetica-Bold').fontSize(9).text('Gracias por su compra.', MARGEN, y + 8);
  doc.fillColor(GRIS).font('Helvetica').fontSize(7.5).text(
    'Los códigos QR de sus boletos (uno por persona) van adjuntos en el mismo correo. Documento emitido por SVB-GUA, proyecto '
    + 'académico de Programación II (UMG): es un comprobante de compra y no una factura electrónica (FEL) certificada por la SAT.',
    MARGEN, y + 22, { width: ANCHO },
  );
}

/** PDF de la factura como Buffer. */
export function generarFacturaPdf(venta, opciones = {}) {
  const factura = armarFactura(venta, opciones);
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({
      size: 'A4',
      // Margen inferior casi nulo: el pie se coloca a mano muy abajo y no debe provocar un salto de página.
      margins: { top: MARGEN, left: MARGEN, right: MARGEN, bottom: 10 },
      bufferPages: true,
      info: { Title: `Factura ${factura.numero}`, Author: factura.emisor.nombre },
    });
    const trozos = [];
    doc.on('data', (trozo) => trozos.push(trozo));
    doc.on('end', () => resolve(Buffer.concat(trozos)));
    doc.on('error', reject);

    encabezado(doc, factura);
    const yTabla = datosGenerales(doc, factura);
    const yFinal = tablaDeLineas(doc, factura, yTabla);
    totales(doc, factura, yFinal);

    // El pie va en la última página, después de saber cuántas hay.
    const paginas = doc.bufferedPageRange();
    doc.switchToPage(paginas.start + paginas.count - 1);
    pie(doc);
    doc.end();
  });
}

/**
 * Proveedor inyectable en `createApp`, igual que el de los QR: en las pruebas se pasa uno falso.
 * El emisor (nombre, NIT, dirección) se configura con FACTURA_EMISOR, FACTURA_NIT y FACTURA_DIRECCION;
 * sin ellas, la factura sale a nombre de SVB-GUA y sin NIT ni dirección (no se inventan datos fiscales).
 */
export function createFacturaProvider({
  emisor = {
    nombre: process.env.FACTURA_EMISOR || 'SVB-GUA',
    nit: process.env.FACTURA_NIT || null,
    direccion: process.env.FACTURA_DIRECCION || null,
  },
} = {}) {
  return {
    async generar({ venta, email, cliente }) {
      return {
        nombre: `factura-${numeroDeFactura(venta.id_venta)}.pdf`,
        contenido: await generarFacturaPdf(venta, { email, emisor, cliente }),
      };
    },
  };
}
