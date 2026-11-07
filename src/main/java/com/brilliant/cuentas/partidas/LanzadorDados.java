package com.brilliant.cuentas.partidas;

import java.security.SecureRandom;
import java.util.Random;

import org.springframework.stereotype.Component;

@Component
public class LanzadorDados {

	private final Random azar;

	public LanzadorDados() {
		this(new SecureRandom());
	}

	public LanzadorDados(Random azar) {
		this.azar = azar;
	}

	public int lanzar() {
		return azar.nextInt(1, 7);
	}
}
