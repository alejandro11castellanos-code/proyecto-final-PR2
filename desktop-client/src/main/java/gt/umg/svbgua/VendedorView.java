package gt.umg.svbgua;

import java.util.List;

/** Punto de venta del vendedor: vender y consultar sus propias ventas. */
public final class VendedorView extends AppShell {

    public VendedorView(CatalogClient client, String token, String nombreCompleto, Runnable cerrarSesion) {
        super("Punto de venta", nombreCompleto, "Vendedor", List.of(
                new Seccion("Vender", new VentaPane(client, token)),
                new Seccion("Mis ventas", new HistorialPane(client, token, false))), cerrarSesion);
    }
}
