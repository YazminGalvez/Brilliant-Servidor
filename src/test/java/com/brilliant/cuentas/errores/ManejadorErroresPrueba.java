package com.brilliant.cuentas.errores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.brilliant.cuentas.ServicioCuentas;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class ManejadorErroresPrueba {

	@TempDir
	static Path directorioDatos;

	@DynamicPropertySource
	static void configurarDirectorioDatos(DynamicPropertyRegistry registro) {
		registro.add("cuentas.directorio-datos", directorioDatos::toString);
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ServicioCuentas servicio;

	@Test
	void errorInesperadoResponde500SinDetallesInternos() throws Exception {
		given(servicio.crearCuenta(any(), any())).willThrow(new IllegalStateException("detalle interno"));

		String respuesta = mockMvc.perform(post("/api/v1/cuentas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Ana\",\"contrasena\":\"Clave-segura-91\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
				.andReturn().getResponse().getContentAsString();

		assertThat(respuesta).doesNotContain("detalle interno", "IllegalStateException", "at com.");
	}
}
