package com.brilliant.cuentas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PuntuacionPrueba {

	private static Map<String, Integer> zona(Object... jugadorYRonda) {
		Map<String, Integer> completados = new LinkedHashMap<>();
		for (int i = 0; i < jugadorYRonda.length; i += 2) {
			completados.put((String) jugadorYRonda[i], (Integer) jugadorYRonda[i + 1]);
		}
		return completados;
	}

	@Test
	void unEmpateEnLaMismaRondaNoGastaLugares() {
		assertThat(Puntuacion.lugar(1, List.of(1, 1, 2))).isEqualTo(1);
		assertThat(Puntuacion.lugar(2, List.of(1, 1, 2))).isEqualTo(2);

		Map<String, Integer> puntos = Puntuacion.calcular(1, Map.of(1, zona("ana", 1, "beto", 1, "ciro", 2)));

		assertThat(puntos).containsOnly(entry("ana", 8), entry("beto", 8), entry("ciro", 6));
	}

	@Test
	void rondasDistintasDanLugaresDistintos() {
		Map<String, Integer> puntos = Puntuacion.calcular(1, Map.of(1, zona("ana", 2, "beto", 5, "ciro", 9)));

		assertThat(puntos).containsOnly(entry("ana", 8), entry("beto", 6), entry("ciro", 4));
	}

	@Test
	void delCuartoLugarEnAdelanteSonCeroPuntos() {
		Map<String, Integer> puntos = Puntuacion.calcular(1,
				Map.of(1, zona("ana", 1, "beto", 1, "ciro", 2, "dora", 3, "eli", 4, "fede", 6)));

		assertThat(puntos).containsOnly(entry("ana", 8), entry("beto", 8), entry("ciro", 6), entry("dora", 4),
				entry("eli", 0), entry("fede", 0));
	}

	@Test
	void sumaLosTotalesDeVariosJugadoresEnVariasZonas() {
		Map<Integer, Map<String, Integer>> zonas = new LinkedHashMap<>();
		zonas.put(1, zona("ana", 1, "beto", 2));
		zonas.put(3, zona("beto", 2, "ana", 3, "ciro", 3));
		zonas.put(9, zona("ciro", 4));

		Map<String, Integer> puntos = Puntuacion.calcular(1, zonas);

		assertThat(puntos).containsOnly(entry("ana", 8 + 5), entry("beto", 6 + 7), entry("ciro", 5 + 7));
	}

	@Test
	void sinZonasNoHayPuntos() {
		assertThat(Puntuacion.calcular(2, Map.of())).isEmpty();
	}
}
