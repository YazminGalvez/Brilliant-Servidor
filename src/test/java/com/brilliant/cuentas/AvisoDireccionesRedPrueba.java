package com.brilliant.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
@DirtiesContext
class AvisoDireccionesRedPrueba {

	@TempDir
	static Path directorioDatos;

	@DynamicPropertySource
	static void configurarDirectorioDatos(DynamicPropertyRegistry registro) {
		registro.add("cuentas.directorio-datos", directorioDatos::toString);
	}

	@Autowired
	private TestRestTemplate cliente;

	@Test
	void conVariasDireccionesImprimeUnaLineaPorCada() {
		assertThat(AvisoDireccionesRed.mensajes("0.0.0.0", 8080, List.of("192.168.1.50", "10.0.0.7")))
				.containsExactly("Disponible en http://192.168.1.50:8080", "Disponible en http://10.0.0.7:8080");
		assertThat(AvisoDireccionesRed.mensajes("", 9090, List.of("192.168.1.50")))
				.containsExactly("Disponible en http://192.168.1.50:9090");
	}

	@Test
	void sinDireccionesLoAvisa() {
		assertThat(AvisoDireccionesRed.mensajes("0.0.0.0", 8080, List.of()))
				.containsExactly(AvisoDireccionesRed.SIN_DIRECCIONES);
	}

	@Test
	void soloLocalRecuerdaComoAbrirlo() {
		for (String direccion : List.of("127.0.0.1", "localhost", " LocalHost ", "::1")) {
			assertThat(AvisoDireccionesRed.mensajes(direccion, 8080, List.of("192.168.1.50")))
					.containsExactly(AvisoDireccionesRed.SOLO_LOCAL);
		}
	}

	@Test
	void conUnaDireccionFijaSoloMuestraEsa() {
		assertThat(AvisoDireccionesRed.mensajes("192.168.1.50", 8080, List.of("192.168.1.50", "10.0.0.7")))
				.containsExactly("Disponible en http://192.168.1.50:8080");
	}

	@Test
	void lasDireccionesDeRedSonIpv4SinLoopback() {
		assertThat(AvisoDireccionesRed.ipsDeRed())
				.allMatch(ip -> ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"))
				.noneMatch(ip -> ip.startsWith("127.") || ip.startsWith("169.254."));
	}

	@Test
	void alArrancarLoImprimeYSaludSigueRespondiendo(CapturedOutput salida) {
		ResponseEntity<Map> respuesta = cliente.getForEntity("/api/v1/salud", Map.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).containsEntry("estado", "ok");
		assertThat(salida.getOut()).contains(AvisoDireccionesRed.SOLO_LOCAL);
	}
}
