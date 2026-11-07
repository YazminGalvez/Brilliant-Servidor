package com.brilliant.cuentas.partidas;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RespuestaSala(
		String codigo,
		int mapa,
		int capacidad,
		String anfitrion,
		List<Jugador> jugadores,
		boolean comenzada,
		boolean terminada,
		int ronda,
		List<Integer> dados,
		List<Integer> miTablero,
		Map<String, Map<String, Integer>> zonas,
		Map<String, Integer> puntos,
		int meta,
		List<String> ganadores,
		String motivoFin) {

	public record Jugador(
			String nombre,
			boolean listo,
			boolean tableroListo,
			boolean conectado,
			boolean concedio,
			boolean jugoRonda) {
	}

	public static RespuestaSala de(Sala sala, UUID usuarioId, Instant ahora, Duration toleranciaConexion) {
		List<Jugador> jugadores = sala.jugadores().stream()
				.map(jugador -> new Jugador(jugador.nombre(), jugador.listo(), jugador.tableroListo(),
						jugador.conectadoEn(ahora, toleranciaConexion), false, jugador.jugoRonda(sala.ronda())))
				.toList();
		JugadorEnSala propio = sala.jugador(usuarioId);
		List<Integer> miTablero = propio == null ? List.of() : propio.tablero();
		return new RespuestaSala(sala.codigo(), sala.mapa(), sala.capacidad(), sala.nombreAnfitrion(), jugadores,
				sala.comenzada(), sala.terminada(), sala.ronda(), sala.dados(), miTablero, copiaDeZonas(sala),
				Map.of(), 0, List.of(), sala.motivoFin());
	}

	private static Map<String, Map<String, Integer>> copiaDeZonas(Sala sala) {
		Map<String, Map<String, Integer>> copia = new LinkedHashMap<>();
		sala.zonas().forEach((zona, jugadores) -> copia.put(String.valueOf(zona), new LinkedHashMap<>(jugadores)));
		return copia;
	}
}
