package com.brilliant.cuentas.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.brilliant.cuentas.ConfiguracionBaseDatos;
import com.zaxxer.hikari.HikariDataSource;

class RepositorioUsuariosPrueba {

	@TempDir
	Path directorioDatos;

	private HikariDataSource fuenteDatos;
	private JdbcTemplate jdbc;
	private RepositorioUsuarios repositorio;

	@BeforeEach
	void prepararBase() throws Exception {
		fuenteDatos = ConfiguracionBaseDatos.crearFuenteDatos(directorioDatos);
		new ResourceDatabasePopulator(new ClassPathResource("esquema.sql")).execute(fuenteDatos);
		jdbc = new JdbcTemplate(fuenteDatos);
		repositorio = new RepositorioUsuarios(jdbc);
	}

	@AfterEach
	void cerrarBase() {
		fuenteDatos.close();
	}

	@Test
	void creaYBuscaPorNombre() {
		Usuario creado = repositorio.crear("  Ana María ", "hash-1");

		assertThat(creado.nombre()).isEqualTo("  Ana María ");
		assertThat(creado.nombreNormalizado()).isEqualTo("ana maría");
		assertThat(creado.creadoEn()).isEqualTo(creado.actualizadoEn());
		assertThat(repositorio.buscarPorNombre("Ana María")).contains(creado);
		assertThat(repositorio.buscarPorId(creado.id())).contains(creado);
		assertThat(repositorio.contar()).isEqualTo(1);
	}

	@Test
	void nombreSeComparaSinImportarMayusculas() {
		Usuario creado = repositorio.crear("Ana", "hash-1");

		assertThat(repositorio.buscarPorNombre("ANA")).contains(creado);
		assertThatThrownBy(() -> repositorio.crear("ana", "hash-2"))
				.isInstanceOf(NombreUsuarioDuplicadoExcepcion.class);
		assertThat(repositorio.contar()).isEqualTo(1);
	}

	@Test
	void nombreConAcentoSeComparaSinImportarCodificacionNiMayusculas() {
		Usuario creado = repositorio.crear("Ñandú", "hash-1");

		assertThat(creado.nombreNormalizado()).isEqualTo("ñandú");
		assertThat(repositorio.buscarPorNombre("Ñandú")).contains(creado);
		assertThat(repositorio.buscarPorNombre("ÑANDÚ")).contains(creado);
		assertThatThrownBy(() -> repositorio.crear("ñandú", "hash-2"))
				.isInstanceOf(NombreUsuarioDuplicadoExcepcion.class);
	}

	@Test
	void nombreDuplicadoLanzaExcepcionPropia() {
		Usuario original = repositorio.crear("Luis", "hash-1");

		assertThatThrownBy(() -> repositorio.crear("Luis", "hash-2"))
				.isInstanceOf(NombreUsuarioDuplicadoExcepcion.class);
		assertThat(repositorio.buscarPorNombre("Luis")).contains(original);
	}

	@Test
	void borrarEliminaAlUsuario() {
		Usuario creado = repositorio.crear("Sofía", "hash-1");

		assertThat(repositorio.borrar(creado.id())).isTrue();
		assertThat(repositorio.buscarPorId(creado.id())).isEmpty();
		assertThat(repositorio.buscarPorNombre("Sofía")).isEmpty();
		assertThat(repositorio.contar()).isZero();
		assertThat(repositorio.borrar(creado.id())).isFalse();
	}

	@Test
	void buscarUsuarioInexistenteDaVacio() {
		assertThat(repositorio.buscarPorNombre("nadie")).isEmpty();
		assertThat(repositorio.buscarPorId(UUID.randomUUID())).isEmpty();
	}

	@Test
	void conexionAplicaClavesForaneasYEsperaPorBloqueo() {
		assertThat(jdbc.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("PRAGMA busy_timeout", Integer.class)).isEqualTo(5000);
	}

	@Test
	void tablaNoTieneColumnaDeContrasenaEnTexto() {
		assertThat(jdbc.queryForList("SELECT name FROM pragma_table_info('usuarios')", String.class))
				.containsExactlyInAnyOrder(
						"id", "nombre", "nombre_normalizado", "hash_contrasena", "creado_en", "actualizado_en");
	}
}
