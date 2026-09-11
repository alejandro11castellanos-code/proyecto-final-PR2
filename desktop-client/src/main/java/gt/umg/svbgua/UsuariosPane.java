package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Usuario;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Administración de usuarios (login del sistema): alta, edición de nombre y
 * rol, restablecer contraseña y baja. Exclusivo de administrador — el
 * backend rechaza a cualquier otro rol.
 */
public final class UsuariosPane extends BorderPane {

    private static final List<String> ROLES = List.of("administrador", "vendedor");

    private final CatalogClient client;
    private final String token;
    private final ObservableList<Usuario> items = FXCollections.observableArrayList();
    private final TableView<Usuario> table = new TableView<>(items);

    private final TextField nombreUsuarioField = new TextField();
    private final PasswordField contrasenaField = new PasswordField();
    private final TextField nombreCompletoField = new TextField();
    private final ComboBox<String> rolCombo = new ComboBox<>(FXCollections.observableArrayList(ROLES));
    private final Label status = new Label();
    private final Button guardarButton = new Button("Guardar");
    private final Button eliminarButton = new Button("Eliminar");
    private final Button restablecerButton = new Button("Restablecer contraseña");
    private Usuario seleccionado;

    public UsuariosPane(CatalogClient client, String token) {
        this.client = client;
        this.token = token;
        setPadding(new Insets(16));
        setCenter(buildTable());
        setBottom(buildForm());
        cargar();
    }

    private TableView<Usuario> buildTable() {
        TableColumn<Usuario, String> nombreUsuario = new TableColumn<>("Usuario");
        nombreUsuario.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nombreUsuario()));

        TableColumn<Usuario, String> nombreCompleto = new TableColumn<>("Nombre completo");
        nombreCompleto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nombreCompleto()));

        TableColumn<Usuario, String> rol = new TableColumn<>("Rol");
        rol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().rol()));

        table.getColumns().addAll(List.of(nombreUsuario, nombreCompleto, rol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().selectedItemProperty().addListener((obs, previous, current) -> seleccionar(current));
        return table;
    }

    private VBox buildForm() {
        nombreUsuarioField.setPromptText("Usuario (login)");
        contrasenaField.setPromptText("Contraseña (mínimo 6 caracteres)");
        nombreCompletoField.setPromptText("Nombre completo");
        rolCombo.setPromptText("Rol");

        Button nuevoButton = new Button("Nuevo");
        nuevoButton.setOnAction(event -> limpiarSeleccion());
        guardarButton.setOnAction(event -> guardar());
        eliminarButton.setOnAction(event -> eliminar());
        eliminarButton.setDisable(true);
        restablecerButton.setOnAction(event -> restablecerContrasena());
        restablecerButton.setDisable(true);

        HBox botones = new HBox(8, nuevoButton, guardarButton, eliminarButton, restablecerButton);
        botones.setAlignment(Pos.CENTER_LEFT);
        status.setWrapText(true);

        VBox form = new VBox(8, nombreUsuarioField, contrasenaField, nombreCompletoField, rolCombo, botones, status);
        form.setPadding(new Insets(12, 0, 0, 0));
        return form;
    }

    private void seleccionar(Usuario usuario) {
        seleccionado = usuario;
        eliminarButton.setDisable(usuario == null);
        restablecerButton.setDisable(usuario == null);
        // El nombre de usuario es el identificador de login: no se edita, solo
        // se define al crear. La contraseña de un usuario existente se cambia
        // aparte, con "Restablecer contraseña".
        nombreUsuarioField.setDisable(usuario != null);
        contrasenaField.setDisable(usuario != null);
        if (usuario == null) {
            return;
        }
        nombreUsuarioField.setText(usuario.nombreUsuario());
        contrasenaField.clear();
        nombreCompletoField.setText(usuario.nombreCompleto());
        rolCombo.setValue(usuario.rol());
    }

    private void limpiarSeleccion() {
        table.getSelectionModel().clearSelection();
        seleccionado = null;
        nombreUsuarioField.setDisable(false);
        contrasenaField.setDisable(false);
        nombreUsuarioField.clear();
        contrasenaField.clear();
        nombreCompletoField.clear();
        rolCombo.setValue(null);
        eliminarButton.setDisable(true);
        restablecerButton.setDisable(true);
        status.setText("");
    }

    private void cargar() {
        Async.run(
                () -> client.listUsuarios(token),
                lista -> {
                    items.setAll(lista);
                    status.setText("");
                },
                status::setText);
    }

    private void guardar() {
        String nombreCompleto = nombreCompletoField.getText();
        String rol = rolCombo.getValue();

        if (nombreCompleto == null || nombreCompleto.isBlank() || rol == null) {
            status.setText("Nombre completo y rol son obligatorios.");
            return;
        }

        guardarButton.setDisable(true);
        Runnable onDone = () -> {
            guardarButton.setDisable(false);
            limpiarSeleccion();
            cargar();
        };
        Runnable onError = () -> guardarButton.setDisable(false);

        if (seleccionado == null) {
            String nombreUsuario = nombreUsuarioField.getText();
            String contrasena = contrasenaField.getText();
            if (nombreUsuario == null || nombreUsuario.isBlank()
                    || contrasena == null || contrasena.length() < 6) {
                guardarButton.setDisable(false);
                status.setText("Usuario obligatorio y contraseña de al menos 6 caracteres.");
                return;
            }
            Async.run(
                    () -> client.createUsuario(token, nombreUsuario.trim(), contrasena, nombreCompleto.trim(), rol),
                    ignored -> onDone.run(),
                    error -> {
                        onError.run();
                        status.setText(error);
                    });
        } else {
            Async.run(
                    () -> client.updateUsuario(token, seleccionado.idUsuario(), nombreCompleto.trim(), rol),
                    ignored -> onDone.run(),
                    error -> {
                        onError.run();
                        status.setText(error);
                    });
        }
    }

    private void restablecerContrasena() {
        if (seleccionado == null) {
            return;
        }
        TextInputDialog dialogo = new TextInputDialog();
        dialogo.setTitle("Restablecer contraseña");
        dialogo.setHeaderText("Nueva contraseña para " + seleccionado.nombreUsuario());
        dialogo.setContentText("Contraseña (mínimo 6 caracteres):");

        Optional<String> resultado = dialogo.showAndWait();
        if (resultado.isEmpty()) {
            return;
        }
        String nuevaContrasena = resultado.get();
        if (nuevaContrasena.length() < 6) {
            status.setText("La contraseña debe tener al menos 6 caracteres.");
            return;
        }

        int id = seleccionado.idUsuario();
        restablecerButton.setDisable(true);
        Async.run(
                () -> client.restablecerContrasena(token, id, nuevaContrasena),
                () -> {
                    restablecerButton.setDisable(false);
                    status.setText("Contraseña actualizada para " + seleccionado.nombreUsuario() + ".");
                },
                error -> {
                    restablecerButton.setDisable(false);
                    status.setText(error);
                });
    }

    private void eliminar() {
        if (seleccionado == null) {
            return;
        }
        int id = seleccionado.idUsuario();
        eliminarButton.setDisable(true);
        Async.run(
                () -> client.deleteUsuario(token, id),
                () -> {
                    limpiarSeleccion();
                    cargar();
                },
                error -> {
                    eliminarButton.setDisable(false);
                    status.setText(error);
                });
    }
}
