package com.brilliant.cuentas.partidas;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class CodigoSala {

	public static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	public static final int LONGITUD = 6;

	// "ACTUAL" chocaría con la ruta /partidas/actual, porque el código no distingue mayúsculas.
	private static final Set<String> RESERVADOS = Set.of("ACTUAL");

	private final SecureRandom aleatorio = new SecureRandom();

	public String generar() {
		String codigo;
		do {
			StringBuilder letras = new StringBuilder(LONGITUD);
			for (int i = 0; i < LONGITUD; i++) {
				letras.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
			}
			codigo = letras.toString();
		} while (RESERVADOS.contains(codigo));
		return codigo;
	}

	public static String normalizar(String codigo) {
		return codigo.strip().toUpperCase(Locale.ROOT);
	}
}
