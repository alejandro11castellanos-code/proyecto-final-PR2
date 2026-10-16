package gt.umg.svbgua;

import java.util.List;

/** Panel de administración: catálogo, aforo, ventas, historial y reportes. */
public final class AdminView extends AppShell {

    public AdminView(CatalogClient client, String token, String nombreCompleto, Runnable cerrarSesion) {
        super("Administración", nombreCompleto, "Administrador", secciones(client, token, nombreCompleto),
                cerrarSesion);
    }

    private static List<Seccion> secciones(CatalogClient client, String token, String nombreCompleto) {
        InventarioPane aforo = new InventarioPane(client, token);
        VentaPane ventas = new VentaPane(client, token);
        HistorialPane historial = new HistorialPane(client, token, true);
        AccesoPane acceso = new AccesoPane(client, token);
        ReportesPane reportes = new ReportesPane(client, token, nombreCompleto);
        // Estas vistas dependen de ventas que se hacen en otra sección: se refrescan al abrirlas.
        return List.of(
                new Seccion("Usuarios", new UsuariosPane(client, token)),
                new Seccion("Artistas", new ArtistasPane(client, token)),
                new Seccion("Localidades", new LocalidadesPane(client, token)),
                new Seccion("Conciertos", new ConciertosPane(client, token)),
                new Seccion("Aforo", aforo, aforo::actualizar),
                new Seccion("Ventas", ventas, ventas::actualizar),
                new Seccion("Historial", historial, historial::actualizar),
                new Seccion("Control de acceso", acceso, acceso::alAbrir),
                new Seccion("Reportes", reportes, reportes::actualizar));
    }
}
