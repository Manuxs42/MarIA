package com.maria.dao;

import com.maria.config.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/** Lee y actualiza las preferencias persistentes de cada usuario. */
public final class ConfiguracionDAO {

    private static final Set<String> IDIOMAS_PERMITIDOS = Set.of("Español", "English", "Français");
    private static final Set<String> TONOS_PERMITIDOS = Set.of("Formal", "Informal");
    private static final Set<String> TEMAS_PERMITIDOS = Set.of("Claro", "Oscuro");

    /** Recupera idioma, tono y tema asociados a una configuración. */
    public Preferencias obtener(String idConfig) throws SQLException {
        String idLimpio = validarId(idConfig);

        String sql = """
                SELECT IDIOMA, TONO_IA, TEMA
                FROM HR.CONFIGURACION
                WHERE ID_CONFIG = ?
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idLimpio);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No se encontró la configuración del usuario.");
                }

                return new Preferencias(
                        rs.getString("IDIOMA").trim(),
                        rs.getString("TONO_IA").trim(),
                        rs.getString("TEMA").trim()
                );
            }
        }
    }

    /** Guarda las preferencias seleccionadas por el usuario. */
    public void actualizar(
            String idConfig,
            String idioma,
            String tonoIA,
            String tema
    ) throws SQLException {
        String idLimpio = validarId(idConfig);
        validarOpciones(idioma, tonoIA, tema);

        String sql = """
                UPDATE HR.CONFIGURACION
                SET IDIOMA = ?, TONO_IA = ?, TEMA = ?
                WHERE ID_CONFIG = ?
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idioma);
            ps.setString(2, tonoIA);
            ps.setString(3, tema);
            ps.setString(4, idLimpio);

            if (ps.executeUpdate() != 1) {
                throw new SQLException("No se pudo actualizar la configuración del usuario.");
            }
        }
    }

    private static String validarId(String idConfig) {
        if (idConfig == null || idConfig.isBlank()) {
            throw new IllegalArgumentException("El identificador de configuración es obligatorio.");
        }
        return idConfig.trim();
    }

    private static void validarOpciones(String idioma, String tonoIA, String tema) {
        if (idioma == null || !IDIOMAS_PERMITIDOS.contains(idioma)) {
            throw new IllegalArgumentException("Idioma no permitido.");
        }
        if (tonoIA == null || !TONOS_PERMITIDOS.contains(tonoIA)) {
            throw new IllegalArgumentException("Tono de IA no permitido.");
        }
        if (tema == null || !TEMAS_PERMITIDOS.contains(tema)) {
            throw new IllegalArgumentException("Tema no permitido.");
        }
    }

    public record Preferencias(
            String idioma,
            String tonoIA,
            String tema
    ) {
    }
}