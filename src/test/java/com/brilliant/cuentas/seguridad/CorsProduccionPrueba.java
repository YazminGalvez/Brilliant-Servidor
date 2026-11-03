package com.brilliant.cuentas.seguridad;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"cuentas.produccion=true",
		"cuentas.origenes-permitidos= https://juego.ejemplo.com , https://otro.ejemplo.com" })
@AutoConfigureMockMvc
@DirtiesContext
class CorsProduccionPrueba {

	@TempDir
	static Path directorioDatos;

	@DynamicPropertySource
	static void configurarDirectorioDatos(DynamicPropertyRegistry registro) {
		registro.add("cuentas.directorio-datos", directorioDatos::toString);
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void origenNoListadoNoRecibeCabecerasCors() throws Exception {
		mockMvc.perform(options("/api/v1/sesiones")
						.header(HttpHeaders.ORIGIN, "http://localhost:5555")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

		mockMvc.perform(get("/api/v1/salud").header(HttpHeaders.ORIGIN, "http://localhost:5555"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void origenListadoRecibeCabecerasCors() throws Exception {
		mockMvc.perform(options("/api/v1/cuentas/yo")
						.header(HttpHeaders.ORIGIN, "https://juego.ejemplo.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://juego.ejemplo.com"));

		mockMvc.perform(get("/api/v1/salud").header(HttpHeaders.ORIGIN, "https://otro.ejemplo.com"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://otro.ejemplo.com"));
	}

	@Test
	void metodoOCabeceraNoPermitidosSeRechazan() throws Exception {
		mockMvc.perform(options("/api/v1/sesiones")
						.header(HttpHeaders.ORIGIN, "https://juego.ejemplo.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
				.andExpect(status().isForbidden());

		mockMvc.perform(options("/api/v1/sesiones")
						.header(HttpHeaders.ORIGIN, "https://juego.ejemplo.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-otra-cabecera"))
				.andExpect(status().isForbidden());
	}

	@Test
	void origenesConComodinSeRechazan() {
		assertThatThrownBy(() -> ConfiguracionSeguridad.crearConfiguracionCors("https://*.ejemplo.com", true))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> ConfiguracionSeguridad.crearConfiguracionCors("*", false))
				.isInstanceOf(IllegalStateException.class);
	}
}
