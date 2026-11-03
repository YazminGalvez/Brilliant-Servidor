package com.brilliant.cuentas.sesiones;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioSesiones {

	private static final RowMapper<Sesion> MAPEADOR = (fila, numero) -> new Sesion(
			UUID.fromString(fila.getString("id")),
			UUID.fromString(fila.getString("usuario_id")),
			Instant.parse(fila.getString("creado_en")),
			Instant.parse(fila.getString("expira_en")),
			fila.getString("revocado_en") == null ? null : Instant.parse(fila.getString("revocado_en")));

	private final JdbcTemplate jdbc;

	public RepositorioSesiones(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	private static Instant aSegundos(Instant momento) {
		return momento.truncatedTo(ChronoUnit.SECONDS);
	}

	public Sesion crear(UUID usuarioId, Instant creadoEn, Instant expiraEn) {
		Sesion sesion = new Sesion(UUID.randomUUID(), usuarioId, aSegundos(creadoEn), aSegundos(expiraEn), null);
		jdbc.update("INSERT INTO sesiones (id, usuario_id, creado_en, expira_en) VALUES (?, ?, ?, ?)",
				sesion.id().toString(),
				sesion.usuarioId().toString(),
				sesion.creadoEn().toString(),
				sesion.expiraEn().toString());
		return sesion;
	}

	public Optional<Sesion> buscarPorId(UUID id) {
		return jdbc.query("SELECT * FROM sesiones WHERE id = ?", MAPEADOR, id.toString())
				.stream().findFirst();
	}

	public boolean revocar(UUID id, Instant momento) {
		return jdbc.update("UPDATE sesiones SET revocado_en = ? WHERE id = ? AND revocado_en IS NULL",
				aSegundos(momento).toString(), id.toString()) > 0;
	}

	public int borrarExpiradas(Instant momento) {
		return jdbc.update("DELETE FROM sesiones WHERE expira_en <= ?", aSegundos(momento).toString());
	}
}
