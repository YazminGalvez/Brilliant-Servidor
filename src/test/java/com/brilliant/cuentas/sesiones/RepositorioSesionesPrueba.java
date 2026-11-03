package com.brilliant.cuentas.sesiones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.brilliant.cuentas.ConfiguracionBaseDatos;
import com.brilliant.cuentas.usuarios.RepositorioUsuarios;
import com.brilliant.cuentas.usuarios.Usuario;
import com.zaxxer.hikari.HikariDataSource;

class RepositorioSesionesPrueba {

	private static final Instant AHORA = Instant.parse("2026-01-01T10:00:00.750Z");

	@TempDir
	Path directorioDatos;

	private HikariDataSource fuenteDatos;
	private RepositorioUsuarios usuarios;
	private RepositorioSesiones sesiones;
	private Usuario usuario;

	@BeforeEach
	void prepararBase() throws Exception {
		fuenteDatos = ConfiguracionBaseDatos.crearFuenteDatos(directorioDatos);
		new ResourceDatabasePopulator(new ClassPathResource("esquema.sql")).execute(fuenteDatos);
		JdbcTemplate jdbc = new JdbcTemplate(fuenteDatos);
		usuarios = new RepositorioUsuarios(jdbc);
		sesiones = new RepositorioSesiones(jdbc);
		usuario = usuarios.crear("Ana", "hash-1");
	}

	@AfterEach
	void cerrarBase() {
		fuenteDatos.close();
	}

	@Test
	void creaYBuscaPorId() {
		Sesion creada = sesiones.crear(usuario.id(), AHORA, AHORA.plus(Duration.ofHours(1)));

		assertThat(creada.creadoEn()).isEqualTo(AHORA.truncatedTo(ChronoUnit.SECONDS));
		assertThat(creada.revocadoEn()).isNull();
		assertThat(sesiones.buscarPorId(creada.id())).contains(creada);
		assertThat(sesiones.buscarPorId(UUID.randomUUID())).isEmpty();
	}

	@Test
	void esValidaSoloAntesDeExpirarYSinRevocar() {
		Sesion sesion = sesiones.crear(usuario.id(), AHORA, AHORA.plusSeconds(60));

		assertThat(sesion.esValidaEn(AHORA.plusSeconds(30))).isTrue();
		assertThat(sesion.esValidaEn(sesion.expiraEn())).isFalse();

		sesiones.revocar(sesion.id(), AHORA.plusSeconds(10));
		assertThat(sesiones.buscarPorId(sesion.id()).orElseThrow().esValidaEn(AHORA.plusSeconds(30))).isFalse();
	}

	@Test
	void revocarSoloFuncionaUnaVez() {
		Sesion sesion = sesiones.crear(usuario.id(), AHORA, AHORA.plusSeconds(60));

		assertThat(sesiones.revocar(sesion.id(), AHORA.plusSeconds(5))).isTrue();
		assertThat(sesiones.revocar(sesion.id(), AHORA.plusSeconds(9))).isFalse();
		assertThat(sesiones.buscarPorId(sesion.id()).orElseThrow().revocadoEn())
				.isEqualTo(AHORA.plusSeconds(5).truncatedTo(ChronoUnit.SECONDS));
	}

	@Test
	void borraSoloLasExpiradas() {
		Sesion vencida = sesiones.crear(usuario.id(), AHORA, AHORA.plusSeconds(60));
		Sesion vigente = sesiones.crear(usuario.id(), AHORA, AHORA.plusSeconds(600));

		assertThat(sesiones.borrarExpiradas(AHORA.plusSeconds(60))).isEqualTo(1);
		assertThat(sesiones.buscarPorId(vencida.id())).isEmpty();
		assertThat(sesiones.buscarPorId(vigente.id())).isPresent();
	}

	@Test
	void borrarUsuarioBorraSusSesiones() {
		Sesion sesion = sesiones.crear(usuario.id(), AHORA, AHORA.plusSeconds(600));

		usuarios.borrar(usuario.id());

		assertThat(sesiones.buscarPorId(sesion.id())).isEmpty();
	}

	@Test
	void noPermiteSesionDeUsuarioInexistente() {
		assertThatThrownBy(() -> sesiones.crear(UUID.randomUUID(), AHORA, AHORA.plusSeconds(60)))
				.isInstanceOf(DataAccessException.class);
	}
}
