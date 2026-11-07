package com.brilliant.cuentas.partidas;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Puntuacion {

	private Puntuacion() {
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
