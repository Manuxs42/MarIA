package com.maria;

import com.maria.controller.ChatController;
import com.maria.controller.LoginController;
import com.maria.model.UsuarioSesion;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;

/**
 * Punto de entrada de MarIA y encargado de alternar entre las ventanas
 * de inicio de sesión y el chat principal.
 */
public class Main extends Application {

    /** Carga y muestra la pantalla inicial de inicio de sesión. */
    @Override
    public void start(Stage stage) throws IOException {
        stage.initStyle(StageStyle.DECORATED);

        URL fxmlLogin = Main.class.getResource("/login.fxml");
        if (fxmlLogin == null) {
            throw new IllegalStateException("No se encontró el recurso /login.fxml.");
        }

        FXMLLoader loader = new FXMLLoader(fxmlLogin);
        Parent login = loader.load();

        LoginController controller = loader.getController();
        controller.setAlIniciarSesion(usuario -> abrirChat(stage, usuario));

        stage.setTitle("MarIA · Iniciar sesión");
        stage.setScene(new Scene(login));
        stage.setResizable(false);
        stage.show();
    }

    private void abrirChat(Stage ventanaLogin, UsuarioSesion usuario) {
        try {
            URL fxmlChat = Main.class.getResource("/interfaz.fxml");
            if (fxmlChat == null) {
                throw new IllegalStateException("No se encontró el recurso /interfaz.fxml.");
            }

            FXMLLoader loader = new FXMLLoader(fxmlChat);
            Parent chat = loader.load();

            ChatController controller = loader.getController();
            controller.setUsuarioSesion(usuario);

            // Ventana principal sin bordes con barra de título personalizada.
            Stage ventanaChat = new Stage(StageStyle.UNDECORATED);
            ventanaChat.setTitle("MarIA · " + usuario.username());
            ventanaChat.setResizable(true);
            ventanaChat.setMinWidth(940);
            ventanaChat.setMinHeight(680);
            ventanaChat.setScene(new Scene(chat, 1120, 720));
            ventanaChat.show();
            ventanaChat.centerOnScreen();

            ventanaLogin.close();
        } catch (IOException | RuntimeException error) {
            System.err.println("Error al abrir la ventana principal: " + error.getMessage());

            new Alert(
                    Alert.AlertType.ERROR,
                    "No se pudo abrir la ventana de MarIA:\n" + error.getMessage()
            ).showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}