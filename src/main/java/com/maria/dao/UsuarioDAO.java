package com.maria.dao;

import com.maria.config.ConexionBD;
import com.maria.model.UsuarioSesion;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/** Gestiona registro, autenticación, perfil y eliminación de usuarios. */
public final class UsuarioDAO {

    private static final int ITERACIONES = 210_000;
    private static final int ORACLE_UNIQUE_VIOLATION = 1;

    /** Registra una cuenta, incluyendo la configuración inicial del usuario. */
    public void registrar(
            String username,
            String nombre,
            String apellidos,
            String correo,
            char[] contrasena,
            String idTipos
    ) throws SQLException {
        try {
            String usernameLimpio = validarTexto(username, "username", 50);
            String nombreLimpio = validarTexto(nombre, "nombre", 100);
            String apellidosLimpios = validarTexto(apellidos, "apellidos", 100);
            String correoLimpio = validarCorreo(correo);
            String idTiposLimpio = validarTexto(idTipos, "membresía", 4);

            if (contrasena == null || contrasena.length < 8) {
                throw new IllegalArgumentException(
                        "La contraseña debe tener al menos 8 caracteres."
                );
            }

            String idUsuario = nuevoId();
            String idConfig = nuevoId();
            String hash = crearHash(contrasena);

            try (Connection conexion = ConexionBD.obtenerConexion()) {
                conexion.setAutoCommit(false);

                try {
                    String sqlConfig = """
                            INSERT INTO HR.CONFIGURACION
                                (ID_CONFIG, IDIOMA, TONO_IA, TEMA)
                            VALUES (?, 'Español', 'Formal', 'Claro')
                            """;

                    try (PreparedStatement ps = conexion.prepareStatement(sqlConfig)) {
                        ps.setString(1, idConfig);
                        ps.executeUpdate();
                    }

                    String sqlUsuario = """
                            INSERT INTO HR.USUARIOS
                                (ID_USUARIO, ID_CONFIG, ID_TIPOS, USERNAME,
                                 NOMBRE, APELLIDOS, CORREO, CONTRASENA)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """;

                    try (PreparedStatement ps = conexion.prepareStatement(sqlUsuario)) {
                        ps.setString(1, idUsuario);
                        ps.setString(2, idConfig);
                        ps.setString(3, idTiposLimpio);
                        ps.setString(4, usernameLimpio);
                        ps.setString(5, nombreLimpio);
                        ps.setString(6, apellidosLimpios);
                        ps.setString(7, correoLimpio);
                        ps.setString(8, hash);
                        ps.executeUpdate();
                    }

                    conexion.commit();
                } catch (SQLException error) {
                    revertirTransaccion(conexion, error);
                    if (error.getErrorCode() == ORACLE_UNIQUE_VIOLATION) {
                        throw new IllegalArgumentException(
                                "El nombre de usuario o el correo electrónico ya están registrados."
                        );
                    }
                    throw error;
                } catch (RuntimeException error) {
                    revertirTransaccion(conexion, error);
                    throw error;
                }
            }
        } finally {
            if (contrasena != null) {
                Arrays.fill(contrasena, '\0');
            }
        }
    }

    /** Obtiene los datos personales del perfil identificado por su ID. */
    public PerfilUsuario consultarPerfil(String idUsuario) throws SQLException {
        String idLimpio = validarTexto(idUsuario, "idUsuario", 32);

        String sql = """
                SELECT USERNAME, NOMBRE, APELLIDOS, CORREO
                FROM HR.USUARIOS
                WHERE ID_USUARIO = ?
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idLimpio);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String correo = rs.getString("CORREO");
                return new PerfilUsuario(
                        rs.getString("USERNAME").trim(),
                        rs.getString("NOMBRE").trim(),
                        rs.getString("APELLIDOS").trim(),
                        correo == null ? "" : correo.trim()
                );
            }
        }
    }

    /** Actualiza nombre de usuario, nombre, apellidos y correo del usuario. */
    public void actualizarPerfil(
            String idUsuario,
            String username,
            String nombre,
            String apellidos,
            String correo
    ) throws SQLException {
        String idLimpio = validarTexto(idUsuario, "idUsuario", 32);
        String usernameLimpio = validarTexto(username, "username", 50);
        String nombreLimpio = validarTexto(nombre, "nombre", 100);
        String apellidosLimpios = validarTexto(apellidos, "apellidos", 100);
        String correoLimpio = validarCorreo(correo);

        String sql = """
                UPDATE HR.USUARIOS
                SET USERNAME = ?, NOMBRE = ?, APELLIDOS = ?, CORREO = ?
                WHERE ID_USUARIO = ?
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, usernameLimpio);
            ps.setString(2, nombreLimpio);
            ps.setString(3, apellidosLimpios);
            ps.setString(4, correoLimpio);
            ps.setString(5, idLimpio);

            if (ps.executeUpdate() != 1) {
                throw new SQLException("No se encontró el usuario para actualizar.");
            }
        } catch (SQLException error) {
            if (error.getErrorCode() == ORACLE_UNIQUE_VIOLATION) {
                throw new IllegalArgumentException(
                        "El nombre de usuario o el correo electrónico ya pertenece a otra cuenta."
                );
            }
            throw error;
        }
    }

    /** Valida las credenciales y devuelve la sesión o null. */
    public UsuarioSesion autenticar(String username, char[] contrasena)
            throws SQLException {
        try {
            if (username == null || username.isBlank()
                    || contrasena == null || contrasena.length == 0) {
                return null;
            }

            String sql = """
                    SELECT ID_USUARIO, USERNAME, ID_CONFIG, ID_TIPOS, CONTRASENA
                    FROM HR.USUARIOS
                    WHERE USERNAME = ?
                    """;

            try (Connection conexion = ConexionBD.obtenerConexion();
                 PreparedStatement ps = conexion.prepareStatement(sql)) {

                ps.setString(1, username.trim());

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }

                    String hashGuardado = rs.getString("CONTRASENA");
                    if (hashGuardado == null || !verificarHash(contrasena, hashGuardado)) {
                        return null;
                    }

                    return new UsuarioSesion(
                            rs.getString("ID_USUARIO").trim(),
                            rs.getString("USERNAME").trim(),
                            rs.getString("ID_CONFIG").trim(),
                            rs.getString("ID_TIPOS").trim()
                    );
                }
            }
        } finally {
            if (contrasena != null) {
                Arrays.fill(contrasena, '\0');
            }
        }
    }

    /** Elimina la cuenta identificada por el ID de usuario. */
    public void eliminarCuenta(String idUsuario) throws SQLException {
        String idLimpio = validarTexto(idUsuario, "idUsuario", 32);
        String idConfig;

        try (Connection conexion = ConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);

            try {
                String sqlConfig = """
                        SELECT ID_CONFIG
                        FROM HR.USUARIOS
                        WHERE ID_USUARIO = ?
                        """;

                try (PreparedStatement ps = conexion.prepareStatement(sqlConfig)) {
                    ps.setString(1, idLimpio);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("No se encontró la cuenta del usuario.");
                        }
                        idConfig = rs.getString("ID_CONFIG").trim();
                    }
                }

                try (PreparedStatement ps = conexion.prepareStatement(
                        "DELETE FROM HR.USUARIOS WHERE ID_USUARIO = ?")) {
                    ps.setString(1, idLimpio);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("No se pudo eliminar la cuenta.");
                    }
                }

                int usuariosConConfig;
                try (PreparedStatement ps = conexion.prepareStatement(
                        "SELECT COUNT(*) FROM HR.USUARIOS WHERE ID_CONFIG = ?")) {
                    ps.setString(1, idConfig);

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        usuariosConConfig = rs.getInt(1);
                    }
                }

                if (usuariosConConfig == 0) {
                    try (PreparedStatement ps = conexion.prepareStatement(
                            "DELETE FROM HR.CONFIGURACION WHERE ID_CONFIG = ?")) {
                        ps.setString(1, idConfig);
                        ps.executeUpdate();
                    }
                }

                conexion.commit();
            } catch (SQLException | RuntimeException error) {
                revertirTransaccion(conexion, error);
                throw error;
            }
        }
    }

    private static String crearHash(char[] contrasena) throws SQLException {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(contrasena, salt, ITERACIONES, 256);

        try {
            byte[] hash = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

            return "pbkdf2-sha256$" + ITERACIONES + "$"
                    + encoder.encodeToString(salt) + "$"
                    + encoder.encodeToString(hash);
        } catch (GeneralSecurityException error) {
            throw new SQLException("No se pudo proteger la contraseña.", error);
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean verificarHash(char[] contrasena, String hashGuardado)
            throws SQLException {
        try {
            String[] partes = hashGuardado.split("\\$");

            if (partes.length != 4 || !partes[0].equals("pbkdf2-sha256")) {
                return false;
            }

            int iteraciones = Integer.parseInt(partes[1]);
            if (iteraciones < 10_000 || iteraciones > 1_000_000) {
                return false;
            }

            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] salt = decoder.decode(partes[2]);
            byte[] hashEsperado = decoder.decode(partes[3]);

            PBEKeySpec spec = new PBEKeySpec(
                    contrasena, salt, iteraciones, hashEsperado.length * 8
            );

            try {
                byte[] hashCalculado = SecretKeyFactory
                        .getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec)
                        .getEncoded();

                return MessageDigest.isEqual(hashEsperado, hashCalculado);
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new SQLException("No se pudo verificar la contraseña.", error);
        }
    }

    private static String validarTexto(String valor, String campo, int maxLongitud) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio.");
        }
        String limpio = valor.trim();
        if (limpio.length() > maxLongitud) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " no puede superar " + maxLongitud + " caracteres."
            );
        }
        return limpio;
    }

    private static String validarCorreo(String correo) {
        String limpio = validarTexto(correo, "correo", 150).toLowerCase(Locale.ROOT);
        int arroba = limpio.indexOf('@');
        int punto = limpio.lastIndexOf('.');
        if (arroba <= 0 || punto <= arroba + 1 || punto >= limpio.length() - 1) {
            throw new IllegalArgumentException("El formato del correo electrónico no es válido.");
        }
        return limpio;
    }

    private static void revertirTransaccion(Connection conexion, Throwable errorOriginal) {
        try {
            conexion.rollback();
        } catch (SQLException errorRollback) {
            errorOriginal.addSuppressed(errorRollback);
        }
    }

    private static String nuevoId() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .toUpperCase();
    }

    public record PerfilUsuario(
            String username,
            String nombre,
            String apellidos,
            String correo
    ) {
    }
}