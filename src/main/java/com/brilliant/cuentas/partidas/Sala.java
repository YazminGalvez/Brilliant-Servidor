package com.brilliant.cuentas.partidas;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class Sala {

	// Si nadie de la sala hace peticiones durante más de este tiempo (se cayó la red de todos o el servidor se
	// pausó), el reloj de juego se detiene: ese silencio no debe marcar a nadie como desconectado ni abandonado.
	private static final Duration SILENCIO_MAXIMO = Duration.ofSeconds(5);

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
	private Instant terminadaEn;
	private String motivoFin;
	private List<String> ganadores = List.of();
	private Instant ultimaActividad;
	private Duration silencio = Duration.ZERO;

	public Sala(String codigo, int mapa, int capacidad, JugadorEnSala anfitrion, Instant ahora) {
		this.codigo = codigo;
		this.mapa = mapa;
		this.capacidad = capacidad;
		this.anfitrionId = anfitrion.usuarioId();
		this.nombreAnfitrion = anfitrion.nombre();
		this.ultimaActividad = ahora;
		agregar(anfitrion, ahora);
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

	public List<JugadorEnSala> activos() {
		return jugadores.values().stream().filter(jugador -> !jugador.concedio()).toList();
	}

	public void registrarContacto(JugadorEnSala jugador, Instant ahora) {
		Instant tope = ultimaActividad.plus(SILENCIO_MAXIMO);
		if (ahora.isAfter(tope)) {
			silencio = silencio.plus(Duration.between(tope, ahora));
		}
		if (ahora.isAfter(ultimaActividad)) {
			ultimaActividad = ahora;
		}
		jugador.registrarSenal(ahora, momentoDeJuego(ahora));
	}

	public boolean conectado(JugadorEnSala jugador, Instant ahora, Duration desconexion) {
		return jugador.ausenciaDeJuego(momentoDeJuego(ahora)).compareTo(desconexion) < 0;
	}

	public boolean abandono(JugadorEnSala jugador, Instant ahora, Duration abandono) {
		return jugador.ausenciaDeJuego(momentoDeJuego(ahora)).compareTo(abandono) >= 0;
	}

	private Instant momentoDeJuego(Instant ahora) {
		Instant tope = ultimaActividad.plus(SILENCIO_MAXIMO);
		return (ahora.isAfter(tope) ? tope : ahora).minus(silencio);
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

	public boolean hayActivoConectado(Instant ahora, Duration desconexion) {
		return activos().stream().anyMatch(jugador -> conectado(jugador, ahora, desconexion));
	}

	public boolean todosTienenTablero(Instant ahora, Duration desconexion) {
		return activos().stream()
				.allMatch(jugador -> jugador.tableroListo() || !conectado(jugador, ahora, desconexion));
	}

	public void comenzarRonda(int primerDado, int segundoDado) {
		ronda++;
		dados = List.of(primerDado, segundoDado);
	}

	public boolean terminada() {
		return terminada;
	}

	public boolean terminadaHace(Instant ahora, Duration tiempo) {
		return terminada && !ahora.isBefore(terminadaEn.plus(tiempo));
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

	public void terminar(String motivo, List<String> ganadores, Instant ahora) {
		terminada = true;
		terminadaEn = ahora;
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

	public boolean todosTerminaronLaRonda(Instant ahora, Duration desconexion) {
		return ronda > 0 && jugadores.values().stream()
				.allMatch(jugador -> jugador.terminoRonda(ronda) || !conectado(jugador, ahora, desconexion));
	}

	public boolean todosLlenaronSuTablero() {
		return activos().stream().allMatch(JugadorEnSala::tableroLleno);
	}

	public void marcarListo(UUID usuarioId, boolean listo) {
		jugadores.get(usuarioId).marcarListo(listo);
		comenzarSiTodosListos();
	}

	public boolean estaLlena() {
		return jugadores.size() >= capacidad;
	}

	public void agregar(JugadorEnSala jugador, Instant ahora) {
		jugadores.put(jugador.usuarioId(), jugador);
		registrarContacto(jugador, ahora);
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
