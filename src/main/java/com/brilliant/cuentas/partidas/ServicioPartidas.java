package com.brilliant.cuentas.partidas;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.brilliant.cuentas.ServicioCuentas;
import com.brilliant.cuentas.errores.CodigoError;
import com.brilliant.cuentas.errores.SolicitudInvalidaExcepcion;

/**
 * Las salas viven solo en memoria: se pierden al reiniciar el servidor.
 */
@Service
public class ServicioPartidas {

	public static final Duration TOLERANCIA_CONEXION = Duration.ofSeconds(15);
	private static final Set<Integer> NUMEROS_TABLERO = Set.of(1, 2, 3, 4, 5, 6);
	// El tablero es de 7x7: no puede haber más zonas que celdas.
	private static final int ZONA_MAXIMA = 49;
	private static final String FIN_SIN_TABLEROS = "sin_tableros";

	private final Map<String, Sala> salas = new HashMap<>();
	private final Map<UUID, String> salaPorUsuario = new HashMap<>();
	private final ServicioCuentas servicioCuentas;
	private final LimitadorCodigos limitador;
	private final CodigoSala generadorCodigos;
	private final LanzadorDados dados;
	private final Clock reloj;
	private final Duration limiteSinContacto;

	public ServicioPartidas(ServicioCuentas servicioCuentas, LimitadorCodigos limitador, CodigoSala generadorCodigos,
			LanzadorDados dados, Clock reloj, @Value("${cuentas.room-idle-seconds}") long segundosSinContacto) {
		if (segundosSinContacto <= 0) {
			throw new IllegalStateException("SEGUNDOS_SIN_CONTACTO_SALA debe ser mayor que cero");
		}
		this.servicioCuentas = servicioCuentas;
		this.limitador = limitador;
		this.generadorCodigos = generadorCodigos;
		this.dados = dados;
		this.reloj = reloj;
		this.limiteSinContacto = Duration.ofSeconds(segundosSinContacto);
	}

	public RespuestaSala crear(UUID usuarioId, Integer mapa, Integer jugadores) {
		if (mapa == null || jugadores == null || mapa < 1 || mapa > 4 || jugadores < 2 || jugadores > 6) {
			throw new SolicitudInvalidaExcepcion(CodigoError.DATOS_INVALIDOS);
		}
		String nombre = servicioCuentas.obtenerCuenta(usuarioId).nombre();

		synchronized (this) {
			atender(usuarioId);
			comprobarQueNoEstaEnPartidaComenzada(usuarioId);
			sacarDeSalaActual(usuarioId);
			String codigo;
			do {
				codigo = generadorCodigos.generar();
			} while (salas.containsKey(codigo));

			Instant ahora = reloj.instant();
			Sala sala = new Sala(codigo, mapa, jugadores, new JugadorEnSala(usuarioId, nombre, ahora));
			salas.put(codigo, sala);
			salaPorUsuario.put(usuarioId, codigo);
			return RespuestaSala.de(sala, usuarioId, ahora, TOLERANCIA_CONEXION);
		}
	}

	public RespuestaSala unirse(UUID usuarioId, String codigo) {
		if (codigo == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.DATOS_INVALIDOS);
		}
		limitador.comprobar(usuarioId);
		String nombre = servicioCuentas.obtenerCuenta(usuarioId).nombre();

		synchronized (this) {
			atender(usuarioId);
			Sala sala = salas.get(CodigoSala.normalizar(codigo));
			if (sala == null) {
				limitador.registrarFallo(usuarioId);
				throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_ENCONTRADA);
			}

			Instant ahora = reloj.instant();
			JugadorEnSala existente = sala.jugador(usuarioId);
			if (existente != null) {
				existente.registrarSenal(ahora);
			} else {
				comprobarQueNoEstaEnPartidaComenzada(usuarioId);
				if (sala.comenzada()) {
					throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_COMENZADA);
				}
				if (sala.estaLlena()) {
					throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_LLENA);
				}
				sacarDeSalaActual(usuarioId);
				sala.agregar(new JugadorEnSala(usuarioId, nombre, ahora));
				salaPorUsuario.put(usuarioId, sala.codigo());
			}
			limitador.reiniciar(usuarioId);
			return RespuestaSala.de(sala, usuarioId, ahora, TOLERANCIA_CONEXION);
		}
	}

	public synchronized Optional<RespuestaSala> actual(UUID usuarioId) {
		atender(usuarioId);
		String codigo = salaPorUsuario.get(usuarioId);
		if (codigo == null) {
			return Optional.empty();
		}
		return Optional.of(consultarComoJugador(salas.get(codigo), usuarioId));
	}

	public synchronized RespuestaSala consultar(UUID usuarioId, String codigo) {
		atender(usuarioId);
		return consultarComoJugador(salas.get(CodigoSala.normalizar(codigo)), usuarioId);
	}

	public synchronized RespuestaSala marcarListo(UUID usuarioId, String codigo, Boolean listo) {
		if (listo == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.DATOS_INVALIDOS);
		}
		atender(usuarioId);
		Sala sala = salas.get(CodigoSala.normalizar(codigo));
		if (sala != null && sala.jugador(usuarioId) != null && !sala.comenzada()) {
			sala.marcarListo(usuarioId, listo);
		}
		return consultarComoJugador(sala, usuarioId);
	}

	public synchronized RespuestaSala enviarTablero(UUID usuarioId, String codigo, List<Integer> numeros) {
		atender(usuarioId);
		Sala sala = salaDeJugador(usuarioId, codigo);
		if (numeros == null || numeros.size() != NUMEROS_TABLERO.size()
				|| !new HashSet<>(numeros).equals(NUMEROS_TABLERO)) {
			throw new SolicitudInvalidaExcepcion(CodigoError.DATOS_INVALIDOS);
		}
		if (!sala.comenzada()) {
			throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_COMENZADA);
		}
		JugadorEnSala jugador = sala.jugador(usuarioId);
		if (!jugador.tableroListo()) {
			jugador.fijarTablero(numeros);
			avanzarSiCorresponde(sala);
		}
		return consultarComoJugador(sala, usuarioId);
	}

	public synchronized RespuestaSala terminarTurno(UUID usuarioId, String codigo, Integer ronda,
			Boolean tableroLleno, Integer zona) {
		atender(usuarioId);
		Sala sala = salaDeJugador(usuarioId, codigo);
		if (ronda == null || tableroLleno == null || (zona != null && (zona < 1 || zona > ZONA_MAXIMA))) {
			throw new SolicitudInvalidaExcepcion(CodigoError.DATOS_INVALIDOS);
		}
		if (!sala.comenzada()) {
			throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_COMENZADA);
		}
		sala.registrarTurno(usuarioId, ronda, tableroLleno, zona);
		avanzarSiCorresponde(sala);
		return consultarComoJugador(sala, usuarioId);
	}

	public synchronized void salir(UUID usuarioId, String codigo) {
		atender(usuarioId);
		salaDeJugador(usuarioId, codigo);
		sacarDeSalaActual(usuarioId);
	}

	private Sala salaDeJugador(UUID usuarioId, String codigo) {
		Sala sala = salas.get(CodigoSala.normalizar(codigo));
		if (sala == null || sala.jugador(usuarioId) == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_ENCONTRADA);
		}
		return sala;
	}

	private void avanzarSiCorresponde(Sala sala) {
		if (!sala.comenzada() || sala.terminada() || sala.jugadores().isEmpty()) {
			return;
		}
		if (sala.ronda() == 0) {
			if (sala.todosTienenTablero()) {
				sala.comenzarRonda(dados.lanzar(), dados.lanzar());
			}
			return;
		}
		if (!sala.todosTerminaronLaRonda()) {
			return;
		}
		if (sala.todosLlenaronSuTablero()) {
			sala.terminar(FIN_SIN_TABLEROS);
		} else {
			sala.comenzarRonda(dados.lanzar(), dados.lanzar());
		}
	}

	private void atender(UUID usuarioId) {
		expulsarSinContacto();
		String codigo = salaPorUsuario.get(usuarioId);
		if (codigo != null) {
			salas.get(codigo).jugador(usuarioId).registrarSenal(reloj.instant());
		}
	}

	private void expulsarSinContacto() {
		Instant ahora = reloj.instant();
		List<UUID> ausentes = salas.values().stream()
				.filter(sala -> !sala.comenzada())
				.flatMap(sala -> sala.jugadores().stream())
				.filter(jugador -> !jugador.conectadoEn(ahora, limiteSinContacto))
				.map(JugadorEnSala::usuarioId)
				.toList();
		ausentes.forEach(this::sacarDeSalaActual);
	}

	private void comprobarQueNoEstaEnPartidaComenzada(UUID usuarioId) {
		String codigo = salaPorUsuario.get(usuarioId);
		Sala sala = codigo == null ? null : salas.get(codigo);
		if (sala != null && sala.comenzada() && !sala.terminada()) {
			throw new SolicitudInvalidaExcepcion(CodigoError.YA_EN_PARTIDA);
		}
	}

	private RespuestaSala consultarComoJugador(Sala sala, UUID usuarioId) {
		JugadorEnSala jugador = sala == null ? null : sala.jugador(usuarioId);
		if (jugador == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_ENCONTRADA);
		}
		Instant ahora = reloj.instant();
		jugador.registrarSenal(ahora);
		return RespuestaSala.de(sala, usuarioId, ahora, TOLERANCIA_CONEXION);
	}

	private void sacarDeSalaActual(UUID usuarioId) {
		String codigo = salaPorUsuario.remove(usuarioId);
		Sala sala = codigo == null ? null : salas.get(codigo);
		if (sala == null) {
			return;
		}
		if (sala.esAnfitrion(usuarioId) && !sala.comenzada()) {
			cerrar(sala);
			return;
		}
		sala.quitar(usuarioId);
		if (sala.jugadores().isEmpty()) {
			salas.remove(codigo);
		} else {
			avanzarSiCorresponde(sala);
		}
	}

	private void cerrar(Sala sala) {
		salas.remove(sala.codigo());
		for (JugadorEnSala jugador : sala.jugadores()) {
			salaPorUsuario.remove(jugador.usuarioId(), sala.codigo());
		}
	}
}
