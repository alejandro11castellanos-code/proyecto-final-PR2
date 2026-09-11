package gt.umg.svbgua;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

/** Panel de administración: pestañas de artistas, localidades y conciertos. */
public final class AdminView extends BorderPane {

    public AdminView(CatalogClient client, String token, String nombreCompleto) {
        Label header = new Label("Panel de administración — " + nombreCompleto);
        header.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        header.setPadding(new Insets(16, 16, 0, 16));
        setTop(header);

        TabPane tabs = new TabPane(
                tab("Usuarios", new UsuariosPane(client, token)),
                tab("Artistas", new ArtistasPane(client, token)),
                tab("Localidades", new LocalidadesPane(client, token)),
                tab("Conciertos", new ConciertosPane(client, token)),
                tab("Aforo", new InventarioPane(client, token)),
                tab("Ventas", new VentaPane(client, token)));
        setCenter(tabs);
    }

    private static Tab tab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }
}
