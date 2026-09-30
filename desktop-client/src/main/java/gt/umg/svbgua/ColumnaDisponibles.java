package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Inventario;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

/**
 * Columna "Disponibles" compartida entre Aforo y el punto de venta: resalta
 * cuando queda poco cupo o se agotó, en vez de mostrar un número pelado que
 * pasa desapercibido. El umbral es una cantidad fija (no un %) porque
 * "últimos boletos" tiene que sentirse igual en una sala de 10 personas que
 * en un estadio de 10,000.
 */
final class ColumnaDisponibles {

    private static final int UMBRAL_ULTIMOS = 10;

    private ColumnaDisponibles() {
    }

    static TableColumn<Inventario, Inventario> crear() {
        TableColumn<Inventario, Inventario> columna = new TableColumn<>("Disponibles");
        columna.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        columna.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Inventario item, boolean vacio) {
                super.updateItem(item, vacio);
                if (vacio || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                int disponible = item.cantidadDisponible();
                if (disponible <= 0) {
                    setText("AGOTADO");
                    setStyle("-fx-text-fill: #b00020; -fx-font-weight: bold;");
                } else if (disponible <= UMBRAL_ULTIMOS) {
                    setText(disponible == 1 ? "¡Último 1!" : "¡Últimos " + disponible + "!");
                    setStyle("-fx-text-fill: #e67e00; -fx-font-weight: bold;");
                } else {
                    setText(String.valueOf(disponible));
                    setStyle("");
                }
            }
        });
        return columna;
    }
}
