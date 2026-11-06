package com.brilliant.cuentas.partidas;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

	public static RespuestaSala de(Sala sala, Instant ahora, Duration toleranciaConexion) {
		List<Jugador> jugadores = sala.jugadores().stream()
				.map(jugador -> new Jugador(jugador.nombre(), jugador.listo(), false,
						jugador.conectadoEn(ahora, toleranciaConexion), false, false))
				.toList();
		return new RespuestaSala(sala.codigo(), sala.mapa(), sala.capacidad(), sala.nombreAnfitrion(), jugadores,
				sala.comenzada(), false, 0, List.of(), List.of(), Map.of(), Map.of(), 0, List.of(), null);
	}
}
