package com.brilliant.cuentas.partidas;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class JugadorEnSala {

	private final UUID usuarioId;
	private final String nombre;
	private Instant ultimaSenal;
	private boolean listo;

	public JugadorEnSala(UUID usuarioId, String nombre, Instant ultimaSenal) {
		this.usuarioId = usuarioId;
		this.nombre = nombre;
		this.ultimaSenal = ultimaSenal;
	}

	public UUID usuarioId() {
		return usuarioId;
	}

	public String nombre() {
		return nombre;
	}

	public boolean listo() {
		return listo;
	}

	public void marcarListo(boolean listo) {
		this.listo = listo;
	}

	public void registrarSenal(Instant momento) {
		ultimaSenal = momento;
	}

	public boolean conectadoEn(Instant momento, Duration tolerancia) {
		return momento.isBefore(ultimaSenal.plus(tolerancia));
	}
}
