package com.maria.model;

/** Datos de identidad inmutables de una cuenta con sesión iniciada. */
public record UsuarioSesion(
        String idUsuario,
        String username,
        String idConfig,
        String idTipos
) {
    public UsuarioSesion {
        idUsuario = limpiarRequerido(idUsuario, "idUsuario");
        username = limpiarRequerido(username, "username");
        idConfig = limpiarRequerido(idConfig, "idConfig");
        idTipos = limpiarRequerido(idTipos, "idTipos");
    }

    private static String limpiarRequerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "El campo de sesión '" + campo + "' no puede ser nulo ni estar vacío."
            );
        }
        return valor.trim();
    }
}