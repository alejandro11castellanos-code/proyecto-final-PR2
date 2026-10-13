package gt.umg.svbgua;

import java.util.List;

/** Panel de administración: catálogo, aforo, ventas, historial y reportes. */
public final class AdminView extends AppShell {

    public AdminView(CatalogClient client, String token, String nombreCompleto, Runnable cerrarSesion) {
        super("Administración", nombreCompleto, "Administrador", secciones(client, token, nombreCompleto),
                cerrarSesion);
    }

    private static List<Seccion> secciones(CatalogClient client, String token, String nombreCompleto) {
        return List.of(
                new Seccion("Usuarios", new UsuariosPane(client, token)),
                new Seccion("Artistas", new ArtistasPane(client, token)),
                new Seccion("Localidades", new LocalidadesPane(client, token)),
                new Seccion("Conciertos", new ConciertosPane(client, token)),
                new Seccion("Aforo", new InventarioPane(client, token)),
                new Seccion("Ventas", new VentaPane(client, token)),
                new Seccion("Historial", new HistorialPane(client, token, true)),
                new Seccion("Reportes", new ReportesPane(client, token, nombreCompleto)));
    }
}
