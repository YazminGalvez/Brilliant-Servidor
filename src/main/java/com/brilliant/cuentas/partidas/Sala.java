package com.brilliant.cuentas.partidas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class Sala {

	private final String codigo;
	private final int mapa;
	private final int capacidad;
	private final UUID anfitrionId;
	private final String nombreAnfitrion;
	private final Map<UUID, JugadorEnSala> jugadores = new LinkedHashMap<>();
	private boolean comenzada;
	private int ronda;
	private List<Integer> dados = List.of();
	private final Map<Integer, Map<String, Integer>> zonas = new TreeMap<>();
	private int jugadoresAlComenzar;
	private boolean terminada;
	private String motivoFin;
	private List<String> ganadores = List.of();

	public Sala(String codigo, int mapa, int capacidad, JugadorEnSala anfitrion) {
		this.codigo = codigo;
		this.mapa = mapa;
		this.capacidad = capacidad;
		this.anfitrionId = anfitrion.usuarioId();
		this.nombreAnfitrion = anfitrion.nombre();
		agregar(anfitrion);
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

	public String nombreAnfitrion() {
		return nombreAnfitrion;
	}

	public JugadorEnSala jugador(UUID usuarioId) {
		return jugadores.get(usuarioId);
	}

	public List<JugadorEnSala> jugadores() {
		return new ArrayList<>(jugadores.values());
	}

	public boolean comenzada() {
		return comenzada;
	}

	public int ronda() {
		return ronda;
	}

	public List<Integer> dados() {
		return dados;
	}

	public boolean todosTienenTablero() {
		return jugadores.values().stream().allMatch(JugadorEnSala::tableroListo);
	}

	public void comenzarRonda(int primerDado, int segundoDado) {
		ronda++;
		dados = List.of(primerDado, segundoDado);
	}

	public boolean terminada() {
		return terminada;
	}

	public String motivoFin() {
		return motivoFin;
	}

	public int meta() {
		return comenzada ? Puntuacion.meta(jugadoresAlComenzar) : 0;
	}

	public List<String> ganadores() {
		return ganadores;
	}

	public void terminar(String motivo, List<String> ganadores) {
		terminada = true;
		motivoFin = motivo;
		this.ganadores = List.copyOf(ganadores);
	}

	public Map<Integer, Map<String, Integer>> zonas() {
		return zonas;
	}

	public void registrarTurno(UUID usuarioId, int rondaAvisada, boolean tableroLleno, Integer zona) {
		JugadorEnSala jugador = jugadores.get(usuarioId);
		if (!comenzada || terminada || ronda == 0 || rondaAvisada != ronda || jugador.terminoRonda(ronda)) {
			return;
		}
		jugador.terminarTurno(ronda, tableroLleno);
		if (zona != null) {
			zonas.computeIfAbsent(zona, clave -> new LinkedHashMap<>()).putIfAbsent(jugador.nombre(), ronda);
		}
	}

	public boolean todosTerminaronLaRonda() {
		return ronda > 0 && jugadores.values().stream().allMatch(jugador -> jugador.terminoRonda(ronda));
	}

	public boolean todosLlenaronSuTablero() {
		return jugadores.values().stream().allMatch(JugadorEnSala::tableroLleno);
	}

	public void marcarListo(UUID usuarioId, boolean listo) {
		jugadores.get(usuarioId).marcarListo(listo);
		comenzarSiTodosListos();
	}

	public boolean estaLlena() {
		return jugadores.size() >= capacidad;
	}

	public void agregar(JugadorEnSala jugador) {
		jugadores.put(jugador.usuarioId(), jugador);
	}

	public void quitar(UUID usuarioId) {
		jugadores.remove(usuarioId);
		comenzarSiTodosListos();
	}

	private void comenzarSiTodosListos() {
		if (!comenzada && jugadores.size() >= 2 && jugadores.values().stream().allMatch(JugadorEnSala::listo)) {
			comenzada = true;
			jugadoresAlComenzar = jugadores.size();
		}
	}
}
