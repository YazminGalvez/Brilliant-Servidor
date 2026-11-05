package com.brilliant.cuentas.partidas;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.brilliant.cuentas.ServicioCuentas;
import com.brilliant.cuentas.errores.CodigoError;
import com.brilliant.cuentas.errores.SolicitudInvalidaExcepcion;

/**
 * Las salas viven solo en memoria: se pierden al reiniciar el servidor.
 */
@Service
public class ServicioPartidas {

	private static final Duration TOLERANCIA_CONEXION = Duration.ofSeconds(15);

	private final Map<String, Sala> salas = new HashMap<>();
	private final Map<UUID, String> salaPorUsuario = new HashMap<>();
	private final ServicioCuentas servicioCuentas;
	private final CodigoSala generadorCodigos;
	private final Clock reloj;

	public ServicioPartidas(ServicioCuentas servicioCuentas, CodigoSala generadorCodigos, Clock reloj) {
		this.servicioCuentas = servicioCuentas;
		this.generadorCodigos = generadorCodigos;
		this.reloj = reloj;
	}

	public RespuestaSala crear(UUID usuarioId, Integer mapa, Integer jugadores) {
		if (mapa == null || jugadores == null || mapa < 1 || mapa > 4 || jugadores < 2 || jugadores > 6) {
			throw new SolicitudInvalidaExcepcion(CodigoError.DATOS_INVALIDOS);
		}
		String nombre = servicioCuentas.obtenerCuenta(usuarioId).nombre();

		synchronized (this) {
			sacarDeSalaActual(usuarioId);
			String codigo;
			do {
				codigo = generadorCodigos.generar();
			} while (salas.containsKey(codigo));

			Instant ahora = reloj.instant();
			Sala sala = new Sala(codigo, mapa, jugadores, usuarioId);
			sala.agregar(new JugadorEnSala(usuarioId, nombre, ahora));
			salas.put(codigo, sala);
			salaPorUsuario.put(usuarioId, codigo);
			return RespuestaSala.de(sala, ahora, TOLERANCIA_CONEXION);
		}
	}

	public synchronized Optional<RespuestaSala> actual(UUID usuarioId) {
		String codigo = salaPorUsuario.get(usuarioId);
		if (codigo == null) {
			return Optional.empty();
		}
		return Optional.of(consultarComoJugador(salas.get(codigo), usuarioId));
	}

	public synchronized RespuestaSala consultar(UUID usuarioId, String codigo) {
		return consultarComoJugador(salas.get(CodigoSala.normalizar(codigo)), usuarioId);
	}

	private RespuestaSala consultarComoJugador(Sala sala, UUID usuarioId) {
		JugadorEnSala jugador = sala == null ? null : sala.jugador(usuarioId);
		if (jugador == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_ENCONTRADA);
		}
		Instant ahora = reloj.instant();
		jugador.registrarSenal(ahora);
		return RespuestaSala.de(sala, ahora, TOLERANCIA_CONEXION);
	}

	private void sacarDeSalaActual(UUID usuarioId) {
		String codigo = salaPorUsuario.remove(usuarioId);
		if (codigo != null) {
			salas.remove(codigo);
		}
	}
}
