package com.brilliant.cuentas.partidas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodigoSalaPrueba {

	@Test
	void generaSeisCaracteresSinLetrasConfusas() {
		CodigoSala generador = new CodigoSala();

		for (int i = 0; i < 2000; i++) {
			assertThat(generador.generar())
					.hasSize(6)
					.matches("[" + CodigoSala.ALFABETO + "]{6}")
					.doesNotContain("0", "O", "1", "I")
					.isNotEqualTo("ACTUAL");
		}
	}

	@Test
	void normalizaMayusculasYEspacios() {
		assertThat(CodigoSala.normalizar("  k7m2qx\t")).isEqualTo("K7M2QX");
		assertThat(CodigoSala.normalizar("K7M2QX")).isEqualTo("K7M2QX");
	}
}
