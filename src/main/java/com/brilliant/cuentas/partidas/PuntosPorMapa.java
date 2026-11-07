package com.brilliant.cuentas.partidas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class PuntosPorMapa {

	static final String TABLA_DE_PUNTOS = "1:8,6,4|4,3,2|7,5,3|6,4,2|4,3,2|6,4,2|6,4,2|6,4,2|7,5,3;"
			+ "2:6,4,2|3,2,1|4,3,2|6,4,2|7,5,3|6,4,2|6,4,2|4,3,2|7,5,3|6,4,2;"
			+ "3:7,5,3|7,5,3|8,6,4|6,4,2|6,4,2|6,5,4|7,5,3|7,5,3;"
			+ "4:6,4,3|8,6,4|6,4,2|8,6,4|3,2,1|6,4,2|7,5,3|6,4,3";

	private static final int LUGARES_CON_PUNTOS = 3;
	private static final Map<Integer, List<List<Integer>>> TABLA = leer(TABLA_DE_PUNTOS);

	private PuntosPorMapa() {
	}

	public static boolean existe(int mapa) {
		return TABLA.containsKey(mapa);
	}

	public static int cantidadZonas(int mapa) {
		List<List<Integer>> zonas = TABLA.get(mapa);
		return zonas == null ? 0 : zonas.size();
	}

	public static int puntos(int mapa, int zona, int lugar) {
		if (zona < 1 || zona > cantidadZonas(mapa) || lugar < 1 || lugar > LUGARES_CON_PUNTOS) {
			return 0;
		}
		return TABLA.get(mapa).get(zona - 1).get(lugar - 1);
	}

	static Map<Integer, List<List<Integer>>> leer(String texto) {
		if (texto == null || texto.isBlank()) {
			throw malFormada("la tabla está vacía");
		}
		Map<Integer, List<List<Integer>>> tabla = new TreeMap<>();
		for (String mapa : texto.split(";", -1)) {
			int separador = mapa.indexOf(':');
			if (separador < 0) {
				throw malFormada("falta ':' en '" + mapa + "'");
			}
			int numero = numeroNoNegativo(mapa.substring(0, separador));
			if (numero < 1 || tabla.containsKey(numero)) {
				throw malFormada("número de mapa inválido o repetido: " + numero);
			}
			List<List<Integer>> zonas = new ArrayList<>();
			for (String zona : mapa.substring(separador + 1).split("\\|", -1)) {
				String[] lugares = zona.split(",", -1);
				if (lugares.length != LUGARES_CON_PUNTOS) {
					throw malFormada("la zona '" + zona + "' no tiene " + LUGARES_CON_PUNTOS + " valores");
				}
				List<Integer> puntos = new ArrayList<>();
				for (String lugar : lugares) {
					puntos.add(numeroNoNegativo(lugar));
				}
				zonas.add(List.copyOf(puntos));
			}
			tabla.put(numero, List.copyOf(zonas));
		}
		return Map.copyOf(tabla);
	}

	private static int numeroNoNegativo(String texto) {
		try {
			int numero = Integer.parseInt(texto);
			if (numero < 0) {
				throw malFormada("valor negativo: " + texto);
			}
			return numero;
		} catch (NumberFormatException excepcion) {
			throw malFormada("valor no numérico: '" + texto + "'");
		}
	}

	private static IllegalArgumentException malFormada(String detalle) {
		return new IllegalArgumentException("Tabla de puntos mal formada: " + detalle);
	}
}
