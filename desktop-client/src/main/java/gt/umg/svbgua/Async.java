package gt.umg.svbgua;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;

/**
 * Ejecuta una tarea bloqueante (típicamente una llamada HTTP) en un hilo aparte
 * y entrega el resultado o el error de vuelta en el hilo de JavaFX.
 */
final class Async {

    private Async() {
    }

    static <T> void run(Supplier<T> task, Consumer<T> onSuccess, Consumer<String> onError) {
        CompletableFuture.supplyAsync(task).whenComplete((result, error) -> Platform.runLater(() -> {
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                onError.accept(cause.getMessage());
                return;
            }
            onSuccess.accept(result);
        }));
    }

    static void run(Runnable task, Runnable onSuccess, Consumer<String> onError) {
        run(() -> {
            task.run();
            return null;
        }, ignored -> onSuccess.run(), onError);
    }
}
