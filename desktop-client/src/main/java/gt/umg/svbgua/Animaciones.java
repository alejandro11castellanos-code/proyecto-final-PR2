package gt.umg.svbgua;

import java.util.List;
import java.util.function.DoubleFunction;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;

/** Animaciones reutilizables de la interfaz; todas son cortas (< 0.6 s) para no estorbar el uso. */
final class Animaciones {

    private static final String CLAVE = "svbgua.animacion";

    private Animaciones() {
    }

    /** Aparece desde abajo con fundido; {@code retrasoMs} permite escalonar varios elementos. */
    static void entrada(Node nodo, double retrasoMs) {
        reemplazar(nodo, () -> {
            nodo.setOpacity(0);
            nodo.setTranslateY(16);
            FadeTransition fundido = new FadeTransition(Duration.millis(380), nodo);
            fundido.setToValue(1);
            TranslateTransition subida = new TranslateTransition(Duration.millis(380), nodo);
            subida.setToY(0);
            subida.setInterpolator(Interpolator.EASE_OUT);
            ParallelTransition entrada = new ParallelTransition(fundido, subida);
            entrada.setDelay(Duration.millis(retrasoMs));
            return entrada;
        });
    }

    static void entradaEnCascada(List<? extends Node> nodos) {
        double retraso = 0;
        for (Node nodo : nodos) {
            entrada(nodo, retraso);
            retraso += 70;
        }
    }

    /** Cambio de sección: el contenido entra deslizando desde la derecha. */
    static void cambioDeVista(Node nodo) {
        reemplazar(nodo, () -> {
            nodo.setOpacity(0);
            nodo.setTranslateX(26);
            FadeTransition fundido = new FadeTransition(Duration.millis(300), nodo);
            fundido.setToValue(1);
            TranslateTransition deslizar = new TranslateTransition(Duration.millis(300), nodo);
            deslizar.setToX(0);
            deslizar.setInterpolator(Interpolator.EASE_OUT);
            return new ParallelTransition(fundido, deslizar);
        });
    }

    /** Sacudida horizontal para señalar un error (credenciales incorrectas, etc.). */
    static void sacudir(Node nodo) {
        reemplazar(nodo, () -> {
            DoubleProperty x = new SimpleDoubleProperty(0);
            x.addListener((obs, anterior, actual) -> nodo.setTranslateX(actual.doubleValue()));
            Timeline sacudida = new Timeline();
            double[] pasos = {-10, 9, -7, 5, -3, 0};
            for (int i = 0; i < pasos.length; i++) {
                sacudida.getKeyFrames().add(new KeyFrame(Duration.millis(60 * (i + 1)), new KeyValue(x, pasos[i])));
            }
            return sacudida;
        });
    }

    /** Escala del botón según su estado: 1.04 con el mouse encima, 0.97 al presionarlo. */
    static void escalaDeBoton(ButtonBase boton) {
        double destino = boton.isDisabled() ? 1.0 : boton.isPressed() ? 0.97 : boton.isHover() ? 1.04 : 1.0;
        Object previa = boton.getProperties().get(CLAVE + ".escala");
        if (previa instanceof Animation animacion) {
            animacion.stop();
        }
        ScaleTransition escala = new ScaleTransition(Duration.millis(110), boton);
        escala.setToX(destino);
        escala.setToY(destino);
        boton.getProperties().put(CLAVE + ".escala", escala);
        escala.play();
    }

    /** Cuenta desde 0 hasta {@code objetivo} actualizando el texto con {@code formato}. */
    static void contar(Labeled etiqueta, double objetivo, DoubleFunction<String> formato) {
        reemplazar(etiqueta, () -> {
            DoubleProperty valor = new SimpleDoubleProperty(0);
            valor.addListener((obs, anterior, actual) -> etiqueta.setText(formato.apply(actual.doubleValue())));
            etiqueta.setText(formato.apply(0));
            Timeline conteo = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(valor, 0)),
                    new KeyFrame(Duration.millis(900), new KeyValue(valor, objetivo, Interpolator.EASE_OUT)));
            conteo.setOnFinished(evento -> etiqueta.setText(formato.apply(objetivo)));
            return conteo;
        });
    }

    static void llenar(ProgressBar barra, double objetivo) {
        reemplazar(barra, () -> {
            barra.setProgress(0);
            return new Timeline(
                    new KeyFrame(Duration.millis(120), new KeyValue(barra.progressProperty(), 0)),
                    new KeyFrame(Duration.millis(900), new KeyValue(barra.progressProperty(), objetivo,
                            Interpolator.EASE_OUT)));
        });
    }

    // Una sola animación activa por nodo y tipo: si llega otra, la anterior se detiene.
    private static void reemplazar(Node nodo, java.util.function.Supplier<Animation> fabrica) {
        Object previa = nodo.getProperties().get(CLAVE);
        if (previa instanceof Animation animacion) {
            animacion.stop();
        }
        Animation nueva = fabrica.get();
        nodo.getProperties().put(CLAVE, nueva);
        nueva.play();
    }
}
