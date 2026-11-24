package com.maria.service;

import com.maria.dao.UsuarioDAO;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Herramientas de perfil invocables por el asistente durante una conversación. */
public final class UsuarioHerramientas {

    private static volatile String idUsuarioActual;
    private static final AtomicReference<CambioPerfil> CAMBIO_PENDIENTE =
            new AtomicReference<>();

    private UsuarioHerramientas() {
    }

    /** Define el usuario autenticado para la sesión actual y limpia propuestas previas. */
    public static void establecerUsuarioActual(String idUsuario) {
        idUsuarioActual = (idUsuario == null || idUsuario.isBlank())
                ? null
                : idUsuario.trim();
        limpiarCambioPendiente();
    }

    /** Descarta cualquier propuesta pendiente de cambio de perfil. */
    public static void limpiarCambioPendiente() {
        CAMBIO_PENDIENTE.set(null);
    }

    /**
     * Consulta el perfil no sensible del usuario que inició sesión.
     */
    public static String consultarMiPerfil() {
        String idUsuario = idUsuarioActual;
        if (idUsuario == null || idUsuario.isBlank()) {
            return "No hay un usuario activo.";
        }

        try {
            UsuarioDAO.PerfilUsuario perfil =
                    new UsuarioDAO().consultarPerfil(idUsuario);

            if (perfil == null) {
                return "No se encontró el perfil del usuario activo.";
            }

            return "Perfil actual: usuario "
                    + perfil.username()
                    + ", nombre "
                    + perfil.nombre()
                    + ", apellidos "
                    + perfil.apellidos()
                    + ", correo "
                    + perfil.correo()
                    + ".";
        } catch (SQLException error) {
            throw new IllegalStateException(
                    "No se pudo consultar el perfil.",
                    error
            );
        }
    }

    /**
     * Prepara un cambio de un solo campo. No guarda nada en la base de datos.
     *
     * @param campo Campo: username, nombre, apellidos o correo.
     * @param nuevoValor Valor que el usuario pidió guardar.
     */
    public static String prepararCambioPerfil(
            String campo,
            String nuevoValor
    ) {
        String idUsuario = idUsuarioActual;

        if (idUsuario == null || idUsuario.isBlank()) {
            return "No hay un usuario activo.";
        }
        if (campo == null || nuevoValor == null || nuevoValor.isBlank()) {
            return "Indica el campo y el nuevo valor.";
        }

        try {
            UsuarioDAO dao = new UsuarioDAO();
            UsuarioDAO.PerfilUsuario actual = dao.consultarPerfil(idUsuario);

            if (actual == null) {
                return "No se encontró el perfil del usuario activo.";
            }

            String campoNormalizado = campo.trim().toLowerCase(Locale.ROOT);
            String valor = nuevoValor.trim();

            UsuarioDAO.PerfilUsuario propuesto;
            String valorAnterior;

            switch (campoNormalizado) {
                case "username", "usuario", "nombre de usuario" -> {
                    if (valor.length() > 50) {
                        return "El nombre de usuario no puede superar 50 caracteres.";
                    }
                    campoNormalizado = "username";
                    valorAnterior = actual.username();
                    propuesto = new UsuarioDAO.PerfilUsuario(
                            valor,
                            actual.nombre(),
                            actual.apellidos(),
                            actual.correo()
                    );
                }
                case "nombre" -> {
                    if (valor.length() > 100) {
                        return "El nombre no puede superar 100 caracteres.";
                    }
                    valorAnterior = actual.nombre();
                    propuesto = new UsuarioDAO.PerfilUsuario(
                            actual.username(),
                            valor,
                            actual.apellidos(),
                            actual.correo()
                    );
                }
                case "apellidos" -> {
                    if (valor.length() > 100) {
                        return "Los apellidos no pueden superar 100 caracteres.";
                    }
                    valorAnterior = actual.apellidos();
                    propuesto = new UsuarioDAO.PerfilUsuario(
                            actual.username(),
                            actual.nombre(),
                            valor,
                            actual.correo()
                    );
                }
                case "correo", "email" -> {
                    valor = valor.toLowerCase(Locale.ROOT);
                    if (!esCorreoValido(valor)) {
                        return "El formato del correo electrónico no es válido o supera 150 caracteres.";
                    }
                    campoNormalizado = "correo";
                    valorAnterior = actual.correo();
                    propuesto = new UsuarioDAO.PerfilUsuario(
                            actual.username(),
                            actual.nombre(),
                            actual.apellidos(),
                            valor
                    );
                }
                default -> {
                    return "Campo no permitido. Usa username, nombre, apellidos o correo.";
                }
            }

            String anteriorLimpio = valorAnterior == null ? "" : valorAnterior.trim();
            if (anteriorLimpio.equals(valor)) {
                return "El campo " + campoNormalizado + " ya tiene ese mismo valor.";
            }

            CAMBIO_PENDIENTE.set(new CambioPerfil(
                    campoNormalizado,
                    anteriorLimpio,
                    valor,
                    propuesto
            ));

            return "Preparé el cambio de "
                    + campoNormalizado
                    + ". La aplicación pedirá confirmación antes de guardarlo.";

        } catch (SQLException error) {
            throw new IllegalStateException(
                    "No se pudo preparar el cambio de perfil.",
                    error
            );
        }
    }

    /** Entrega y limpia atómicamente la propuesta pendiente de cambio de perfil. */
    public static CambioPerfil tomarCambioPendiente() {
        return CAMBIO_PENDIENTE.getAndSet(null);
    }

    private static boolean esCorreoValido(String correo) {
        if (correo.length() > 150) {
            return false;
        }
        int arroba = correo.indexOf('@');
        int punto = correo.lastIndexOf('.');
        return arroba > 0 && punto > arroba + 1 && punto < correo.length() - 1;
    }

    public record CambioPerfil(
            String campo,
            String valorAnterior,
            String valorNuevo,
            UsuarioDAO.PerfilUsuario perfilNuevo
    ) {
        public CambioPerfil {
            Objects.requireNonNull(campo, "El campo es obligatorio.");
            Objects.requireNonNull(valorAnterior, "El valor anterior es obligatorio.");
            Objects.requireNonNull(valorNuevo, "El valor nuevo es obligatorio.");
            Objects.requireNonNull(perfilNuevo, "El perfil propuesto es obligatorio.");
        }
    }
}