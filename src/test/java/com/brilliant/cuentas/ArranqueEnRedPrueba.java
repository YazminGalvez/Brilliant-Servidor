package com.brilliant.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "server.address=0.0.0.0")
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
class ArranqueEnRedPrueba {

	@TempDir
	static Path directorioDatos;

	@DynamicPropertySource
	static void configurarDirectorioDatos(DynamicPropertyRegistry registro) {
		registro.add("cuentas.directorio-datos", directorioDatos::toString);
	}

	@LocalServerPort
	private int puerto;

	@Test
	void escuchandoEnTodasLasRedesLaSaludSigueRespondiendo() throws Exception {
		HttpResponse<String> respuesta = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/api/v1/salud")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.body()).contains("\"estado\":\"ok\"");
	}

	@Test
	void elArranqueAnunciaLasDireccionesDeRed(CapturedOutput salida) {
		// La captura empieza antes de levantar el contexto, así que incluye el log de arranque.
		for (String linea : AvisoDireccionesRed.mensajes("0.0.0.0", puerto,
				AvisoDireccionesRed.ipsDeRed())) {
			assertThat(salida.getAll()).contains(linea);
		}
		assertThat(salida.getAll()).doesNotContain(AvisoDireccionesRed.SOLO_LOCAL);
	}
}
