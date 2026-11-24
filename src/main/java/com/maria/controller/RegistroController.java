package com.maria.controller;

import com.maria.dao.UsuarioDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.function.Consumer;

/** Controlador de la pantalla de creación de cuentas de usuario. */
public class RegistroController {

    private static final String MEMBRESIA_POR_DEFECTO = "BAS1";

    @FXML
    private TextField usernameField;

    @FXML
    private TextField nombreField;

    @FXML
    private TextField apellidosField;

    @FXML
    private TextField correoField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label mensajeLabel;

    private Consumer<String> alRegistrar;

    public void setAlRegistrar(Consumer<String> accion) {
        this.alRegistrar = accion;
    }

    @FXML
    private void registrar() {
        String usernameIngresado = usernameField.getText();
        char[] contrasena = passwordField.getText().toCharArray();

        try {
            UsuarioDAO usuarioDAO = new UsuarioDAO();
            usuarioDAO.registrar(
                    usernameIngresado,
                    nombreField.getText(),
                    apellidosField.getText(),
                    correoField.getText(),
                    contrasena,
                    MEMBRESIA_POR_DEFECTO
            );

            String usuarioLimpio = usernameIngresado == null ? "" : usernameIngresado.trim();
            usernameField.clear();
            nombreField.clear();
            apellidosField.clear();
            correoField.clear();
            passwordField.clear();

            if (alRegistrar != null) {
                alRegistrar.accept(usuarioLimpio);
            } else {
                mensajeLabel.setText("Usuario registrado correctamente.");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            mensajeLabel.setText(e.getMessage());
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) {
                mensajeLabel.setText(
                        "Ese nombre de usuario o correo ya está registrado."
                );
            } else {
                mensajeLabel.setText(
                        "Error Oracle " + e.getErrorCode() + ": " + e.getMessage()
                );
            }
        } finally {
            Arrays.fill(contrasena, '\0');
        }
    }
}