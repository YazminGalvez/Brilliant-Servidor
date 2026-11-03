package com.brilliant.cuentas.seguridad;

import java.util.UUID;

public record UsuarioAutenticado(UUID usuarioId, UUID sesionId) {
}
