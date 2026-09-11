package gt.umg.svbgua;

import gt.umg.svbgua.PreviewClient.Preview;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/**
 * Panel reutilizable: imagen + artista + canción + botón para reproducir un
 * adelanto corto. Se usa tanto en el catálogo de conciertos como en el punto
 * de venta.
 */
public final class PreviewPanel extends VBox {

    private final PreviewClient previewClient = PreviewClient.crear();
    private final PreviewPlayer previewPlayer = new PreviewPlayer();
    private final ImageView imagen = new ImageView();
    private final Label nombreArtista = new Label();
    private final Label tituloCancion = new Label();
    private final Button boton = new Button("▶ Escuchar");
    private final Label status = new Label();

    private String artistaSolicitado;
    private String previewUrlActual;

    public PreviewPanel() {
        setAlignment(Pos.CENTER);
        setPadding(new Insets(8));
        setSpacing(6);
        setMaxWidth(180);

        imagen.setFitWidth(96);
        imagen.setFitHeight(96);
        imagen.setPreserveRatio(true);

        nombreArtista.setStyle("-fx-font-weight: bold;");
        nombreArtista.setWrapText(true);
        tituloCancion.setWrapText(true);
        tituloCancion.setStyle("-fx-font-size: 11px; -fx-opacity: 0.85;");
        status.setWrapText(true);
        status.setStyle("-fx-font-size: 11px;");

        boton.setDisable(true);
        boton.setOnAction(event -> alternar());

        getChildren().addAll(imagen, nombreArtista, tituloCancion, boton, status);
    }

    /** Limpia el panel; se usa cuando no hay nada seleccionado. */
    public void limpiar() {
        detener();
        artistaSolicitado = null;
        previewUrlActual = null;
        imagen.setImage(null);
        nombreArtista.setText("");
        tituloCancion.setText("");
        status.setText("");
        boton.setDisable(true);
    }

    /** Busca y muestra el adelanto de un artista, reemplazando lo anterior. */
    public void cargarParaArtista(String nombreDeArtista) {
        detener();
        artistaSolicitado = nombreDeArtista;
        previewUrlActual = null;
        imagen.setImage(null);
        nombreArtista.setText(nombreDeArtista);
        tituloCancion.setText("");
        boton.setDisable(true);
        status.setText("Buscando un adelanto...");

        Async.run(
                () -> previewClient.buscarPreview(nombreDeArtista),
                preview -> mostrar(nombreDeArtista, preview),
                error -> {
                    if (nombreDeArtista.equals(artistaSolicitado)) {
                        status.setText(error);
                    }
                });
    }

    private void mostrar(String artistaConsultado, Preview preview) {
        // El usuario pudo haber cambiado de selección mientras la búsqueda seguía en vuelo.
        if (!artistaConsultado.equals(artistaSolicitado)) {
            return;
        }
        if (preview == null) {
            status.setText("No se encontró un adelanto para " + artistaConsultado + ".");
            return;
        }
        status.setText("");
        tituloCancion.setText(preview.tituloCancion() == null ? "" : preview.tituloCancion());
        if (preview.imagenUrl() != null) {
            imagen.setImage(new Image(preview.imagenUrl(), true));
        }
        previewUrlActual = preview.previewUrl();
        boton.setDisable(false);
    }

    private void alternar() {
        if (previewPlayer.estaReproduciendo()) {
            detener();
            return;
        }
        if (previewUrlActual == null) {
            return;
        }
        boton.setText("■ Detener");
        previewPlayer.reproducir(previewUrlActual, () -> boton.setText("▶ Escuchar"));
    }

    public void detener() {
        previewPlayer.detener();
        boton.setText("▶ Escuchar");
    }
}
