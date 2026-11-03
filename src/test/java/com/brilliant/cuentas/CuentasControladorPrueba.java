package com.brilliant.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.brilliant.cuentas.usuarios.RepositorioUsuarios;
import com.brilliant.cuentas.usuarios.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
		"logging.level.org.springframework.web=DEBUG",
		"logging.level.com.brilliant=DEBUG" })
@AutoConfigureMockMvc
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class CuentasControladorPrueba {

	private static final String RUTA = "/api/v1/cuentas";

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
	private RepositorioUsuarios repositorio;

	@Autowired
	private PasswordEncoder codificador;

	private ResultActions crear(String nombre, String contrasena) throws Exception {
		Map<String, String> cuerpo = new HashMap<>();
		cuerpo.put("nombre", nombre);
		cuerpo.put("contrasena", contrasena);
		return enviar(mapeador.writeValueAsString(cuerpo));
	}

	private ResultActions enviar(String cuerpo) throws Exception {
		return mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private Usuario guardado(String nombre) {
		return repositorio.buscarPorNombre(nombre).orElseThrow();
	}

	@Test
	void creaCuentaYRespondeSinContrasenaNiHash() throws Exception {
		String respuesta = crear("Ana_1", "Clave-segura-91")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.*", hasSize(2)))
				.andExpect(jsonPath("$.nombre").value("Ana_1"))
				.andReturn().getResponse().getContentAsString();

		assertThat(mapeador.readTree(respuesta).get("id").asText()).isEqualTo(guardado("Ana_1").id().toString());
		assertThat(respuesta).doesNotContain("Clave-segura-91", "argon2", "contrasena", "hash");
	}

	@Test
	void guardaHashArgon2idDistintoDeLaContrasena() throws Exception {
		crear("Bruno", "Clave-segura-91").andExpect(status().isCreated());

		String hash = guardado("Bruno").hashContrasena();
		assertThat(hash).startsWith("$argon2id$").isNotEqualTo("Clave-segura-91");
		assertThat(codificador.matches("Clave-segura-91", hash)).isTrue();
	}

	@Test
	void mismaContrasenaGeneraHashesDistintos() throws Exception {
		crear("Carla", "Misma-clave-77").andExpect(status().isCreated());
		crear("Diego", "Misma-clave-77").andExpect(status().isCreated());

		assertThat(guardado("Carla").hashContrasena()).isNotEqualTo(guardado("Diego").hashContrasena());
	}

	@Test
	void recortaEspaciosDelNombre() throws Exception {
		crear("  Elena  ", "Clave-segura-91")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value("Elena"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Ñoño", "José_Pérez", "Güero-99", "ÁÉÍÓÚÜÑ" })
	void aceptaEnieYAcentos(String nombre) throws Exception {
		crear(nombre, "Clave-segura-91")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value(nombre));
	}

	@Test
	void nombreConAcentoEnOtraCodificacionResponde409() throws Exception {
		crear("María", "Clave-segura-91").andExpect(status().isCreated());

		crear("María", "Clave-segura-91")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("NOMBRE_EN_USO"));
		crear("MARÍA", "Clave-segura-91")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("NOMBRE_EN_USO"));
	}

	@Test
	void nombreRepetidoConOtraCapitalizacionResponde409() throws Exception {
		crear("Pedro", "Clave-segura-91").andExpect(status().isCreated());

		crear("pEDRO", "Otra-clave-segura-5")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("NOMBRE_EN_USO"));
		assertThat(guardado("pedro").nombre()).isEqualTo("Pedro");
	}

	static Stream<Arguments> datosInvalidos() {
		return Stream.of(
				Arguments.of("ab", "Clave-segura-91", "NOMBRE_INVALIDO"),
				Arguments.of("a".repeat(33), "Clave-segura-91", "NOMBRE_INVALIDO"),
				Arguments.of("con espacio", "Clave-segura-91", "NOMBRE_INVALIDO"),
				Arguments.of("hola!", "Clave-segura-91", "NOMBRE_INVALIDO"),
				Arguments.of("   ", "Clave-segura-91", "NOMBRE_INVALIDO"),
				Arguments.of("Иван", "Clave-segura-91", "NOMBRE_INVALIDO"),
				Arguments.of("Françoise", "Clave-segura-91", "NOMBRE_INVALIDO"),
				Arguments.of("ñ".repeat(33), "Clave-segura-91", "NOMBRE_INVALIDO"),
				Arguments.of("Íñigo", "íñigo", "CONTRASENA_LONGITUD_INVALIDA"),
				Arguments.of("Begoña2024", "BEGOÑA2024", "CONTRASENA_IGUAL_AL_NOMBRE"),
				Arguments.of("Fabian", "corta1", "CONTRASENA_LONGITUD_INVALIDA"),
				Arguments.of("Fabian", "x".repeat(129), "CONTRASENA_LONGITUD_INVALIDA"),
				Arguments.of("Fabian", "PASSWORD123", "CONTRASENA_COMUN"),
				Arguments.of("Fabian", "Contraseña123", "CONTRASENA_COMUN"),
				Arguments.of("Gabriela2024", "gabriela2024", "CONTRASENA_IGUAL_AL_NOMBRE"),
				Arguments.of(null, "Clave-segura-91", "CAMPOS_FALTANTES"),
				Arguments.of("Fabian", null, "CAMPOS_FALTANTES"));
	}

	@ParameterizedTest
	@MethodSource("datosInvalidos")
	void datosInvalidosResponden400(String nombre, String contrasena, String codigo) throws Exception {
		crear(nombre, contrasena)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value(codigo));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "null", "{", "no es json", "[1, 2]", "{\"nombre\": [\"x\"]}" })
	void cuerpoInvalidoResponde400(String cuerpo) throws Exception {
		enviar(cuerpo)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));
	}

	@Test
	void cuerpoDemasiadoGrandeResponde413() throws Exception {
		crear("Hugo", "x".repeat(5000))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.codigo").value("CUERPO_DEMASIADO_GRANDE"));
	}

	@Test
	void tipoDeContenidoNoSoportadoResponde415() throws Exception {
		mockMvc.perform(post(RUTA).contentType(MediaType.TEXT_PLAIN).content("hola"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.codigo").value("TIPO_CONTENIDO_NO_SOPORTADO"));
	}

	@Test
	void rutaInexistenteSinTokenResponde401() throws Exception {
		mockMvc.perform(get("/api/v1/no-existe"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
	}

	@Test
	void contrasenaYHashNoAparecenEnLosLogs(CapturedOutput salida) throws Exception {
		crear("Ivan", "Secreto-unico-4821").andExpect(status().isCreated());
		crear("ivan", "Secreto-unico-4821").andExpect(status().isConflict());
		crear("Julia", "Secreto-unico-4821").andExpect(status().isCreated());
		String hash = guardado("Ivan").hashContrasena();

		assertThat(salida.getAll()).contains("SolicitudCrearCuenta[nombre=Ivan, contrasena=****]");
		assertThat(salida.getAll()).doesNotContain("Secreto-unico-4821", hash, "$argon2id$");
	}
}
