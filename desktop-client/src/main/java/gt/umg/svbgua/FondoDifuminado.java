package gt.umg.svbgua;

import javafx.animation.FadeTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Rectangle2D;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Portada de fondo difuminada: la imagen se recorta para cubrir todo el panel, se
 * desenfoca y se cubre con un velo claro para que el contenido siga legible. Al
 * cambiar de imagen la anterior se desvanece y la nueva aparece con fundido.
 */
final class FondoDifuminado extends Pane {

    private static final double RADIO_DESENFOQUE = 48;
    // El desenfoque difumina los bordes; se agranda la imagen más allá del panel para que no se noten.
    private static final double MARGEN = RADIO_DESENFOQUE + 12;

    private final ImageView vista = new ImageView();
    private final Region velo = new Region();
    private final Rectangle recorte = new Rectangle();
    private Image actual;
    private SequentialTransition transicion;

    FondoDifuminado() {
        setMouseTransparent(true);
        setManaged(false);

        vista.setEffect(new GaussianBlur(RADIO_DESENFOQUE));
        vista.setPreserveRatio(false);
        vista.setOpacity(0);
        velo.setStyle("-fx-background-color: rgba(243, 245, 251, 0.66);");
        velo.prefWidthProperty().bind(widthProperty());
        velo.prefHeightProperty().bind(heightProperty());
        getChildren().addAll(vista, velo);

        recorte.setArcWidth(32);
        recorte.setArcHeight(32);
        recorte.widthProperty().bind(widthProperty());
        recorte.heightProperty().bind(heightProperty());
        setClip(recorte);

        widthProperty().addListener(obs -> acomodar());
        heightProperty().addListener(obs -> acomodar());
    }

    /** Muestra la imagen (cuando termine de cargar) o, con null, quita el fondo. */
    void mostrar(Image imagen) {
        actual = imagen;
        if (imagen == null) {
            cambiarA(null);
        } else if (imagen.getProgress() >= 1.0) {
            cambiarA(imagen);
        } else {
            imagen.progressProperty().addListener((obs, anterior, progreso) -> {
                if (progreso.doubleValue() >= 1.0 && !imagen.isError()) {
                    // Si el usuario ya eligió otro concierto, esta carga tardía se descarta.
                    if (imagen == actual) {
                        cambiarA(imagen);
                    }
                }
            });
        }
    }

    private void cambiarA(Image imagen) {
        if (transicion != null) {
            transicion.stop();
        }
        FadeTransition salida = new FadeTransition(Duration.millis(imagen == null ? 350 : 160), vista);
        salida.setToValue(0);
        salida.setOnFinished(evento -> {
            vista.setImage(imagen);
            acomodar();
        });
        transicion = new SequentialTransition(salida);
        if (imagen != null) {
            FadeTransition entrada = new FadeTransition(Duration.millis(600), vista);
            entrada.setToValue(1);
            transicion.getChildren().add(entrada);
        }
        transicion.play();
    }

    // Recorta la imagen a la proporción del panel (sin deformarla) y la deja un poco más grande que éste.
    private void acomodar() {
        Image imagen = vista.getImage();
        if (imagen == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        double ancho = getWidth() + 2 * MARGEN;
        double alto = getHeight() + 2 * MARGEN;
        double proporcionPanel = ancho / alto;
        double proporcionImagen = imagen.getWidth() / imagen.getHeight();

        double visibleAncho = proporcionImagen > proporcionPanel ? imagen.getHeight() * proporcionPanel : imagen.getWidth();
        double visibleAlto = proporcionImagen > proporcionPanel ? imagen.getHeight() : imagen.getWidth() / proporcionPanel;
        vista.setViewport(new Rectangle2D((imagen.getWidth() - visibleAncho) / 2,
                (imagen.getHeight() - visibleAlto) / 2, visibleAncho, visibleAlto));
        vista.setFitWidth(ancho);
        vista.setFitHeight(alto);
        vista.setLayoutX(-MARGEN);
        vista.setLayoutY(-MARGEN);
    }
}
