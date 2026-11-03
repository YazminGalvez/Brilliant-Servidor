package com.brilliant.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.brilliant.cuentas.usuarios.RepositorioUsuarios;
import com.brilliant.cuentas.usuarios.Usuario;

class EsquemaPrueba {

	@TempDir
	Path directorioTemporal;

	private ConfigurableApplicationContext arrancar(Path directorioDatos) {
		return new SpringApplicationBuilder(ServidorCuentasAplicacion.class)
				.run("--cuentas.directorio-datos=" + directorioDatos, "--server.port=0");
	}

	@Test
	void esquemaSeCreaAlArrancarYConservaDatosAlReiniciar() {
		Path directorioDatos = directorioTemporal.resolve("datos");
		Usuario creado;

		try (ConfigurableApplicationContext contexto = arrancar(directorioDatos)) {
			creado = contexto.getBean(RepositorioUsuarios.class).crear("Ana", "hash-1");
		}

		assertThat(directorioDatos.resolve(ConfiguracionBaseDatos.NOMBRE_ARCHIVO)).isRegularFile();

		try (ConfigurableApplicationContext contexto = arrancar(directorioDatos)) {
			RepositorioUsuarios repositorio = contexto.getBean(RepositorioUsuarios.class);
			assertThat(repositorio.contar()).isEqualTo(1);
			assertThat(repositorio.buscarPorId(creado.id())).contains(creado);
		}
	}
}
