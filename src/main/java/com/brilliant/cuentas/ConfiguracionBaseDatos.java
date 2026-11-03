package com.brilliant.cuentas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class ConfiguracionBaseDatos {

	public static final String NOMBRE_ARCHIVO = "cuentas.db";
	private static final int ESPERA_BLOQUEO_MS = 5000;

	@Bean
	public HikariDataSource fuenteDatos(@Value("${cuentas.directorio-datos}") Path directorioDatos)
			throws IOException {
		return crearFuenteDatos(directorioDatos);
	}

	public static HikariDataSource crearFuenteDatos(Path directorioDatos) throws IOException {
		Files.createDirectories(directorioDatos);

		SQLiteConfig sqlite = new SQLiteConfig();
		sqlite.enforceForeignKeys(true);
		sqlite.setBusyTimeout(ESPERA_BLOQUEO_MS);

		HikariConfig hikari = new HikariConfig();
		hikari.setPoolName("cuentas");
		hikari.setJdbcUrl("jdbc:sqlite:" + directorioDatos.resolve(NOMBRE_ARCHIVO).toAbsolutePath());
		hikari.setDataSourceProperties(sqlite.toProperties());
		return new HikariDataSource(hikari);
	}
}
