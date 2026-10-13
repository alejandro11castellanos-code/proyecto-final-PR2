package gt.umg.svbgua;

import java.util.List;

/** Punto de venta del vendedor: vender y consultar sus propias ventas. */
public final class VendedorView extends AppShell {

    public VendedorView(CatalogClient client, String token, String nombreCompleto, Runnable cerrarSesion) {
        super("Punto de venta", nombreCompleto, "Vendedor", secciones(client, token), cerrarSesion);
    }

    private static List<Seccion> secciones(CatalogClient client, String token) {
        VentaPane ventas = new VentaPane(client, token);
        HistorialPane historial = new HistorialPane(client, token, false);
        return List.of(
                new Seccion("Vender", ventas, ventas::actualizar),
                new Seccion("Mis ventas", historial, historial::actualizar));
    }
}
