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

	@Override
	public String toString() {
		return "Usuario[id=" + id + ", nombre=" + nombre + ", hashContrasena=****, creadoEn=" + creadoEn
				+ ", actualizadoEn=" + actualizadoEn + "]";
	}
}
