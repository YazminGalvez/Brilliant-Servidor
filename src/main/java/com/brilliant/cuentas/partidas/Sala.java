package com.brilliant.cuentas.partidas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Sala {

	private final String codigo;
	private final int mapa;
	private final int capacidad;
	private final UUID anfitrionId;
	private final Map<UUID, JugadorEnSala> jugadores = new LinkedHashMap<>();

	public Sala(String codigo, int mapa, int capacidad, UUID anfitrionId) {
		this.codigo = codigo;
		this.mapa = mapa;
		this.capacidad = capacidad;
		this.anfitrionId = anfitrionId;
	}

	public String codigo() {
		return codigo;
	}

	public int mapa() {
		return mapa;
	}

	public int capacidad() {
		return capacidad;
	}

	public boolean esAnfitrion(UUID usuarioId) {
		return anfitrionId.equals(usuarioId);
	}

	public JugadorEnSala anfitrion() {
		return jugadores.get(anfitrionId);
	}

	public JugadorEnSala jugador(UUID usuarioId) {
		return jugadores.get(usuarioId);
	}

	public List<JugadorEnSala> jugadores() {
		return new ArrayList<>(jugadores.values());
	}

	public boolean estaLlena() {
		return jugadores.size() >= capacidad;
	}

	public void agregar(JugadorEnSala jugador) {
		jugadores.put(jugador.usuarioId(), jugador);
	}

	public void quitar(UUID usuarioId) {
		jugadores.remove(usuarioId);
	}
}
