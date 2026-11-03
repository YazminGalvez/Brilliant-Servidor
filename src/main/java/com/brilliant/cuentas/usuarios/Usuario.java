package com.brilliant.cuentas.usuarios;

import java.time.Instant;
import java.util.UUID;

public record Usuario(
		UUID id,
		String nombre,
		String nombreNormalizado,
		String hashContrasena,
		Instant creadoEn,
		Instant actualizadoEn) {
}
