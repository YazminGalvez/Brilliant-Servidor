package com.brilliant.cuentas.partidas;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Puntuacion {

	public static final int META_DOS_JUGADORES = 26;
	public static final int META_TRES_JUGADORES = 24;
	public static final int META_CUATRO_JUGADORES = 22;
	// La hoja oficial llega hasta 5 jugadores; con 6 se usa la misma meta.
	public static final int META_CINCO_O_MAS_JUGADORES = 20;

	private Puntuacion() {
	}

	public static int meta(int jugadoresAlComenzar) {
		return switch (jugadoresAlComenzar) {
			case 0, 1, 2 -> META_DOS_JUGADORES;
			case 3 -> META_TRES_JUGADORES;
			case 4 -> META_CUATRO_JUGADORES;
			default -> META_CINCO_O_MAS_JUGADORES;
		};
	}

	public static List<String> ganadores(List<String> jugadores, Map<String, Integer> puntos) {
		int maximo = jugadores.stream().mapToInt(jugador -> puntos.getOrDefault(jugador, 0)).max().orElse(0);
		return jugadores.stream().filter(jugador -> puntos.getOrDefault(jugador, 0) == maximo).toList();
	}

	public static int lugar(int ronda, Collection<Integer> rondasDeLaZona) {
		return 1 + (int) rondasDeLaZona.stream().filter(otra -> otra < ronda).distinct().count();
	}

	public static Map<String, Integer> calcular(int mapa, Map<Integer, Map<String, Integer>> zonas) {
		Map<String, Integer> totales = new LinkedHashMap<>();
		zonas.forEach((zona, completados) -> completados.forEach((jugador, ronda) -> {
			int puntos = PuntosPorMapa.puntos(mapa, zona, lugar(ronda, completados.values()));
			totales.merge(jugador, puntos, Integer::sum);
		}));
		return totales;
	}
}
