package com.brilliant.cuentas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.brilliant.cuentas.ConfiguracionRelojPrueba;
import com.brilliant.cuentas.RelojPrueba;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@Import(ConfiguracionRelojPrueba.class)
class PartidasPrueba {

	private static final String CONTRASENA = "Clave-segura-91";
	private static final String PATRON_CODIGO = "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}";
	private static final List<Integer> TABLERO = List.of(1, 2, 3, 4, 5, 6);

	static class DadosPrueba extends LanzadorDados {

		private final Deque<Integer> preparados = new ArrayDeque<>();
		private int tiros;

		synchronized void preparar(Integer... valores) {
			preparados.addAll(List.of(valores));
		}

		@Override
		public synchronized int lanzar() {
			int predeterminado = tiros++ % 2 == 0 ? 3 : 5;
			Integer preparado = preparados.poll();
			return preparado != null ? preparado : predeterminado;
		}
	}

	@TestConfiguration
	static class ConfiguracionDadosPrueba {

		@Bean
		@Primary
		DadosPrueba dadosPrueba() {
			return new DadosPrueba();
		}
	}

	@Autowired
	private DadosPrueba dados;

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

	@Autowired
	private RelojPrueba reloj;

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

	private ResultActions unirse(String token, String codigo) throws Exception {
		return mockMvc.perform(con(post("/api/v1/partidas/unirse"), token)
				.contentType(MediaType.APPLICATION_JSON).content(json(Map.of("codigo", codigo))));
	}

	private ResultActions consultar(String token, String codigo) throws Exception {
		return mockMvc.perform(con(get("/api/v1/partidas/" + codigo), token));
	}

	private ResultActions actual(String token) throws Exception {
		return mockMvc.perform(con(get("/api/v1/partidas/actual"), token));
	}

	private ResultActions listo(String token, String codigo, Boolean listo) throws Exception {
		Map<String, Boolean> cuerpo = new HashMap<>();
		cuerpo.put("listo", listo);
		return mockMvc.perform(con(post("/api/v1/partidas/" + codigo + "/listo"), token)
				.contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
	}

	private ResultActions salir(String token, String codigo) throws Exception {
		return mockMvc.perform(con(post("/api/v1/partidas/" + codigo + "/salir"), token));
	}

	private ResultActions tablero(String token, String codigo, List<Integer> numeros) throws Exception {
		Map<String, List<Integer>> cuerpo = new HashMap<>();
		cuerpo.put("numeros", numeros);
		return mockMvc.perform(con(post("/api/v1/partidas/" + codigo + "/tablero"), token)
				.contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
	}

	private String partidaComenzada(String... jugadores) throws Exception {
		String codigo = codigoNuevo(jugadores[0], jugadores.length);
		for (int i = 1; i < jugadores.length; i++) {
			unirse(jugadores[i], codigo).andExpect(status().isOk());
		}
		for (String jugador : jugadores) {
			listo(jugador, codigo, true).andExpect(status().isOk());
		}
		consultar(jugadores[0], codigo).andExpect(jsonPath("$.comenzada").value(true));
		return codigo;
	}

	private ResultActions turno(String token, String codigo, Integer ronda, Boolean tableroLleno, Integer zona)
			throws Exception {
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("ronda", ronda);
		cuerpo.put("tableroLleno", tableroLleno);
		cuerpo.put("zona", zona);
		return mockMvc.perform(con(post("/api/v1/partidas/" + codigo + "/turno"), token)
				.contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
	}

	private String rondaEnCurso(String... jugadores) throws Exception {
		String codigo = partidaComenzada(jugadores);
		for (String jugador : jugadores) {
			tablero(jugador, codigo, TABLERO).andExpect(status().isOk());
		}
		consultar(jugadores[0], codigo).andExpect(jsonPath("$.ronda").value(1));
		return codigo;
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
	void unirseConMinusculasYEspacios() throws Exception {
		String ana = jugadorNuevo("bea");
		String luis = jugadorNuevo("luis");
		String codigo = codigoNuevo(ana, 4);

		unirse(luis, "  " + codigo.toLowerCase(Locale.ROOT) + " ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.codigo").value(codigo))
				.andExpect(jsonPath("$.anfitrion").value("bea"))
				.andExpect(jsonPath("$.jugadores[*].nombre", contains("bea", "luis")));
	}

	@Test
	void volverAEntrarNoDuplicaAlJugador() throws Exception {
		String anfitrion = jugadorNuevo("carla");
		String invitado = jugadorNuevo("dario");
		String codigo = codigoNuevo(anfitrion, 2);

		unirse(invitado, codigo).andExpect(status().isOk());
		unirse(invitado, codigo).andExpect(status().isOk()).andExpect(jsonPath("$.jugadores", hasSize(2)));
		unirse(anfitrion, codigo).andExpect(status().isOk()).andExpect(jsonPath("$.jugadores", hasSize(2)));
	}

	@Test
	void salaLlenaResponde409() throws Exception {
		String anfitrion = jugadorNuevo("elena");
		String codigo = codigoNuevo(anfitrion, 2);
		unirse(jugadorNuevo("fabian"), codigo).andExpect(status().isOk());

		String tercero = jugadorNuevo("gabriel");
		unirse(tercero, codigo)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PARTIDA_LLENA"));
		actual(tercero).andExpect(status().isNoContent());
	}

	@Test
	void sinTokenResponde401() throws Exception {
		mockMvc.perform(post("/api/v1/partidas").contentType(MediaType.APPLICATION_JSON)
						.content("{\"mapa\":1,\"jugadores\":2}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
		mockMvc.perform(post("/api/v1/partidas/unirse").contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigo\":\"ABCDEF\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/partidas/actual")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/partidas/ABCDEF")).andExpect(status().isUnauthorized());
		consultar("token-falso", "ABCDEF").andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/partidas/ABCDEF/listo").contentType(MediaType.APPLICATION_JSON)
						.content("{\"listo\":true}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
		listo("token-falso", "ABCDEF", true).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/partidas/ABCDEF/salir"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
		salir("token-falso", "ABCDEF").andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/partidas/ABCDEF/tablero").contentType(MediaType.APPLICATION_JSON)
						.content("{\"numeros\":[1,2,3,4,5,6]}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
		tablero("token-falso", "ABCDEF", TABLERO).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/partidas/ABCDEF/turno").contentType(MediaType.APPLICATION_JSON)
						.content("{\"ronda\":1,\"tableroLleno\":false,\"zona\":null}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
		turno("token-falso", "ABCDEF", 1, false, null).andExpect(status().isUnauthorized());
	}

	@Test
	void laRondaAvanzaSoloCuandoTodosTerminaron() throws Exception {
		String ana = jugadorNuevo("ronda-ana");
		String beto = jugadorNuevo("ronda-beto");
		String ciro = jugadorNuevo("ronda-ciro");
		dados.preparar(1, 2, 4, 6);
		String codigo = rondaEnCurso(ana, beto, ciro);

		turno(ana, codigo, 1, false, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jugadores[*].jugoRonda", contains(true, false, false)))
				.andExpect(jsonPath("$.ronda").value(1))
				.andExpect(jsonPath("$.dados", contains(1, 2)));
		turno(beto, codigo, 1, false, null)
				.andExpect(jsonPath("$.jugadores[*].jugoRonda", contains(true, true, false)))
				.andExpect(jsonPath("$.ronda").value(1));
		turno(ciro, codigo, 1, false, null)
				.andExpect(jsonPath("$.jugadores[*].jugoRonda", contains(false, false, false)))
				.andExpect(jsonPath("$.ronda").value(2))
				.andExpect(jsonPath("$.dados", contains(4, 6)))
				.andExpect(jsonPath("$.terminada").value(false));
	}

	@Test
	void todosVenLosMismosDados() throws Exception {
		String ana = jugadorNuevo("mismos-ana");
		String beto = jugadorNuevo("mismos-beto");
		dados.preparar(6, 1, 2, 5);
		String codigo = rondaEnCurso(ana, beto);
		consultar(ana, codigo).andExpect(jsonPath("$.dados", contains(6, 1)));
		consultar(beto, codigo).andExpect(jsonPath("$.dados", contains(6, 1)));

		turno(ana, codigo, 1, false, null).andExpect(status().isOk());
		turno(beto, codigo, 1, false, null).andExpect(jsonPath("$.dados", contains(2, 5)));

		consultar(ana, codigo)
				.andExpect(jsonPath("$.ronda").value(2))
				.andExpect(jsonPath("$.dados", contains(2, 5)));
	}

	@Test
	void unAvisoDeOtraRondaSeIgnora() throws Exception {
		String ana = jugadorNuevo("vieja-ana");
		String beto = jugadorNuevo("vieja-beto");
		String codigo = rondaEnCurso(ana, beto);
		turno(ana, codigo, 1, false, null).andExpect(status().isOk());
		turno(beto, codigo, 1, false, null).andExpect(jsonPath("$.ronda").value(2));

		turno(ana, codigo, 1, true, 3)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ronda").value(2))
				.andExpect(jsonPath("$.jugadores[0].jugoRonda").value(false))
				.andExpect(jsonPath("$.zonas").isEmpty());
		turno(ana, codigo, 7, false, 4)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jugadores[0].jugoRonda").value(false))
				.andExpect(jsonPath("$.zonas").isEmpty());

		turno(ana, codigo, 2, false, null).andExpect(jsonPath("$.jugadores[0].jugoRonda").value(true));
	}

	@Test
	void unaZonaSeRegistraUnaVezYSoloUnaPorRonda() throws Exception {
		String ana = jugadorNuevo("zona-ana");
		String beto = jugadorNuevo("zona-beto");
		String codigo = rondaEnCurso(ana, beto);

		turno(ana, codigo, 1, false, 3).andExpect(jsonPath("$.zonas['3']['zona-ana']").value(1));
		turno(ana, codigo, 1, false, 4)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.zonas['4']").doesNotExist());
		turno(beto, codigo, 1, false, 3)
				.andExpect(jsonPath("$.zonas['3']['zona-ana']").value(1))
				.andExpect(jsonPath("$.zonas['3']['zona-beto']").value(1))
				.andExpect(jsonPath("$.ronda").value(2));

		turno(ana, codigo, 2, false, 3).andExpect(jsonPath("$.zonas['3']['zona-ana']").value(1));
		turno(beto, codigo, 2, false, 5)
				.andExpect(jsonPath("$.zonas['5']['zona-beto']").value(2))
				.andExpect(jsonPath("$.zonas['5']['zona-ana']").doesNotExist());
		consultar(ana, codigo).andExpect(jsonPath("$.zonas['3']['zona-beto']").value(1));
	}

	@Test
	void laPartidaTerminaCuandoTodosLlenaronSuTablero() throws Exception {
		String ana = jugadorNuevo("lleno-ana");
		String beto = jugadorNuevo("lleno-beto");
		String codigo = rondaEnCurso(ana, beto);

		turno(ana, codigo, 1, true, null).andExpect(jsonPath("$.ronda").value(1));
		turno(beto, codigo, 1, false, null)
				.andExpect(jsonPath("$.ronda").value(2))
				.andExpect(jsonPath("$.terminada").value(false));

		turno(beto, codigo, 2, false, null)
				.andExpect(jsonPath("$.ronda").value(3))
				.andExpect(jsonPath("$.terminada").value(false));

		turno(beto, codigo, 3, true, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.terminada").value(true))
				.andExpect(jsonPath("$.motivoFin").value("sin_tableros"))
				.andExpect(jsonPath("$.ronda").value(3))
				.andExpect(jsonPath("$.puntos").isEmpty())
				.andExpect(jsonPath("$.meta").value(0))
				.andExpect(jsonPath("$.ganadores", hasSize(0)));

		turno(ana, codigo, 3, false, 2).andExpect(jsonPath("$.zonas").isEmpty());
		crear(ana, 1, 2).andExpect(status().isCreated());
	}

	@Test
	void turnoConDatosInvalidosResponde400() throws Exception {
		String ana = jugadorNuevo("turno-ana");
		String beto = jugadorNuevo("turno-beto");
		String codigo = rondaEnCurso(ana, beto);

		for (Map<String, Object> cuerpo : List.of(
				Map.<String, Object>of("tableroLleno", false),
				Map.<String, Object>of("ronda", 1),
				Map.<String, Object>of("ronda", 1, "tableroLleno", false, "zona", 0),
				Map.<String, Object>of("ronda", 1, "tableroLleno", false, "zona", 50))) {
			mockMvc.perform(con(post("/api/v1/partidas/" + codigo + "/turno"), ana)
							.contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		}
		consultar(ana, codigo).andExpect(jsonPath("$.jugadores[0].jugoRonda").value(false));
	}

	@Test
	void turnoAntesDeComenzarOAjenoResponde409O404() throws Exception {
		String ana = jugadorNuevo("espera-ana");
		String codigo = codigoNuevo(ana, 2);

		turno(ana, codigo, 1, false, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PARTIDA_NO_COMENZADA"));
		esperarNoEncontrada(turno(jugadorNuevo("espera-beto"), codigo, 1, false, null));
	}

	@Test
	void losDadosSeLanzanSoloCuandoTodosEnviaronSuTablero() throws Exception {
		String ana = jugadorNuevo("dados-ana");
		String beto = jugadorNuevo("dados-beto");
		String ciro = jugadorNuevo("dados-ciro");
		String codigo = partidaComenzada(ana, beto, ciro);
		consultar(ana, codigo)
				.andExpect(jsonPath("$.ronda").value(0))
				.andExpect(jsonPath("$.dados", hasSize(0)));

		tablero(ana, codigo, TABLERO)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jugadores[*].tableroListo", contains(true, false, false)))
				.andExpect(jsonPath("$.ronda").value(0))
				.andExpect(jsonPath("$.dados", hasSize(0)));
		tablero(beto, codigo, List.of(6, 5, 4, 3, 2, 1))
				.andExpect(jsonPath("$.jugadores[*].tableroListo", contains(true, true, false)))
				.andExpect(jsonPath("$.ronda").value(0))
				.andExpect(jsonPath("$.dados", hasSize(0)));
		tablero(ciro, codigo, List.of(2, 4, 6, 1, 3, 5))
				.andExpect(jsonPath("$.jugadores[*].tableroListo", contains(true, true, true)))
				.andExpect(jsonPath("$.ronda").value(1))
				.andExpect(jsonPath("$.dados", contains(3, 5)));

		consultar(ana, codigo)
				.andExpect(jsonPath("$.ronda").value(1))
				.andExpect(jsonPath("$.dados", contains(3, 5)));
	}

	@Test
	void siSaleQuienNoEnvioSuTableroSeLanzanLosDados() throws Exception {
		String ana = jugadorNuevo("pendiente-ana");
		String beto = jugadorNuevo("pendiente-beto");
		String ciro = jugadorNuevo("pendiente-ciro");
		String codigo = partidaComenzada(ana, beto, ciro);
		tablero(ana, codigo, TABLERO).andExpect(status().isOk());
		tablero(beto, codigo, TABLERO).andExpect(jsonPath("$.ronda").value(0));

		salir(ciro, codigo).andExpect(status().isNoContent());

		consultar(ana, codigo)
				.andExpect(jsonPath("$.ronda").value(1))
				.andExpect(jsonPath("$.dados", contains(3, 5)));
	}

	static Stream<List<Integer>> tablerosInvalidos() {
		return Stream.of(
				List.of(), List.of(1, 2, 3, 4, 5), List.of(1, 2, 3, 4, 5, 6, 1), List.of(1, 1, 2, 3, 4, 5),
				List.of(0, 1, 2, 3, 4, 5), List.of(2, 3, 4, 5, 6, 7), Arrays.asList(1, 2, 3, 4, 5, null), null);
	}

	@ParameterizedTest
	@MethodSource("tablerosInvalidos")
	void tableroInvalidoResponde400(List<Integer> numeros) throws Exception {
		String ana = jugadorNuevo("invalido-" + Math.abs(String.valueOf(numeros).hashCode()) + "-a");
		String beto = jugadorNuevo("invalido-" + Math.abs(String.valueOf(numeros).hashCode()) + "-b");
		String codigo = partidaComenzada(ana, beto);

		tablero(ana, codigo, numeros)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		consultar(ana, codigo)
				.andExpect(jsonPath("$.jugadores[0].tableroListo").value(false))
				.andExpect(jsonPath("$.miTablero", hasSize(0)));
	}

	@Test
	void tableroAntesDeComenzarResponde409() throws Exception {
		String ana = jugadorNuevo("temprano-ana");
		String codigo = codigoNuevo(ana, 2);
		unirse(jugadorNuevo("temprano-beto"), codigo).andExpect(status().isOk());

		tablero(ana, codigo, TABLERO)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PARTIDA_NO_COMENZADA"));
		consultar(ana, codigo).andExpect(jsonPath("$.jugadores[0].tableroListo").value(false));
	}

	@Test
	void reenviarElTableroNoCambiaNada() throws Exception {
		String ana = jugadorNuevo("reintento-ana");
		String beto = jugadorNuevo("reintento-beto");
		String codigo = partidaComenzada(ana, beto);
		tablero(ana, codigo, TABLERO).andExpect(status().isOk());

		tablero(ana, codigo, List.of(6, 5, 4, 3, 2, 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.miTablero", contains(1, 2, 3, 4, 5, 6)));

		tablero(beto, codigo, TABLERO).andExpect(jsonPath("$.ronda").value(1));
		tablero(beto, codigo, List.of(6, 5, 4, 3, 2, 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ronda").value(1))
				.andExpect(jsonPath("$.dados", contains(3, 5)))
				.andExpect(jsonPath("$.miTablero", contains(1, 2, 3, 4, 5, 6)));
	}

	@Test
	void miTableroNoRevelaElDeOtros() throws Exception {
		String ana = jugadorNuevo("secreto-ana");
		String beto = jugadorNuevo("secreto-beto");
		String codigo = partidaComenzada(ana, beto);

		tablero(ana, codigo, List.of(4, 2, 6, 1, 5, 3)).andExpect(jsonPath("$.miTablero", contains(4, 2, 6, 1, 5, 3)));
		consultar(beto, codigo)
				.andExpect(jsonPath("$.jugadores[0].tableroListo").value(true))
				.andExpect(jsonPath("$.miTablero", hasSize(0)));

		tablero(beto, codigo, List.of(3, 1, 2, 6, 4, 5)).andExpect(jsonPath("$.miTablero", contains(3, 1, 2, 6, 4, 5)));
		consultar(ana, codigo).andExpect(jsonPath("$.miTablero", contains(4, 2, 6, 1, 5, 3)));
		unirse(beto, codigo).andExpect(jsonPath("$.miTablero", contains(3, 1, 2, 6, 4, 5)));
	}

	@Test
	void tableroDeQuienNoEsJugadorResponde404() throws Exception {
		String ana = jugadorNuevo("intruso-ana");
		String beto = jugadorNuevo("intruso-beto");
		String codigo = partidaComenzada(ana, beto);

		esperarNoEncontrada(tablero(jugadorNuevo("intruso-ciro"), codigo, TABLERO));
		esperarNoEncontrada(tablero(ana, "ZZZZZZ", TABLERO));
	}

	@Test
	void salirDelAnfitrionCierraLaSalaParaTodos() throws Exception {
		String anfitrion = jugadorNuevo("cierra-ana");
		String invitado = jugadorNuevo("cierra-beto");
		String codigo = codigoNuevo(anfitrion, 4);
		unirse(invitado, codigo).andExpect(status().isOk());

		salir(anfitrion, codigo).andExpect(status().isNoContent()).andExpect(content().string(""));

		esperarNoEncontrada(consultar(invitado, codigo));
		actual(invitado).andExpect(status().isNoContent());
		actual(anfitrion).andExpect(status().isNoContent());
		esperarNoEncontrada(unirse(jugadorNuevo("cierra-ciro"), codigo));
	}

	@Test
	void unInvitadoQueSaleDejaLibreSuLugar() throws Exception {
		String anfitrion = jugadorNuevo("libre-ana");
		String invitado = jugadorNuevo("libre-beto");
		String codigo = codigoNuevo(anfitrion, 2);
		unirse(invitado, codigo).andExpect(status().isOk());

		salir(invitado, codigo.toLowerCase(Locale.ROOT)).andExpect(status().isNoContent());

		consultar(anfitrion, codigo).andExpect(jsonPath("$.jugadores[*].nombre", contains("libre-ana")));
		actual(invitado).andExpect(status().isNoContent());
		unirse(jugadorNuevo("libre-ciro"), codigo)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jugadores[*].nombre", contains("libre-ana", "libre-ciro")));
	}

	@Test
	void laSalaSinJugadoresDesaparece() throws Exception {
		String anfitrion = jugadorNuevo("vacia-ana");
		String invitado = jugadorNuevo("vacia-beto");
		String codigo = partidaComenzada(anfitrion, invitado);

		salir(anfitrion, codigo).andExpect(status().isNoContent());
		consultar(invitado, codigo)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.anfitrion").value("vacia-ana"))
				.andExpect(jsonPath("$.jugadores[*].nombre", contains("vacia-beto")));

		salir(invitado, codigo).andExpect(status().isNoContent());
		esperarNoEncontrada(unirse(jugadorNuevo("vacia-ciro"), codigo));
	}

	@Test
	void unInvitadoSinContactoSaleDeLaSala() throws Exception {
		String anfitrion = jugadorNuevo("ausente-ana");
		String invitado = jugadorNuevo("ausente-beto");
		String codigo = codigoNuevo(anfitrion, 4);
		unirse(invitado, codigo).andExpect(status().isOk());

		reloj.avanzar(Duration.ofSeconds(119));
		consultar(anfitrion, codigo).andExpect(jsonPath("$.jugadores", hasSize(2)));

		reloj.avanzar(Duration.ofSeconds(1));
		consultar(anfitrion, codigo).andExpect(jsonPath("$.jugadores[*].nombre", contains("ausente-ana")));
		actual(invitado).andExpect(status().isNoContent());
	}

	@Test
	void unAnfitrionSinContactoCierraLaSala() throws Exception {
		String anfitrion = jugadorNuevo("dormido-ana");
		String invitado = jugadorNuevo("dormido-beto");
		String codigo = codigoNuevo(anfitrion, 4);
		unirse(invitado, codigo).andExpect(status().isOk());

		reloj.avanzar(Duration.ofSeconds(60));
		consultar(invitado, codigo).andExpect(status().isOk());
		reloj.avanzar(Duration.ofSeconds(60));

		esperarNoEncontrada(consultar(invitado, codigo));
		actual(anfitrion).andExpect(status().isNoContent());
	}

	@Test
	void laFaltaDeContactoNoSacaDeUnaPartidaComenzada() throws Exception {
		String anfitrion = jugadorNuevo("jugando-ana");
		String invitado = jugadorNuevo("jugando-beto");
		String codigo = partidaComenzada(anfitrion, invitado);

		reloj.avanzar(Duration.ofSeconds(300));

		consultar(invitado, codigo).andExpect(jsonPath("$.jugadores", hasSize(2)));
	}

	@Test
	void salirDeUnaSalaAjenaResponde404() throws Exception {
		String anfitrion = jugadorNuevo("ajena-ana");
		String otro = jugadorNuevo("ajena-beto");
		String codigo = codigoNuevo(anfitrion, 4);
		String suSala = codigoNuevo(otro, 4);

		esperarNoEncontrada(salir(otro, codigo));
		esperarNoEncontrada(salir(otro, "ZZZZZZ"));

		consultar(anfitrion, codigo).andExpect(status().isOk());
		actual(otro).andExpect(jsonPath("$.codigo").value(suSala));
	}

	@Test
	void comienzaSoloCuandoTodosEstanListosYSonDosOMas() throws Exception {
		String anfitrion = jugadorNuevo("listo-ana");
		String segundo = jugadorNuevo("listo-beto");
		String tercero = jugadorNuevo("listo-ciro");
		String codigo = codigoNuevo(anfitrion, 3);
		unirse(segundo, codigo).andExpect(status().isOk());
		unirse(tercero, codigo).andExpect(status().isOk());

		listo(anfitrion, codigo, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jugadores[0].listo").value(true))
				.andExpect(jsonPath("$.jugadores[1].listo").value(false))
				.andExpect(jsonPath("$.comenzada").value(false));
		listo(segundo, codigo, true).andExpect(jsonPath("$.comenzada").value(false));
		listo(tercero, codigo, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jugadores[*].listo", contains(true, true, true)))
				.andExpect(jsonPath("$.comenzada").value(true));
		consultar(segundo, codigo).andExpect(jsonPath("$.comenzada").value(true));
	}

	@Test
	void deshacerListoEvitaElComienzo() throws Exception {
		String anfitrion = jugadorNuevo("deshacer-ana");
		String invitado = jugadorNuevo("deshacer-beto");
		String codigo = codigoNuevo(anfitrion, 2);
		unirse(invitado, codigo).andExpect(status().isOk());

		listo(anfitrion, codigo, true).andExpect(jsonPath("$.jugadores[0].listo").value(true));
		listo(anfitrion, codigo, false).andExpect(jsonPath("$.jugadores[0].listo").value(false));
		listo(invitado, codigo, true).andExpect(jsonPath("$.comenzada").value(false));

		listo(anfitrion, codigo, true).andExpect(jsonPath("$.comenzada").value(true));
	}

	@Test
	void aSolasNoComienza() throws Exception {
		String anfitrion = jugadorNuevo("solitario");
		String codigo = codigoNuevo(anfitrion, 4);

		listo(anfitrion, codigo, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jugadores[0].listo").value(true))
				.andExpect(jsonPath("$.comenzada").value(false));
	}

	@Test
	void siSaleQuienNoEstabaListoComienzaConLosDemas() throws Exception {
		String anfitrion = jugadorNuevo("sale-ana");
		String listoInvitado = jugadorNuevo("sale-beto");
		String indeciso = jugadorNuevo("sale-ciro");
		String codigo = codigoNuevo(anfitrion, 3);
		unirse(listoInvitado, codigo).andExpect(status().isOk());
		unirse(indeciso, codigo).andExpect(status().isOk());
		listo(anfitrion, codigo, true).andExpect(status().isOk());
		listo(listoInvitado, codigo, true).andExpect(jsonPath("$.comenzada").value(false));

		codigoNuevo(indeciso, 2);

		consultar(anfitrion, codigo)
				.andExpect(jsonPath("$.jugadores", hasSize(2)))
				.andExpect(jsonPath("$.comenzada").value(true));
	}

	@Test
	void listoConLaPartidaComenzadaNoCambiaNada() throws Exception {
		String anfitrion = jugadorNuevo("cambio-ana");
		String invitado = jugadorNuevo("cambio-beto");
		String codigo = partidaComenzada(anfitrion, invitado);

		listo(invitado, codigo, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jugadores[1].listo").value(true))
				.andExpect(jsonPath("$.comenzada").value(true));
	}

	@Test
	void unExtranoNoPuedeEntrarAUnaPartidaComenzada() throws Exception {
		String anfitrion = jugadorNuevo("extrano-ana");
		String invitado = jugadorNuevo("extrano-beto");
		String codigo = partidaComenzada(anfitrion, invitado);

		String extrano = jugadorNuevo("extrano-ciro");
		unirse(extrano, codigo)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PARTIDA_COMENZADA"));
		actual(extrano).andExpect(status().isNoContent());
	}

	@Test
	void elJugadorOriginalPuedeVolverAEntrar() throws Exception {
		String anfitrion = jugadorNuevo("regreso-ana");
		String invitado = jugadorNuevo("regreso-beto");
		String codigo = partidaComenzada(anfitrion, invitado);

		unirse(invitado, codigo.toLowerCase(Locale.ROOT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comenzada").value(true))
				.andExpect(jsonPath("$.jugadores", hasSize(2)));
		unirse(anfitrion, codigo).andExpect(status().isOk());
	}

	@Test
	void crearOUnirseConUnaPartidaComenzadaResponde409() throws Exception {
		String anfitrion = jugadorNuevo("ocupado-ana");
		String invitado = jugadorNuevo("ocupado-beto");
		String codigo = partidaComenzada(anfitrion, invitado);
		String otra = codigoNuevo(jugadorNuevo("ocupado-ciro"), 4);

		crear(anfitrion, 1, 2)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("YA_EN_PARTIDA"));
		unirse(invitado, otra)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("YA_EN_PARTIDA"));

		actual(anfitrion).andExpect(jsonPath("$.codigo").value(codigo));
		actual(invitado).andExpect(jsonPath("$.codigo").value(codigo));
	}

	@Test
	void listoDeQuienNoEsJugadorResponde404() throws Exception {
		String anfitrion = jugadorNuevo("ajeno-ana");
		String ajeno = jugadorNuevo("ajeno-beto");
		String codigo = codigoNuevo(anfitrion, 2);

		esperarNoEncontrada(listo(ajeno, codigo, true));
		esperarNoEncontrada(listo(ajeno, "ZZZZZZ", true));
		consultar(anfitrion, codigo).andExpect(jsonPath("$.jugadores", hasSize(1)));
	}

	@Test
	void listoSinValorResponde400() throws Exception {
		String anfitrion = jugadorNuevo("vacio-ana");
		String codigo = codigoNuevo(anfitrion, 2);

		listo(anfitrion, codigo, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
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
	void crearEstandoEnOtraSalaLoSacaDeLaAnterior() throws Exception {
		String anfitrion = jugadorNuevo("lucia");
		String invitado = jugadorNuevo("mario");
		String primera = codigoNuevo(anfitrion, 4);
		unirse(invitado, primera).andExpect(status().isOk());

		String segunda = codigoNuevo(invitado, 4);

		consultar(anfitrion, primera).andExpect(jsonPath("$.jugadores[*].nombre", contains("lucia")));
		esperarNoEncontrada(consultar(invitado, primera));
		actual(invitado).andExpect(jsonPath("$.codigo").value(segunda));
	}

	@Test
	void siElAnfitrionCreaOtraSalaLaAnteriorSeCierraParaTodos() throws Exception {
		String anfitrion = jugadorNuevo("nora");
		String invitado = jugadorNuevo("oscar");
		String primera = codigoNuevo(anfitrion, 4);
		unirse(invitado, primera).andExpect(status().isOk());

		codigoNuevo(anfitrion, 4);

		esperarNoEncontrada(consultar(invitado, primera));
		actual(invitado).andExpect(status().isNoContent());
		esperarNoEncontrada(unirse(jugadorNuevo("pablo"), primera));
	}

	@Test
	void unirseAOtraSalaSacaDeLaAnterior() throws Exception {
		String primeraAnfitriona = jugadorNuevo("raquel");
		String segundaAnfitriona = jugadorNuevo("sara");
		String invitado = jugadorNuevo("tomas");
		String primera = codigoNuevo(primeraAnfitriona, 4);
		String segunda = codigoNuevo(segundaAnfitriona, 4);
		unirse(invitado, primera).andExpect(status().isOk());

		unirse(invitado, segunda).andExpect(status().isOk());

		consultar(primeraAnfitriona, primera).andExpect(jsonPath("$.jugadores[*].nombre", contains("raquel")));
		actual(invitado).andExpect(jsonPath("$.codigo").value(segunda));
	}

	@Test
	void consultarCuentaComoSenalDeConexion() throws Exception {
		String anfitrion = jugadorNuevo("ursula");
		String invitado = jugadorNuevo("victor");
		String codigo = codigoNuevo(anfitrion, 2);
		unirse(invitado, codigo).andExpect(status().isOk());

		reloj.avanzar(ServicioPartidas.TOLERANCIA_CONEXION);
		consultar(anfitrion, codigo)
				.andExpect(jsonPath("$.jugadores[0].conectado").value(true))
				.andExpect(jsonPath("$.jugadores[1].conectado").value(false));

		consultar(invitado, codigo).andExpect(jsonPath("$.jugadores[1].conectado").value(true));
	}

	@Test
	void muchosCodigosInexistentesResponden429() throws Exception {
		String anfitrion = jugadorNuevo("walter");
		String adivinador = jugadorNuevo("ximena");
		String codigo = codigoNuevo(anfitrion, 4);

		for (int i = 0; i < 10; i++) {
			esperarNoEncontrada(unirse(adivinador, "ZZZZZ" + i));
		}
		unirse(adivinador, codigo)
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
				.andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));

		unirse(anfitrion, "ZZZZZZ").andExpect(status().isNotFound());

		reloj.avanzar(Duration.ofSeconds(60));
		unirse(adivinador, codigo).andExpect(status().isOk());
	}

	@Test
	void unirseBienReiniciaElContadorDeCodigos() throws Exception {
		String anfitrion = jugadorNuevo("yolanda");
		String jugador = jugadorNuevo("zoe");
		String codigo = codigoNuevo(anfitrion, 4);

		for (int i = 0; i < 9; i++) {
			esperarNoEncontrada(unirse(jugador, "YYYYY" + i));
		}
		unirse(jugador, codigo).andExpect(status().isOk());
		for (int i = 0; i < 9; i++) {
			esperarNoEncontrada(unirse(jugador, "XXXXX" + i));
		}
		unirse(jugador, codigo).andExpect(status().isOk());
	}

	@Test
	void unirseSinCodigoResponde400() throws Exception {
		mockMvc.perform(con(post("/api/v1/partidas/unirse"), jugadorNuevo("adrian"))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}
}
