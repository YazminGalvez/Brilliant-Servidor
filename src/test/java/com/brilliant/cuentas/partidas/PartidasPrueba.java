package com.brilliant.cuentas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class PartidasPrueba {

	private static final String CONTRASENA = "Clave-segura-91";
	private static final String PATRON_CODIGO = "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}";

	@TempDir
	static Path directorioDatos;

	@DynamicPropertySource
	static void configurarDirectorioDatos(DynamicPropertyRegistry registro) {
		registro.add("cuentas.directorio-datos", directorioDatos::toString);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper mapeador;

	private String json(Object valor) throws Exception {
		return mapeador.writeValueAsString(valor);
	}

	private String jugadorNuevo(String nombre) throws Exception {
		Map<String, String> datos = Map.of("nombre", nombre, "contrasena", CONTRASENA);
		mockMvc.perform(post("/api/v1/cuentas").contentType(MediaType.APPLICATION_JSON).content(json(datos)))
				.andExpect(status().isCreated());
		String respuesta = mockMvc.perform(post("/api/v1/sesiones").contentType(MediaType.APPLICATION_JSON)
						.content(json(datos)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return mapeador.readTree(respuesta).get("token").asText();
	}

	private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private ResultActions crear(String token, Integer mapa, Integer jugadores) throws Exception {
		Map<String, Integer> cuerpo = new HashMap<>();
		cuerpo.put("mapa", mapa);
		cuerpo.put("jugadores", jugadores);
		return mockMvc.perform(con(post("/api/v1/partidas"), token)
				.contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
	}

	private String codigoNuevo(String token, int jugadores) throws Exception {
		String respuesta = crear(token, 1, jugadores).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return mapeador.readTree(respuesta).get("codigo").asText();
	}

	private ResultActions consultar(String token, String codigo) throws Exception {
		return mockMvc.perform(con(get("/api/v1/partidas/" + codigo), token));
	}

	private ResultActions actual(String token) throws Exception {
		return mockMvc.perform(con(get("/api/v1/partidas/actual"), token));
	}

	private static void esperarNoEncontrada(ResultActions resultado) throws Exception {
		resultado.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PARTIDA_NO_ENCONTRADA"));
	}

	@Test
	void crearDevuelveCodigoValidoYAlAnfitrion() throws Exception {
		String token = jugadorNuevo("ana");

		String respuesta = crear(token, 2, 4)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.codigo").value(matchesPattern(PATRON_CODIGO)))
				.andExpect(jsonPath("$.mapa").value(2))
				.andExpect(jsonPath("$.capacidad").value(4))
				.andExpect(jsonPath("$.anfitrion").value("ana"))
				.andExpect(jsonPath("$.jugadores", hasSize(1)))
				.andExpect(jsonPath("$.jugadores[0].nombre").value("ana"))
				.andExpect(jsonPath("$.jugadores[0].listo").value(false))
				.andExpect(jsonPath("$.jugadores[0].tableroListo").value(false))
				.andExpect(jsonPath("$.jugadores[0].conectado").value(true))
				.andExpect(jsonPath("$.jugadores[0].concedio").value(false))
				.andExpect(jsonPath("$.jugadores[0].jugoRonda").value(false))
				.andExpect(jsonPath("$.comenzada").value(false))
				.andExpect(jsonPath("$.terminada").value(false))
				.andExpect(jsonPath("$.ronda").value(0))
				.andExpect(jsonPath("$.dados", hasSize(0)))
				.andExpect(jsonPath("$.miTablero", hasSize(0)))
				.andExpect(jsonPath("$.meta").value(0))
				.andExpect(jsonPath("$.ganadores", hasSize(0)))
				.andReturn().getResponse().getContentAsString();

		JsonNode sala = mapeador.readTree(respuesta);
		assertThat(sala.get("zonas").isObject() && sala.get("zonas").isEmpty()).isTrue();
		assertThat(sala.get("puntos").isObject() && sala.get("puntos").isEmpty()).isTrue();
		assertThat(sala.has("motivoFin") && sala.get("motivoFin").isNull()).isTrue();
	}

	static Stream<Arguments> datosInvalidos() {
		return Stream.of(
				Arguments.of(0, 4), Arguments.of(5, 4), Arguments.of(1, 1), Arguments.of(1, 7),
				Arguments.of(null, 4), Arguments.of(1, null));
	}

	@ParameterizedTest
	@MethodSource("datosInvalidos")
	void mapaOCantidadInvalidosResponden400(Integer mapa, Integer jugadores) throws Exception {
		String token = jugadorNuevo("invalido-" + mapa + "-" + jugadores);
		crear(token, mapa, jugadores)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		actual(token).andExpect(status().isNoContent());
	}

	@Test
	void sinTokenResponde401() throws Exception {
		mockMvc.perform(post("/api/v1/partidas").contentType(MediaType.APPLICATION_JSON)
						.content("{\"mapa\":1,\"jugadores\":2}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
		mockMvc.perform(get("/api/v1/partidas/actual")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/partidas/ABCDEF")).andExpect(status().isUnauthorized());
		consultar("token-falso", "ABCDEF").andExpect(status().isUnauthorized());
	}

	@Test
	void quienNoEsJugadorRecibe404IgualQueUnCodigoInexistente() throws Exception {
		String anfitrion = jugadorNuevo("hugo");
		String ajeno = jugadorNuevo("ines");
		String codigo = codigoNuevo(anfitrion, 4);

		String existente = consultar(ajeno, codigo).andReturn().getResponse().getContentAsString();
		esperarNoEncontrada(consultar(ajeno, codigo));
		String inexistente = consultar(ajeno, "ZZZZZZ").andReturn().getResponse().getContentAsString();

		assertThat(existente).isEqualTo(inexistente);
		consultar(anfitrion, codigo).andExpect(status().isOk());
	}

	@Test
	void consultarNoDistingueMayusculas() throws Exception {
		String anfitrion = jugadorNuevo("julia");
		String codigo = codigoNuevo(anfitrion, 3);

		consultar(anfitrion, codigo.toLowerCase(Locale.ROOT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.codigo").value(codigo));
	}

	@Test
	void actualDevuelve200O204() throws Exception {
		String token = jugadorNuevo("karla");
		actual(token).andExpect(status().isNoContent()).andExpect(content().string(""));

		String codigo = codigoNuevo(token, 3);
		actual(token).andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value(codigo));
	}

	@Test
	void crearEstandoEnOtraSalaLaDeja() throws Exception {
		String token = jugadorNuevo("lucia");
		String primera = codigoNuevo(token, 4);

		String segunda = codigoNuevo(token, 2);

		assertThat(segunda).isNotEqualTo(primera);
		esperarNoEncontrada(consultar(token, primera));
		actual(token).andExpect(jsonPath("$.codigo").value(segunda)).andExpect(jsonPath("$.capacidad").value(2));
	}
}
