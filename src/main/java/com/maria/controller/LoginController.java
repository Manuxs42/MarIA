package com.maria.controller;

import com.maria.dao.UsuarioDAO;
import com.maria.model.UsuarioSesion;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.function.Consumer;

/** Gestiona la validación de credenciales y el acceso a la aplicación. */
public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label mensajeLabel;

    private Consumer<UsuarioSesion> alIniciarSesion;

    public void setAlIniciarSesion(Consumer<UsuarioSesion> accion) {
        this.alIniciarSesion = accion;
    }

    @FXML
    private void iniciarSesion() {
        String username = usernameField.getText();
        char[] contrasena = passwordField.getText().toCharArray();

        try {
            if (username == null || username.isBlank() || contrasena.length == 0) {
                mensajeLabel.setText("Ingresa tu nombre de usuario y contraseña.");
                return;
            }

            UsuarioSesion usuario = new UsuarioDAO().autenticar(
                    username,
                    contrasena
            );

            if (usuario == null) {
                mensajeLabel.setText("Usuario o contraseña incorrectos.");
                passwordField.clear();
                passwordField.requestFocus();
                return;
            }

            mensajeLabel.setText("");
            if (alIniciarSesion != null) {
                alIniciarSesion.accept(usuario);
            }
        } catch (IllegalStateException e) {
            mensajeLabel.setText(e.getMessage());
        } catch (SQLException e) {
            System.err.println("Error de conexión al autenticar: " + e.getMessage());
            mensajeLabel.setText("No se pudo conectar con la base de datos.");
        } finally {
            Arrays.fill(contrasena, '\0');
        }
    }

    @FXML
    private void abrirRegistro() {
        try {
            URL fxml = getClass().getResource("/registro.fxml");
            if (fxml == null) {
                mensajeLabel.setText("No se encontró el recurso registro.fxml.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxml);
            Scene escena = new Scene(loader.load());

            Stage ventana = new Stage();
            ventana.setTitle("MarIA · Crear cuenta");
            ventana.initOwner(usernameField.getScene().getWindow());
            ventana.initModality(Modality.APPLICATION_MODAL);
            ventana.setResizable(false);
            ventana.setScene(escena);

            RegistroController controller = loader.getController();
            controller.setAlRegistrar(usuarioCreado -> {
                usernameField.setText(usuarioCreado);
                passwordField.clear();
                mensajeLabel.setText("Cuenta creada correctamente. Ingresa tu contraseña.");
                ventana.close();
                passwordField.requestFocus();
            });

            ventana.showAndWait();
        } catch (IOException | RuntimeException e) {
            mensajeLabel.setText("No se pudo abrir la ventana de registro.");
        }
    }
}