package com.brilliant.cuentas.sesiones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.brilliant.cuentas.ConfiguracionRelojPrueba;
import com.brilliant.cuentas.RelojPrueba;
import com.brilliant.cuentas.seguridad.ConfiguracionTokens;
import com.brilliant.cuentas.usuarios.RepositorioUsuarios;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@SpringBootTest(properties = {
		"logging.level.org.springframework.security=DEBUG",
		"logging.level.org.springframework.web=DEBUG",
		"logging.level.com.brilliant=DEBUG" })
@AutoConfigureMockMvc
@DirtiesContext
@Import(ConfiguracionRelojPrueba.class)
@ExtendWith(OutputCaptureExtension.class)
class SesionesPrueba {

	private static final String CONTRASENA = "Clave-segura-91";

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

	@Autowired
	private JwtEncoder codificadorTokens;

	@Autowired
	private JdbcTemplate jdbc;

	private String json(Object valor) throws Exception {
		return mapeador.writeValueAsString(valor);
	}

	private void registrar(String nombre) throws Exception {
		mockMvc.perform(post("/api/v1/cuentas").contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("nombre", nombre, "contrasena", CONTRASENA))))
				.andExpect(status().isCreated());
	}

	private ResultActions iniciarSesion(String nombre, String contrasena) throws Exception {
		return mockMvc.perform(post("/api/v1/sesiones").contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("nombre", nombre, "contrasena", contrasena))));
	}

	private String token(String nombre) throws Exception {
		String respuesta = iniciarSesion(nombre, CONTRASENA)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return mapeador.readTree(respuesta).get("token").asText();
	}

	private ResultActions consultarCuenta(String token) throws Exception {
		return mockMvc.perform(get("/api/v1/cuentas/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions borrarCuenta(String token, String contrasena) throws Exception {
		return mockMvc.perform(delete("/api/v1/cuentas/yo")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("contrasena", contrasena))));
	}

	private JsonNode claims(String token) throws Exception {
		return mapeador.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
	}

	private String firmar(JwtEncoder codificador, String emisor, String audiencia, JsonNode claimsOriginales) {
		Instant ahora = reloj.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(emisor)
				.audience(List.of(audiencia))
				.subject(claimsOriginales.get("sub").asText())
				.claim(ConfiguracionTokens.CLAIM_SESION, claimsOriginales.get("sid").asText())
				.issuedAt(ahora)
				.expiresAt(ahora.plusSeconds(600))
				.build();
		return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	private void esperarNoAutorizado(ResultActions resultado) throws Exception {
		resultado.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
				.andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
	}

	private long sesionesDe(UUID usuarioId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM sesiones WHERE usuario_id = ?", Long.class,
				usuarioId.toString());
	}

	@Test
	void inicioSesionCorrectoDevuelveTokenQueAbreLaCuenta() throws Exception {
		registrar("Ana");
		UUID id = repositorioUsuarios.buscarPorNombre("Ana").orElseThrow().id();

		String respuesta = iniciarSesion("ana", CONTRASENA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.expiraEnSegundos").value(3600))
				.andExpect(jsonPath("$.usuario.id").value(id.toString()))
				.andExpect(jsonPath("$.usuario.nombre").value("Ana"))
				.andReturn().getResponse().getContentAsString();
		String token = mapeador.readTree(respuesta).get("token").asText();

		JsonNode claims = claims(token);
		assertThat(claims.get("iss").asText()).isEqualTo("brilliant-cuentas");
		assertThat(claims.get("aud").toString()).contains("brilliant-juego");
		assertThat(claims.get("sub").asText()).isEqualTo(id.toString());
		assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(3600);
		assertThat(respuesta).doesNotContain(CONTRASENA, "argon2");

		consultarCuenta(token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.*", hasSize(2)))
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.nombre").value("Ana"));
	}

	@Test
	void contrasenaIncorrectaYUsuarioInexistenteDanElMismo401() throws Exception {
		registrar("Bruno");

		String incorrecta = iniciarSesion("Bruno", "Otra-clave-segura-1")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
				.andReturn().getResponse().getContentAsString();
		String inexistente = iniciarSesion("NadieExiste", "Otra-clave-segura-1")
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		assertThat(inexistente).isEqualTo(incorrecta);
	}

	@Test
	void faltanCamposAlIniciarSesionResponde400() throws Exception {
		mockMvc.perform(post("/api/v1/sesiones").contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Ana\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CAMPOS_FALTANTES"));
	}

	@Test
	void sinTokenOTokenBasuraResponde401() throws Exception {
		esperarNoAutorizado(mockMvc.perform(get("/api/v1/cuentas/yo")));
		esperarNoAutorizado(consultarCuenta("esto-no-es-un-token"));
		esperarNoAutorizado(mockMvc.perform(delete("/api/v1/sesiones/actual")));
	}

	@Test
	void tokenManipuladoResponde401() throws Exception {
		registrar("Carla");
		registrar("Diego");
		String token = token("Carla");
		String otroUsuario = repositorioUsuarios.buscarPorNombre("Diego").orElseThrow().id().toString();
		String[] partes = token.split("\\.");

		String cargaOriginal = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
		String cargaAlterada = cargaOriginal.replace(claims(token).get("sub").asText(), otroUsuario);
		String conOtraCarga = partes[0] + "."
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(cargaAlterada.getBytes(StandardCharsets.UTF_8))
				+ "." + partes[2];
		esperarNoAutorizado(consultarCuenta(conOtraCarga));

		char[] firma = partes[2].toCharArray();
		firma[5] = firma[5] == 'A' ? 'B' : 'A';
		esperarNoAutorizado(consultarCuenta(partes[0] + "." + partes[1] + "." + new String(firma)));

		String sinFirma = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "." + partes[1] + ".";
		esperarNoAutorizado(consultarCuenta(sinFirma));

		consultarCuenta(token).andExpect(status().isOk());
	}

	@Test
	void tokenFirmadoConOtraClaveResponde401() throws Exception {
		registrar("Elena");
		JsonNode claims = claims(token("Elena"));

		byte[] otraClave = new byte[32];
		new SecureRandom().nextBytes(otraClave);
		JwtEncoder otroCodificador = new NimbusJwtEncoder(
				new ImmutableSecret<>(new SecretKeySpec(otraClave, "HmacSHA256")));

		esperarNoAutorizado(consultarCuenta(firmar(otroCodificador, "brilliant-cuentas", "brilliant-juego", claims)));
		consultarCuenta(firmar(codificadorTokens, "brilliant-cuentas", "brilliant-juego", claims))
				.andExpect(status().isOk());
	}

	@Test
	void tokenConOtroEmisorUOtraAudienciaResponde401() throws Exception {
		registrar("Fabian");
		JsonNode claims = claims(token("Fabian"));

		esperarNoAutorizado(consultarCuenta(firmar(codificadorTokens, "otro-emisor", "brilliant-juego", claims)));
		esperarNoAutorizado(consultarCuenta(firmar(codificadorTokens, "brilliant-cuentas", "otro-juego", claims)));
	}

	@Test
	void tokenExpiradoResponde401() throws Exception {
		registrar("Gabriela");
		String token = token("Gabriela");
		consultarCuenta(token).andExpect(status().isOk());

		reloj.avanzar(Duration.ofSeconds(3599));
		consultarCuenta(token).andExpect(status().isOk());

		reloj.avanzar(Duration.ofSeconds(2));
		esperarNoAutorizado(consultarCuenta(token));
	}

	@Test
	void cerrarSesionRevocaSoloEseToken() throws Exception {
		registrar("Hugo");
		String token = token("Hugo");
		String otroToken = token("Hugo");

		mockMvc.perform(delete("/api/v1/sesiones/actual").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNoContent());

		esperarNoAutorizado(consultarCuenta(token));
		esperarNoAutorizado(mockMvc.perform(
				delete("/api/v1/sesiones/actual").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
		consultarCuenta(otroToken).andExpect(status().isOk());
	}

	@Test
	void borrarCuentaConContrasenaCorrectaEliminaUsuarioYSesiones() throws Exception {
		registrar("Ivan");
		UUID id = repositorioUsuarios.buscarPorNombre("Ivan").orElseThrow().id();
		String token = token("Ivan");
		String otroToken = token("Ivan");
		assertThat(sesionesDe(id)).isEqualTo(2);

		borrarCuenta(token, CONTRASENA).andExpect(status().isNoContent());

		assertThat(repositorioUsuarios.buscarPorId(id)).isEmpty();
		assertThat(sesionesDe(id)).isZero();
		esperarNoAutorizado(consultarCuenta(token));
		esperarNoAutorizado(consultarCuenta(otroToken));
		iniciarSesion("Ivan", CONTRASENA)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
	}

	@Test
	void borrarCuentaConContrasenaIncorrectaNoBorraNada() throws Exception {
		registrar("Julia");
		UUID id = repositorioUsuarios.buscarPorNombre("Julia").orElseThrow().id();
		String token = token("Julia");

		borrarCuenta(token, "Otra-clave-segura-1")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

		assertThat(repositorioUsuarios.buscarPorId(id)).isPresent();
		assertThat(sesionesDe(id)).isEqualTo(1);
		consultarCuenta(token).andExpect(status().isOk());
	}

	@Test
	void borrarCuentaSinContrasenaResponde400() throws Exception {
		registrar("Karla");
		mockMvc.perform(delete("/api/v1/cuentas/yo")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token("Karla"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CAMPOS_FALTANTES"));
	}

	@Test
	void rutasPublicasNoExigenToken() throws Exception {
		mockMvc.perform(get("/api/v1/salud")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/salud").header(HttpHeaders.AUTHORIZATION, "Bearer token-vencido"))
				.andExpect(status().isOk());
		registrar("Luis");
		iniciarSesion("Luis", CONTRASENA).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/sesiones").header(HttpHeaders.AUTHORIZATION, "Bearer token-vencido")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("nombre", "Luis", "contrasena", CONTRASENA))))
				.andExpect(status().isOk());
	}

	@Test
	void rutaInexistenteConTokenResponde404() throws Exception {
		registrar("Marta");
		mockMvc.perform(get("/api/v1/no-existe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("Marta")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
	}

	@Test
	void respuestasNoCreanSesionHttpNiCookies() throws Exception {
		registrar("Nora");
		iniciarSesion("Nora", CONTRASENA)
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
				.andExpect(header().exists("X-Content-Type-Options"));
	}

	@Test
	void tokenContrasenaYHashNoAparecenEnLosLogs(CapturedOutput salida) throws Exception {
		registrar("Oscar");
		String token = token("Oscar");
		consultarCuenta(token).andExpect(status().isOk());
		iniciarSesion("Oscar", "Otra-clave-segura-1").andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/sesiones/actual").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNoContent());
		esperarNoAutorizado(consultarCuenta(token));
		String hash = repositorioUsuarios.buscarPorNombre("Oscar").orElseThrow().hashContrasena();

		assertThat(salida.getAll()).contains("Securing GET /api/v1/cuentas/yo");
		assertThat(salida.getAll()).doesNotContain(token, token.split("\\.")[2], CONTRASENA,
				"Otra-clave-segura-1", hash, "$argon2id$");
	}

	@Test
	void corsPermiteLocalhostEnDesarrollo() throws Exception {
		mockMvc.perform(options("/api/v1/sesiones")
						.header(HttpHeaders.ORIGIN, "http://localhost:5555")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5555"));

		mockMvc.perform(get("/api/v1/salud").header(HttpHeaders.ORIGIN, "http://127.0.0.1:8081"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:8081"));

		mockMvc.perform(options("/api/v1/sesiones")
						.header(HttpHeaders.ORIGIN, "https://sitio-ajeno.ejemplo")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}
}
