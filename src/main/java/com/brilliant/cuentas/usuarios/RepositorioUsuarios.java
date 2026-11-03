package com.brilliant.cuentas.usuarios;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioUsuarios {

	private static final RowMapper<Usuario> MAPEADOR = (fila, numero) -> new Usuario(
			UUID.fromString(fila.getString("id")),
			fila.getString("nombre"),
			fila.getString("nombre_normalizado"),
			fila.getString("hash_contrasena"),
			Instant.parse(fila.getString("creado_en")),
			Instant.parse(fila.getString("actualizado_en")));

	private final JdbcTemplate jdbc;

	public RepositorioUsuarios(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public static String normalizarNombre(String nombre) {
		return nombre.strip().toLowerCase(Locale.ROOT);
	}

	public Usuario crear(String nombre, String hashContrasena) {
		Instant ahora = Instant.now();
		Usuario usuario = new Usuario(
				UUID.randomUUID(), nombre, normalizarNombre(nombre), hashContrasena, ahora, ahora);

		int filas = jdbc.update("""
				INSERT INTO usuarios (id, nombre, nombre_normalizado, hash_contrasena, creado_en, actualizado_en)
				VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (nombre_normalizado) DO NOTHING
				""",
				usuario.id().toString(),
				usuario.nombre(),
				usuario.nombreNormalizado(),
				usuario.hashContrasena(),
				usuario.creadoEn().toString(),
				usuario.actualizadoEn().toString());

		if (filas == 0) {
			throw new NombreUsuarioDuplicadoExcepcion(nombre);
		}
		return usuario;
	}

	public Optional<Usuario> buscarPorNombre(String nombre) {
		return jdbc.query("SELECT * FROM usuarios WHERE nombre_normalizado = ?", MAPEADOR,
				normalizarNombre(nombre)).stream().findFirst();
	}

	public Optional<Usuario> buscarPorId(UUID id) {
		return jdbc.query("SELECT * FROM usuarios WHERE id = ?", MAPEADOR, id.toString())
				.stream().findFirst();
	}

	public boolean borrar(UUID id) {
		return jdbc.update("DELETE FROM usuarios WHERE id = ?", id.toString()) > 0;
	}

	public long contar() {
		Long total = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Long.class);
		return total == null ? 0 : total;
	}
}
