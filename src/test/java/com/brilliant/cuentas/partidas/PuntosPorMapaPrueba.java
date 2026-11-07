package com.brilliant.cuentas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PuntosPorMapaPrueba {

	@Test
	void laTablaOficialTieneLosCuatroMapas() {
		assertThat(PuntosPorMapa.leer(PuntosPorMapa.TABLA_DE_PUNTOS)).containsOnlyKeys(1, 2, 3, 4);
		assertThat(PuntosPorMapa.cantidadZonas(1)).isEqualTo(9);
		assertThat(PuntosPorMapa.cantidadZonas(2)).isEqualTo(10);
		assertThat(PuntosPorMapa.cantidadZonas(3)).isEqualTo(8);
		assertThat(PuntosPorMapa.cantidadZonas(4)).isEqualTo(8);
		assertThat(PuntosPorMapa.existe(0)).isFalse();
		assertThat(PuntosPorMapa.existe(5)).isFalse();
	}

	@Test
	void daLosPuntosDeCadaLugar() {
		assertThat(PuntosPorMapa.puntos(1, 1, 1)).isEqualTo(8);
		assertThat(PuntosPorMapa.puntos(1, 1, 2)).isEqualTo(6);
		assertThat(PuntosPorMapa.puntos(1, 1, 3)).isEqualTo(4);
		assertThat(PuntosPorMapa.puntos(2, 2, 1)).isEqualTo(3);
		assertThat(PuntosPorMapa.puntos(3, 6, 2)).isEqualTo(5);
		assertThat(PuntosPorMapa.puntos(4, 8, 3)).isEqualTo(3);
	}

	@Test
	void delCuartoLugarEnAdelanteOFueraDeLaTablaDaCero() {
		assertThat(PuntosPorMapa.puntos(1, 1, 4)).isZero();
		assertThat(PuntosPorMapa.puntos(1, 1, 9)).isZero();
		assertThat(PuntosPorMapa.puntos(1, 10, 1)).isZero();
		assertThat(PuntosPorMapa.puntos(5, 1, 1)).isZero();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "1", "1:", "1:8,6", "1:8,6,4,2", "1:8,6,4|", "x:8,6,4", "1:8,6,a", "1:8,,4",
			"1:8,6,-1", "0:8,6,4", "1:8,6,4;1:7,5,3", "1:8,6,4;", "1:8;6,4"})
	void rechazaUnaTablaMalFormada(String tabla) {
		assertThatThrownBy(() -> PuntosPorMapa.leer(tabla))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageStartingWith("Tabla de puntos mal formada");
	}

	@Test
	void aceptaUnaTablaBienFormada() {
		assertThat(PuntosPorMapa.leer("2:9,8,7|3,2,1;7:5,5,5"))
				.containsOnlyKeys(2, 7)
				.hasEntrySatisfying(2, zonas -> assertThat(zonas).hasSize(2));
	}
}
