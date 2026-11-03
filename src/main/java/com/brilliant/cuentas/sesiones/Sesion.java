package com.brilliant.cuentas.sesiones;

import java.time.Instant;
import java.util.UUID;

public record Sesion(
		UUID id,
		UUID usuarioId,
		Instant creadoEn,
		Instant expiraEn,
		Instant revocadoEn) {

	public boolean esValidaEn(Instant momento) {
		return revocadoEn == null && momento.isBefore(expiraEn);
	}
}
