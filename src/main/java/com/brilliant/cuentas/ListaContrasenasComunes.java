package com.brilliant.cuentas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class ListaContrasenasComunes {

	private final Set<String> contrasenas;

	public ListaContrasenasComunes(@Value("classpath:contrasenas-comunes.txt") Resource archivo)
			throws IOException {
		try (BufferedReader lector = new BufferedReader(
				new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
			contrasenas = lector.lines()
					.map(String::strip)
					.filter(linea -> !linea.isEmpty())
					.map(ListaContrasenasComunes::aMinusculas)
					.collect(Collectors.toUnmodifiableSet());
		}
	}

	public boolean contiene(String contrasena) {
		return contrasenas.contains(aMinusculas(contrasena));
	}

	private static String aMinusculas(String texto) {
		return texto.toLowerCase(Locale.ROOT);
	}
}
