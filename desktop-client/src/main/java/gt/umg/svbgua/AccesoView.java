package gt.umg.svbgua;

import java.util.List;

/** Pantalla del rol de control de acceso (la puerta): solo registra ingresos. */
public final class AccesoView extends AppShell {

    public AccesoView(CatalogClient client, String token, String nombreCompleto, Runnable cerrarSesion) {
        super("Control de acceso", nombreCompleto, "Control de acceso", secciones(client, token), cerrarSesion);
    }

    private static List<Seccion> secciones(CatalogClient client, String token) {
        AccesoPane acceso = new AccesoPane(client, token);
        return List.of(new Seccion("Ingreso", acceso, acceso::alAbrir));
    }
}
