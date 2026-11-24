package com.maria.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Proporciona conexiones JDBC a Oracle usando variables de entorno. */
public final class ConexionBD {

    private ConexionBD() {
    }

    /** Abre una conexión JDBC con las credenciales configuradas en el entorno. */
    public static Connection obtenerConexion() throws SQLException {
        String url = variableRequerida("DB_URL").trim();
        String usuario = variableRequerida("DB_USER").trim();
        String contrasena = variableRequerida("DB_PASSWORD");

        if (!url.startsWith("jdbc:oracle:")) {
            throw new IllegalStateException(
                    "La variable DB_URL debe iniciar con 'jdbc:oracle:' (ejemplo: jdbc:oracle:thin:@//localhost:1521/XEPDB1)."
            );
        }

        return DriverManager.getConnection(url, usuario, contrasena);
    }

    private static String variableRequerida(String nombre) {
        String valor = System.getenv(nombre);

        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar la variable de entorno " + nombre
            );
        }

        return valor;
    }
}