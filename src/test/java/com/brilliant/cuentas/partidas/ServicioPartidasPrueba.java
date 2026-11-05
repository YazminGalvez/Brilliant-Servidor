package com.brilliant.cuentas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.brilliant.cuentas.RelojPrueba;
import com.brilliant.cuentas.ServicioCuentas;
import com.brilliant.cuentas.usuarios.Usuario;

class ServicioPartidasPrueba {

	private final RelojPrueba reloj = new RelojPrueba();

	private static ServicioCuentas cuentasFalsas() {
		ServicioCuentas cuentas = mock(ServicioCuentas.class);
		when(cuentas.obtenerCuenta(any())).thenAnswer(invocacion -> {
			UUID id = invocacion.getArgument(0);
			String nombre = "jugador-" + id.toString().substring(0, 8);
			return new Usuario(id, nombre, nombre, "hash", Instant.EPOCH, Instant.EPOCH);
		});
		return cuentas;
	}

	private static CodigoSala generadorRepetido(String... codigos) {
		Deque<String> pendientes = new ArrayDeque<>(List.of(codigos));
		return new CodigoSala() {
			@Override
			public String generar() {
				String codigo = pendientes.removeFirst();
				pendientes.addLast(codigo);
				return codigo;
			}
		};
	}

	@Test
	void noRepiteCodigosDeSalasVivas() {
		ServicioPartidas servicio = new ServicioPartidas(cuentasFalsas(), new LimitadorCodigos(reloj, 10),
				generadorRepetido("AAAAAA", "AAAAAA", "BBBBBB", "AAAAAA", "BBBBBB", "CCCCCC"), reloj);

		String primero = servicio.crear(UUID.randomUUID(), 1, 2).codigo();
		String segundo = servicio.crear(UUID.randomUUID(), 1, 2).codigo();
		String tercero = servicio.crear(UUID.randomUUID(), 1, 2).codigo();

		assertThat(List.of(primero, segundo, tercero)).containsExactly("AAAAAA", "BBBBBB", "CCCCCC");
	}

	@Test
	void muchasSalasTienenCodigosDistintos() {
		ServicioPartidas servicio = new ServicioPartidas(cuentasFalsas(), new LimitadorCodigos(reloj, 10),
				new CodigoSala(), reloj);
		Set<String> codigos = new HashSet<>();

		for (int i = 0; i < 500; i++) {
			String codigo = servicio.crear(UUID.randomUUID(), 1 + i % 4, 2 + i % 5).codigo();
			assertThat(codigo).matches("[" + CodigoSala.ALFABETO + "]{6}");
			codigos.add(codigo);
		}

		assertThat(codigos).hasSize(500);
	}

	@Test
	void codigoCerradoSePuedeReutilizar() {
		ServicioPartidas servicio = new ServicioPartidas(cuentasFalsas(), new LimitadorCodigos(reloj, 10),
				generadorRepetido("AAAAAA", "BBBBBB"), reloj);
		UUID anfitrion = UUID.randomUUID();

		assertThat(servicio.crear(anfitrion, 1, 2).codigo()).isEqualTo("AAAAAA");
		assertThat(servicio.crear(anfitrion, 1, 2).codigo()).isEqualTo("BBBBBB");
		assertThat(servicio.crear(UUID.randomUUID(), 1, 2).codigo()).isEqualTo("AAAAAA");
	}
}
