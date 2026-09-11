package gt.umg.svbgua;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/** Envuelve un único {@link MediaPlayer} para reproducir un adelanto corto a la vez. */
public final class PreviewPlayer {

    private MediaPlayer actual;

    public void reproducir(String url, Runnable alTerminar) {
        detener();
        actual = new MediaPlayer(new Media(url));
        actual.setOnEndOfMedia(() -> {
            detener();
            alTerminar.run();
        });
        actual.setOnError(() -> {
            detener();
            alTerminar.run();
        });
        actual.play();
    }

    public void detener() {
        if (actual != null) {
            actual.stop();
            actual.dispose();
            actual = null;
        }
    }

    public boolean estaReproduciendo() {
        return actual != null;
    }
}
