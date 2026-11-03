package com.brilliant.cuentas.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.brilliant.cuentas.RelojPrueba;

class LimitadorIntentosPrueba {

	private final RelojPrueba reloj = new RelojPrueba();
	private final LimitadorIntentos limitador = new LimitadorIntentos(reloj, 5, 300);

	@Test
	void bloqueaAlLlegarAlMaximoDuranteElTiempoIndicado() {
		for (int i = 0; i < 4; i++) {
			limitador.registrarFallo("ana", "10.0.0.1");
		}
		assertThat(limitador.tiempoBloqueado("ana", "10.0.0.9")).isEmpty();

		limitador.registrarFallo("ana", "10.0.0.1");
		assertThat(limitador.tiempoBloqueado("ANA", "10.0.0.9")).contains(Duration.ofSeconds(300));
		assertThat(limitador.tiempoBloqueado("otra", "10.0.0.1")).contains(Duration.ofSeconds(300));
		assertThat(limitador.tiempoBloqueado("otra", "10.0.0.9")).isEmpty();

		reloj.avanzar(Duration.ofSeconds(300));
		assertThat(limitador.tiempoBloqueado("ana", "10.0.0.1")).isEmpty();
	}

	@Test
	void fallosFueraDeLaVentanaNoSeAcumulan() {
		for (int i = 0; i < 4; i++) {
			limitador.registrarFallo("bruno", "10.0.0.2");
		}
		reloj.avanzar(Duration.ofSeconds(300));
		for (int i = 0; i < 4; i++) {
			limitador.registrarFallo("bruno", "10.0.0.2");
		}

		assertThat(limitador.tiempoBloqueado("bruno", "10.0.0.2")).isEmpty();
	}

	@Test
	void fallosDuranteElBloqueoNoLoAlargan() {
		for (int i = 0; i < 5; i++) {
			limitador.registrarFallo("carla", "10.0.0.3");
		}
		reloj.avanzar(Duration.ofSeconds(200));
		limitador.registrarFallo("carla", "10.0.0.3");

		assertThat(limitador.tiempoBloqueado("carla", "10.0.0.3")).contains(Duration.ofSeconds(100));
	}

	@Test
	void reiniciarUsuarioBorraSuContador() {
		for (int i = 0; i < 4; i++) {
			limitador.registrarFallo("diego", "10.0.0." + i);
		}
		limitador.reiniciarUsuario("Diego");
		limitador.registrarFallo("diego", "10.0.0.50");

		assertThat(limitador.tiempoBloqueado("diego", "10.0.0.99")).isEmpty();
	}

	@Test
	void limpiaLasEntradasViejas() {
		for (int i = 0; i < 50; i++) {
			limitador.registrarFallo("usuario" + i, "10.1.0." + i);
		}
		assertThat(limitador.cantidadRegistros()).isEqualTo(100);

		reloj.avanzar(Duration.ofSeconds(301));
		limitador.registrarFallo("nuevo", "10.2.0.1");

		assertThat(limitador.cantidadRegistros()).isEqualTo(2);
	}

	@Test
	void cuentaBienConVariosHilos() throws Exception {
		LimitadorIntentos conMaximoAlto = new LimitadorIntentos(reloj, 400, 300);
		ExecutorService hilos = Executors.newFixedThreadPool(8);
		try {
			List<Future<?>> tareas = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				tareas.add(hilos.submit(() -> {
					for (int j = 0; j < 50; j++) {
						conMaximoAlto.registrarFallo("compartido", "10.3.0.1");
					}
				}));
			}
			for (Future<?> pendiente : tareas) {
				pendiente.get();
			}
		} finally {
			hilos.shutdown();
		}

		assertThat(conMaximoAlto.tiempoBloqueado("compartido", "10.9.9.9")).contains(Duration.ofSeconds(300));
	}

	@Test
	void rechazaConfiguracionInvalida() {
		assertThatThrownBy(() -> new LimitadorIntentos(reloj, 0, 300)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new LimitadorIntentos(reloj, 5, 0)).isInstanceOf(IllegalStateException.class);
	}
}
