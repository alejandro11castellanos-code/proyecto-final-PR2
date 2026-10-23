package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.TablaCruzada;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tabla cruzada (pivote): el usuario elige qué va en filas, qué en columnas y
 * qué se mide; la matriz se pinta como mapa de calor con totales. Consume
 * GET /reportes/cruzado con los mismos filtros que el resto del dashboard.
 */
final class TablaCruzadaPane extends VBox {

    private static final String COLOR_CALOR = "78, 121, 167"; // el azul de la paleta del dashboard

    private final CatalogClient client;
    private final String token;
    private final ComboBox<String> filasCombo = comboDeDimensiones();
    private final ComboBox<String> columnasCombo = comboDeDimensiones();
    private final ComboBox<String> medidaCombo = new ComboBox<>();
    private final Label estado = new Label();
    private final GridPane matriz = new GridPane();
    private final Consumer<Pivote> alCargar;

    private FiltrosReporte filtros = FiltrosReporte.NINGUNO;
    private Pivote ultimo;
    private int peticion;
    private boolean ajustando;

    /** @param alCargar se invoca (en el hilo de JavaFX) con cada pivote nuevo, para que el PDF exporte el que se ve. */
    TablaCruzadaPane(CatalogClient client, String token, Consumer<Pivote> alCargar) {
        super(10);
        this.client = client;
        this.token = token;
        this.alCargar = alCargar;

        Label titulo = new Label("Tabla cruzada (pivote)");
        titulo.getStyleClass().add("section-title");

        filasCombo.getSelectionModel().select("concierto");
        columnasCombo.getSelectionModel().select("localidad");
        medidaCombo.getItems().setAll(Pivote.ORDEN_MEDIDAS);
        medidaCombo.setConverter(new Convertidor(Pivote.MEDIDAS));
        medidaCombo.getSelectionModel().select("boletos");

        // Una dimensión no puede estar en filas y columnas a la vez: si se elige la
        // que ya ocupa el otro eje, se intercambian en vez de rechazar la elección.
        filasCombo.valueProperty().addListener((obs, previo, actual) -> evitarRepetida(filasCombo, columnasCombo, previo));
        columnasCombo.valueProperty().addListener((obs, previo, actual) -> evitarRepetida(columnasCombo, filasCombo, previo));
        medidaCombo.valueProperty().addListener((obs, previo, actual) -> recargar());

        FlowPane controles = new FlowPane(8, 8, new Label("Filas:"), filasCombo, new Label("Columnas:"),
                columnasCombo, new Label("Medir:"), medidaCombo);
        controles.setAlignment(Pos.CENTER_LEFT);

        estado.getStyleClass().add("muted");
        Tema.ocultarSiVacia(estado);

        matriz.setHgap(2);
        matriz.setVgap(2);
        ScrollPane desplazable = new ScrollPane(matriz);
        desplazable.setFitToHeight(true);
        desplazable.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(desplazable, Priority.NEVER);

        getChildren().addAll(titulo, controles, estado, desplazable);
    }

    private static ComboBox<String> comboDeDimensiones() {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().setAll(Pivote.ORDEN_DIMENSIONES);
        combo.setConverter(new Convertidor(Pivote.DIMENSIONES));
        combo.setPrefWidth(140);
        return combo;
    }

    private void evitarRepetida(ComboBox<String> cambiado, ComboBox<String> otro, String valorPrevio) {
        if (ajustando) {
            return;
        }
        if (cambiado.getValue() != null && cambiado.getValue().equals(otro.getValue())) {
            ajustando = true;
            otro.setValue(valorPrevio);
            ajustando = false;
        }
        recargar();
    }

    /** Recarga con los filtros del dashboard; lo llama el panel de reportes al aplicar/limpiar/actualizar. */
    void cargar(FiltrosReporte nuevosFiltros) {
        this.filtros = nuevosFiltros;
        recargar();
    }

    Pivote ultimo() {
        return ultimo;
    }

    private void recargar() {
        if (ajustando || filasCombo.getValue() == null || columnasCombo.getValue() == null
                || medidaCombo.getValue() == null) {
            return;
        }
        int miPeticion = ++peticion;
        FiltrosReporte filtrosPedidos = filtros;
        String filas = filasCombo.getValue();
        String columnas = columnasCombo.getValue();
        String medida = medidaCombo.getValue();
        estado.setText("Cargando...");
        Async.run(
                () -> client.obtenerTablaCruzada(token, filtrosPedidos, filas, columnas, medida),
                tabla -> {
                    // Si el usuario cambió un selector mientras cargaba, esta respuesta ya es vieja.
                    if (miPeticion == peticion) {
                        mostrar(tabla);
                    }
                },
                error -> {
                    if (miPeticion == peticion) {
                        estado.setText(error);
                    }
                });
    }

    private void mostrar(TablaCruzada tabla) {
        Pivote pivote = Pivote.de(tabla);
        ultimo = pivote;
        alCargar.accept(pivote);
        estado.setText(pivote.vacia() ? "No hay ventas para los filtros seleccionados." : "");

        matriz.getChildren().clear();
        if (pivote.vacia()) {
            return;
        }

        String esquina = Pivote.DIMENSIONES.get(pivote.dimensionFilas()) + " \\ "
                + Pivote.DIMENSIONES.get(pivote.dimensionColumnas());
        matriz.add(celda(esquina, "-fx-font-weight: bold; -fx-text-fill: #6b6f94;", Pos.CENTER_LEFT, 170), 0, 0);

        int columna = 1;
        for (String clave : pivote.columnas()) {
            matriz.add(celda(Pivote.presentar(pivote.dimensionColumnas(), clave), "-fx-font-weight: bold;",
                    Pos.CENTER, 96), columna++, 0);
        }
        matriz.add(celda("Total", "-fx-font-weight: bold; -fx-background-color: #e6e9f6;", Pos.CENTER, 96),
                columna, 0);

        int fila = 1;
        for (String claveFila : pivote.filas()) {
            matriz.add(celda(Pivote.presentar(pivote.dimensionFilas(), claveFila), "-fx-font-weight: bold;",
                    Pos.CENTER_LEFT, 170), 0, fila);
            columna = 1;
            for (String claveColumna : pivote.columnas()) {
                Double valor = pivote.valor(claveFila, claveColumna);
                matriz.add(valor == null
                        ? celda("—", "-fx-text-fill: #b8bbd3;", Pos.CENTER, 96)
                        : celda(pivote.formatear(valor), estiloCalor(valor, pivote.maximo()), Pos.CENTER, 96),
                        columna++, fila);
            }
            matriz.add(celda(pivote.formatear(pivote.totalFila(claveFila)),
                    "-fx-font-weight: bold; -fx-background-color: #e6e9f6;", Pos.CENTER, 96), columna, fila);
            fila++;
        }

        matriz.add(celda("Total", "-fx-font-weight: bold; -fx-background-color: #e6e9f6;", Pos.CENTER_LEFT, 170),
                0, fila);
        columna = 1;
        for (String claveColumna : pivote.columnas()) {
            matriz.add(celda(pivote.formatear(pivote.totalColumna(claveColumna)),
                    "-fx-font-weight: bold; -fx-background-color: #e6e9f6;", Pos.CENTER, 96), columna++, fila);
        }
        matriz.add(celda(pivote.formatear(pivote.granTotal()),
                "-fx-font-weight: bold; -fx-background-color: #cfd4ee;", Pos.CENTER, 96), columna, fila);
        Animaciones.entrada(matriz, 0);
    }

    /** Más ventas, más intenso: de 10 % a 65 % de opacidad del azul según el valor respecto del máximo. */
    private static String estiloCalor(double valor, double maximo) {
        double intensidad = maximo <= 0 ? 0 : valor / maximo;
        return String.format(java.util.Locale.ROOT, "-fx-background-color: rgba(%s, %.2f);", COLOR_CALOR,
                0.10 + 0.55 * intensidad);
    }

    private static Label celda(String texto, String estilo, Pos alineacion, double ancho) {
        Label etiqueta = new Label(texto);
        etiqueta.setMinWidth(ancho);
        etiqueta.setPrefWidth(ancho);
        etiqueta.setMinHeight(30);
        etiqueta.setAlignment(alineacion);
        etiqueta.setPadding(new Insets(0, 8, 0, 8));
        etiqueta.setStyle("-fx-background-radius: 6; " + estilo);
        return etiqueta;
    }

    /** Muestra el nombre legible de cada clave del selector. */
    private static final class Convertidor extends javafx.util.StringConverter<String> {
        private final java.util.Map<String, String> nombres;

        Convertidor(java.util.Map<String, String> nombres) {
            this.nombres = nombres;
        }

        @Override
        public String toString(String clave) {
            return clave == null ? "" : nombres.getOrDefault(clave, clave);
        }

        @Override
        public String fromString(String texto) {
            return nombres.entrySet().stream().filter(e -> e.getValue().equals(texto)).map(java.util.Map.Entry::getKey)
                    .findFirst().orElse(texto);
        }
    }
}
