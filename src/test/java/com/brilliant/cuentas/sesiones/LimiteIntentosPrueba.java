package com.brilliant.cuentas.sesiones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.brilliant.cuentas.ConfiguracionRelojPrueba;
import com.brilliant.cuentas.RelojPrueba;
import com.brilliant.cuentas.usuarios.RepositorioUsuarios;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@Import(ConfiguracionRelojPrueba.class)
class LimiteIntentosPrueba {

	private static final String CONTRASENA = "Clave-segura-91";
	private static final String INCORRECTA = "Otra-clave-mala-1";

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

	@Autowired
	private RepositorioUsuarios repositorioUsuarios;

	private static MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder peticion, String ip) {
		return peticion.with(solicitud -> {
			solicitud.setRemoteAddr(ip);
			return solicitud;
		});
	}

	private void registrar(String nombre) throws Exception {
		mockMvc.perform(post("/api/v1/cuentas").contentType(MediaType.APPLICATION_JSON)
						.content(mapeador.writeValueAsString(Map.of("nombre", nombre, "contrasena", CONTRASENA))))
				.andExpect(status().isCreated());
	}

	private ResultActions iniciarSesion(String nombre, String contrasena, String ip) throws Exception {
		return mockMvc.perform(desde(post("/api/v1/sesiones"), ip)
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapeador.writeValueAsString(Map.of("nombre", nombre, "contrasena", contrasena))));
	}

	private String token(String nombre, String ip) throws Exception {
		String respuesta = iniciarSesion(nombre, CONTRASENA, ip)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return mapeador.readTree(respuesta).get("token").asText();
	}

	private ResultActions borrarCuenta(String token, String contrasena, String ip) throws Exception {
		return mockMvc.perform(desde(delete("/api/v1/cuentas/yo"), ip)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapeador.writeValueAsString(Map.of("contrasena", contrasena))));
	}

	private void fallar(String nombre, String ip, int veces) throws Exception {
		for (int i = 0; i < veces; i++) {
			iniciarSesion(nombre, INCORRECTA, ip)
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
		}
	}

	private void esperarBloqueo(ResultActions resultado, String segundos) throws Exception {
		resultado.andExpect(status().isTooManyRequests())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, segundos))
				.andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
	}

	@Test
	void cincoFallosBloqueanElSextoIntentoAunqueSeaCorrecto() throws Exception {
		registrar("Ana");
		fallar("Ana", "10.0.1.1", 5);

		esperarBloqueo(iniciarSesion("Ana", CONTRASENA, "10.0.1.1"), "300");
	}

	@Test
	void bloqueoIgnoraMayusculasDelNombre() throws Exception {
		registrar("Beatriz");
		for (int i = 1; i <= 5; i++) {
			iniciarSesion(i % 2 == 0 ? "BEATRIZ" : "beatriz", INCORRECTA, "10.0.2." + i)
					.andExpect(status().isUnauthorized());
		}

		esperarBloqueo(iniciarSesion("Beatriz", CONTRASENA, "10.0.2.9"), "300");
	}

	@Test
	void nombreInexistenteRecibeLasMismasRespuestasQueUnoReal() throws Exception {
		registrar("Bruno");
		List<String> real = new ArrayList<>();
		List<String> inexistente = new ArrayList<>();

		for (int i = 0; i < 6; i++) {
			real.add(resumen(iniciarSesion("Bruno", INCORRECTA, "10.0.3.1")));
			inexistente.add(resumen(iniciarSesion("NoExisteNadie", INCORRECTA, "10.0.3.2")));
		}

		assertThat(real).isEqualTo(inexistente);
		assertThat(real.get(4)).startsWith("401 ");
		assertThat(real.get(5)).startsWith("429 300 ");
	}

	private static String resumen(ResultActions resultado) throws Exception {
		MockHttpServletResponse respuesta = resultado.andReturn().getResponse();
		return respuesta.getStatus() + " " + respuesta.getHeader(HttpHeaders.RETRY_AFTER) + " "
				+ respuesta.getContentAsString();
	}

	@Test
	void pasadoElBloqueoSePuedeIniciarSesion() throws Exception {
		registrar("Carla");
		fallar("Carla", "10.0.4.1", 5);

		reloj.avanzar(Duration.ofSeconds(299));
		esperarBloqueo(iniciarSesion("Carla", CONTRASENA, "10.0.4.1"), "1");

		reloj.avanzar(Duration.ofSeconds(1));
		iniciarSesion("Carla", CONTRASENA, "10.0.4.1").andExpect(status().isOk());
	}

	@Test
	void inicioCorrectoAntesDelMaximoReiniciaElContador() throws Exception {
		registrar("Diego");
		for (int i = 1; i <= 4; i++) {
			iniciarSesion("Diego", INCORRECTA, "10.0.5." + i).andExpect(status().isUnauthorized());
		}
		iniciarSesion("Diego", CONTRASENA, "10.0.5.5").andExpect(status().isOk());

		for (int i = 6; i <= 9; i++) {
			iniciarSesion("Diego", INCORRECTA, "10.0.5." + i).andExpect(status().isUnauthorized());
		}
		iniciarSesion("Diego", CONTRASENA, "10.0.5.10").andExpect(status().isOk());
	}

	@Test
	void usuarioBloqueadoNoImpideQueOtroEntreDesdeOtraIp() throws Exception {
		registrar("Elena");
		registrar("Fabian");
		fallar("Elena", "10.0.6.1", 5);

		esperarBloqueo(iniciarSesion("Elena", CONTRASENA, "10.0.6.2"), "300");
		iniciarSesion("Fabian", CONTRASENA, "10.0.6.2").andExpect(status().isOk());
		esperarBloqueo(iniciarSesion("Fabian", CONTRASENA, "10.0.6.1"), "300");
	}

	@Test
	void unaIpQueFallaConVariosNombresQuedaBloqueada() throws Exception {
		registrar("Gabriela");
		for (int i = 1; i <= 5; i++) {
			iniciarSesion("intruso" + i, INCORRECTA, "10.0.7.1").andExpect(status().isUnauthorized());
		}

		esperarBloqueo(iniciarSesion("Gabriela", CONTRASENA, "10.0.7.1"), "300");
		iniciarSesion("Gabriela", CONTRASENA, "10.0.7.2").andExpect(status().isOk());
	}

	@Test
	void cabeceraXForwardedForNoEvitaElBloqueo() throws Exception {
		registrar("Hugo");
		for (int i = 1; i <= 5; i++) {
			mockMvc.perform(desde(post("/api/v1/sesiones"), "10.0.8.1")
							.header("X-Forwarded-For", "203.0.113." + i)
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapeador.writeValueAsString(Map.of("nombre", "intruso-h" + i, "contrasena", INCORRECTA))))
					.andExpect(status().isUnauthorized());
		}

		esperarBloqueo(mockMvc.perform(desde(post("/api/v1/sesiones"), "10.0.8.1")
				.header("X-Forwarded-For", "203.0.113.99")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapeador.writeValueAsString(Map.of("nombre", "Hugo", "contrasena", CONTRASENA)))), "300");
	}

	@Test
	void borrarCuentaConContrasenaIncorrectaCuentaFallos() throws Exception {
		registrar("Ivan");
		String token = token("Ivan", "10.0.9.1");

		for (int i = 0; i < 5; i++) {
			borrarCuenta(token, INCORRECTA, "10.0.9.1")
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
		}

		esperarBloqueo(borrarCuenta(token, CONTRASENA, "10.0.9.1"), "300");
		esperarBloqueo(iniciarSesion("Ivan", CONTRASENA, "10.0.9.2"), "300");
		assertThat(repositorioUsuarios.buscarPorNombre("Ivan")).isPresent();
	}

	@Test
	void fallosDeBorradoEInicioSeSumanParaElMismoUsuario() throws Exception {
		registrar("Julia");
		String token = token("Julia", "10.0.10.1");

		for (int i = 1; i <= 3; i++) {
			borrarCuenta(token, INCORRECTA, "10.0.10." + (i + 1)).andExpect(status().isUnauthorized());
		}
		iniciarSesion("Julia", INCORRECTA, "10.0.10.5").andExpect(status().isUnauthorized());
		iniciarSesion("Julia", INCORRECTA, "10.0.10.6").andExpect(status().isUnauthorized());

		esperarBloqueo(iniciarSesion("Julia", CONTRASENA, "10.0.10.7"), "300");
	}
}
