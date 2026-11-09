package com.brilliant.cuentas.partidas;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class JugadorEnSala {

	private final UUID usuarioId;
	private final String nombre;
	private Instant ultimaSenal;
	private Instant ultimaSenalDeJuego;
	private boolean listo;
	private List<Integer> tablero;
	private int rondaJugada;
	private boolean tableroLleno;
	private boolean concedio;

	public JugadorEnSala(UUID usuarioId, String nombre) {
		this.usuarioId = usuarioId;
		this.nombre = nombre;
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

	public List<Integer> tablero() {
		return tablero == null ? List.of() : tablero;
	}

	public boolean tableroListo() {
		return tablero != null;
	}

	public void fijarTablero(List<Integer> numeros) {
		tablero = List.copyOf(numeros);
	}

	public boolean tableroLleno() {
		return tableroLleno;
	}

	public boolean jugoRonda(int ronda) {
		return ronda > 0 && rondaJugada == ronda;
	}

	public boolean concedio() {
		return concedio;
	}

	public void conceder() {
		concedio = true;
	}

	public boolean terminoRonda(int ronda) {
		return concedio || tableroLleno || jugoRonda(ronda);
	}

	public void terminarTurno(int ronda, boolean lleno) {
		rondaJugada = ronda;
		tableroLleno = tableroLleno || lleno;
	}

	void registrarSenal(Instant momento, Instant momentoDeJuego) {
		ultimaSenal = momento;
		ultimaSenalDeJuego = momentoDeJuego;
	}

	public boolean contactoReciente(Instant momento, Duration limite) {
		return momento.isBefore(ultimaSenal.plus(limite));
	}

	Duration ausenciaDeJuego(Instant momentoDeJuego) {
		return Duration.between(ultimaSenalDeJuego, momentoDeJuego);
	}
}
