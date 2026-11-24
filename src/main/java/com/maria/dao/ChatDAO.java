package com.maria.dao;

import com.maria.config.ConexionBD;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Ejecuta operaciones JDBC para chats, mensajes, adjuntos y especialidades. */
public final class ChatDAO {

    public static final String CODIGO_MODELO = "gemini-3.5-flash-lite";
    public static final int MAX_BYTES_ARCHIVO = 10 * 1024 * 1024; // 10 MiB

    /** Crea una conversación y devuelve su identificador. */
    public String crearChat(String idUsuario, String primeraPregunta)
            throws SQLException {
        String idUsuarioLimpio = validarRequerido(idUsuario, "idUsuario");
        String idModelo = buscarIdModelo();
        String idChat = nuevoId();
        String titulo = primeraPregunta == null
                ? "Nuevo chat"
                : primeraPregunta.trim();

        if (titulo.isEmpty()) {
            titulo = "Nuevo chat";
        }
        if (titulo.length() > 150) {
            titulo = titulo.substring(0, 150);
        }

        String sql = """
                INSERT INTO HR.CHATS
                    (ID_CHATS, ID_USUARIO, ID_MODELO, TITULO, FECHA, FECHA_MODIFICACION)
                VALUES (?, ?, ?, ?, SYSDATE, SYSDATE)
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idChat);
            ps.setString(2, idUsuarioLimpio);
            ps.setString(3, idModelo);
            ps.setString(4, titulo);
            ps.executeUpdate();
        }

        return idChat;
    }

    /** Persiste un mensaje de texto en una conversación existente. */
    public String guardarMensaje(
            String idChat,
            String emisor,
            String receptor,
            String contenido
    ) throws SQLException {
        String idChatLimpio = validarRequerido(idChat, "idChat");
        String emisorLimpio = validarRequerido(emisor, "emisor");
        String receptorLimpio = validarRequerido(receptor, "receptor");
        String textoSeguro = contenido == null ? "" : contenido;

        String idMensaje = nuevoId();

        String sql = """
                INSERT INTO HR.MENSAJES
                    (ID_MENSAJE, TIPO_MENSAJE, EMISOR, RECEPTOR,
                     ARCHIVO_ADJUNTO, ID_CHATS, CONTENIDO, FECHA)
                VALUES (?, '.txt', ?, ?, 'N', ?, ?, SYSDATE)
                """;

        try (Connection conexion = ConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);

            try {
                try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                    ps.setString(1, idMensaje);
                    ps.setString(2, emisorLimpio);
                    ps.setString(3, receptorLimpio);
                    ps.setString(4, idChatLimpio);
                    ps.setCharacterStream(
                            5,
                            new StringReader(textoSeguro),
                            textoSeguro.length()
                    );
                    ps.executeUpdate();
                }

                actualizarFechaChat(conexion, idChatLimpio);
                conexion.commit();
            } catch (SQLException | RuntimeException error) {
                revertirTransaccion(conexion, error);
                throw error;
            }
        }

        return idMensaje;
    }

    /** Lista las conversaciones de un usuario, de la más reciente a la más antigua. */
    public List<ChatResumen> listarChats(String idUsuario) throws SQLException {
        String idUsuarioLimpio = validarRequerido(idUsuario, "idUsuario");

        String sql = """
                SELECT ID_CHATS,
                       NVL(TITULO, 'Chat sin título') AS TITULO,
                       TO_CHAR(
                           NVL(FECHA_MODIFICACION, FECHA),
                           'DD/MM/YYYY HH24:MI'
                       ) AS FECHA_TEXTO
                FROM HR.CHATS
                WHERE ID_USUARIO = ?
                ORDER BY NVL(FECHA_MODIFICACION, FECHA) DESC
                """;

        List<ChatResumen> chats = new ArrayList<>();

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idUsuarioLimpio);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chats.add(new ChatResumen(
                            rs.getString("ID_CHATS").trim(),
                            rs.getString("TITULO"),
                            rs.getString("FECHA_TEXTO")
                    ));
                }
            }
        }

        return chats;
    }

    /** Recupera los mensajes guardados de una conversación solo si pertenece al usuario. */
    public List<MensajeGuardado> listarMensajes(
            String idChat,
            String idUsuario
    ) throws SQLException {
        String idChatLimpio = validarRequerido(idChat, "idChat");
        String idUsuarioLimpio = validarRequerido(idUsuario, "idUsuario");

        String sql = """
                SELECT m.EMISOR, m.CONTENIDO, m.NOMBRE_ARCHIVO
                FROM HR.MENSAJES m
                JOIN HR.CHATS c ON c.ID_CHATS = m.ID_CHATS
                WHERE c.ID_CHATS = ?
                  AND c.ID_USUARIO = ?
                ORDER BY m.FECHA, m.ROWID
                """;

        List<MensajeGuardado> mensajes = new ArrayList<>();

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idChatLimpio);
            ps.setString(2, idUsuarioLimpio);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String emisor = rs.getString("EMISOR");
                    String contenido = leerClob(rs.getCharacterStream("CONTENIDO"));
                    String nombreArchivo = rs.getString("NOMBRE_ARCHIVO");

                    if (nombreArchivo != null && !nombreArchivo.isBlank()) {
                        contenido += "\n\n📎 " + nombreArchivo.trim();
                    }
                    mensajes.add(new MensajeGuardado(
                            emisor == null ? "" : emisor.trim(),
                            contenido
                    ));
                }
            }
        }

        return mensajes;
    }

    /** Guarda un mensaje junto con su archivo binario en una transacción atómica. */
    public String guardarMensajeConArchivo(
            String idChat,
            String emisor,
            String receptor,
            String texto,
            String nombreArchivo,
            String mimeType,
            String extension,
            byte[] contenidoArchivo
    ) throws SQLException {
        String idChatLimpio = validarRequerido(idChat, "idChat");
        String emisorLimpio = validarRequerido(emisor, "emisor");
        String receptorLimpio = validarRequerido(receptor, "receptor");

        if (contenidoArchivo == null || contenidoArchivo.length == 0) {
            throw new SQLException("El archivo está vacío.");
        }
        if (contenidoArchivo.length > MAX_BYTES_ARCHIVO) {
            throw new SQLException("El archivo supera el límite máximo de 10 MiB.");
        }
        if (nombreArchivo == null || nombreArchivo.isBlank() || nombreArchivo.length() > 100) {
            throw new SQLException(
                    "El nombre del archivo es obligatorio y no puede superar 100 caracteres."
            );
        }

        String textoSeguro = texto == null ? "" : texto;
        String idMensaje = nuevoId();
        String idArchivo = nuevoId();

        String sqlMensaje = """
                INSERT INTO HR.MENSAJES
                    (ID_MENSAJE, TIPO_MENSAJE, EMISOR, RECEPTOR,
                     ARCHIVO_ADJUNTO, TIPO_ARCHIVO, NOMBRE_ARCHIVO,
                     ID_CHATS, CONTENIDO, FECHA)
                VALUES (?, ?, ?, ?, 'S', ?, ?, ?, ?, SYSDATE)
                """;

        String sqlArchivo = """
                INSERT INTO HR.ARCHIVOADJ
                    (ID_AADJ, ID_MENSAJE, TIPO, RUTA, CONTENIDO)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conexion = ConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);

            try {
                try (PreparedStatement ps = conexion.prepareStatement(sqlMensaje)) {
                    ps.setString(1, idMensaje);
                    ps.setString(2, extension);
                    ps.setString(3, emisorLimpio);
                    ps.setString(4, receptorLimpio);
                    ps.setString(5, mimeType);
                    ps.setString(6, nombreArchivo.trim());
                    ps.setString(7, idChatLimpio);

                    if (textoSeguro.isBlank()) {
                        ps.setNull(8, Types.CLOB);
                    } else {
                        ps.setCharacterStream(
                                8,
                                new StringReader(textoSeguro),
                                textoSeguro.length()
                        );
                    }

                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conexion.prepareStatement(sqlArchivo)) {
                    ps.setString(1, idArchivo);
                    ps.setString(2, idMensaje);
                    ps.setString(3, mimeType);
                    ps.setNull(4, Types.VARCHAR);
                    ps.setBinaryStream(
                            5,
                            new ByteArrayInputStream(contenidoArchivo),
                            contenidoArchivo.length
                    );
                    ps.executeUpdate();
                }

                actualizarFechaChat(conexion, idChatLimpio);
                conexion.commit();
            } catch (SQLException | RuntimeException error) {
                revertirTransaccion(conexion, error);
                throw error;
            }
        }
        return idMensaje;
    }

    /** Registra la asociación de un mensaje con un tema de especialidad. */
    public void guardarEspecialidad(
            String idChat,
            String idUsuario,
            String tema,
            String idMensaje
    ) throws SQLException {
        String idChatLimpio = validarRequerido(idChat, "idChat");
        String idUsuarioLimpio = validarRequerido(idUsuario, "idUsuario");
        String temaLimpio = validarRequerido(tema, "tema");
        String idMensajeLimpio = validarRequerido(idMensaje, "idMensaje");

        String sql = """
                INSERT INTO HR.ESPECIALIDAD
                    (ID_ESPECIALIDAD, ID_CHATS, ID_USUARIO,
                     TEMA_CONVERSACION, FECHA, ID_MENSAJE)
                VALUES (?, ?, ?, ?, SYSDATE, ?)
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, nuevoId());
            ps.setString(2, idChatLimpio);
            ps.setString(3, idUsuarioLimpio);
            ps.setString(4, temaLimpio);
            ps.setString(5, idMensajeLimpio);
            ps.executeUpdate();
        }
    }

    /** Obtiene el tema de especialidad más reciente asociado a una conversación. */
    public String obtenerEspecialidad(
            String idChat,
            String idUsuario
    ) throws SQLException {
        String idChatLimpio = validarRequerido(idChat, "idChat");
        String idUsuarioLimpio = validarRequerido(idUsuario, "idUsuario");

        String sql = """
                SELECT TEMA_CONVERSACION
                FROM HR.ESPECIALIDAD
                WHERE ID_CHATS = ?
                  AND ID_USUARIO = ?
                ORDER BY FECHA DESC
                FETCH FIRST 1 ROW ONLY
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idChatLimpio);
            ps.setString(2, idUsuarioLimpio);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? rs.getString("TEMA_CONVERSACION").trim()
                        : null;
            }
        }
    }

    private String buscarIdModelo() throws SQLException {
        String sql = "SELECT ID_MODELO FROM HR.MODELO WHERE CODIGO_API = ? AND ACTIVO = 'S'";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, CODIGO_MODELO);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "No existe en HR.MODELO un registro activo con CODIGO_API = "
                                    + CODIGO_MODELO
                    );
                }

                String idModelo = rs.getString("ID_MODELO").trim();

                if (rs.next()) {
                    throw new SQLException(
                            "Hay más de un registro en HR.MODELO para "
                                    + CODIGO_MODELO
                    );
                }

                return idModelo;
            }
        }
    }

    private static void actualizarFechaChat(Connection conexion, String idChat)
            throws SQLException {
        String sql = "UPDATE HR.CHATS SET FECHA_MODIFICACION = SYSDATE WHERE ID_CHATS = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idChat);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("No se encontró la conversación para actualizar su fecha.");
            }
        }
    }

    private static String leerClob(Reader lector) throws SQLException {
        if (lector == null) {
            return "";
        }

        StringBuilder texto = new StringBuilder();
        char[] bloque = new char[4096];

        try (Reader reader = lector) {
            int cantidad;
            while ((cantidad = reader.read(bloque)) != -1) {
                texto.append(bloque, 0, cantidad);
            }
        } catch (IOException error) {
            throw new SQLException("No se pudo leer el contenido del mensaje.", error);
        }

        return texto.toString();
    }

    private static String validarRequerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El parámetro " + campo + " es obligatorio.");
        }
        return valor.trim();
    }

    private static void revertirTransaccion(Connection conexion, Throwable errorOriginal) {
        try {
            conexion.rollback();
        } catch (SQLException errorRollback) {
            errorOriginal.addSuppressed(errorRollback);
        }
    }

    private static String nuevoId() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();
    }

    public record ChatResumen(String idChat, String titulo, String fecha) {
        @Override
        public String toString() {
            return titulo + "  ·  " + fecha;
        }
    }

    public record MensajeGuardado(String emisor, String contenido) {
    }
}