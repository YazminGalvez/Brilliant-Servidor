package com.brilliant.cuentas.partidas;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

	private static final Set<Integer> NUMEROS_TABLERO = Set.of(1, 2, 3, 4, 5, 6);
	private static final String FIN_META = "meta";
	private static final String FIN_ABANDONO = "abandono";
	private static final String FIN_SIN_TABLEROS = "sin_tableros";

	private final Map<String, Sala> salas = new HashMap<>();
	private final Map<UUID, String> salaPorUsuario = new HashMap<>();
	private final ServicioCuentas servicioCuentas;
	private final LimitadorCodigos limitador;
	private final CodigoSala generadorCodigos;
	private final LanzadorDados dados;
	private final Clock reloj;
	private final TiemposSala tiempos;

	public ServicioPartidas(ServicioCuentas servicioCuentas, LimitadorCodigos limitador, CodigoSala generadorCodigos,
			LanzadorDados dados, Clock reloj, TiemposSala tiempos) {
		this.servicioCuentas = servicioCuentas;
		this.limitador = limitador;
		this.generadorCodigos = generadorCodigos;
		this.dados = dados;
		this.reloj = reloj;
		this.tiempos = tiempos;
	}

	public RespuestaSala crear(UUID usuarioId, Integer mapa, Integer jugadores) {
		if (mapa == null || jugadores == null || jugadores < 2 || jugadores > 6) {
			throw new SolicitudInvalidaExcepcion(CodigoError.DATOS_INVALIDOS);
		}
		if (!PuntosPorMapa.existe(mapa)) {
			throw new SolicitudInvalidaExcepcion(CodigoError.MAPA_NO_DISPONIBLE);
		}
		String nombre = servicioCuentas.obtenerCuenta(usuarioId).nombre();

		synchronized (this) {
			Instant ahora = atender(usuarioId);
			comprobarQueNoEstaEnPartidaComenzada(usuarioId);
			sacarDeSalaActual(usuarioId, ahora);
			String codigo;
			do {
				codigo = generadorCodigos.generar();
			} while (salas.containsKey(codigo));

			Sala sala = new Sala(codigo, mapa, jugadores, new JugadorEnSala(usuarioId, nombre), ahora);
			salas.put(codigo, sala);
			salaPorUsuario.put(usuarioId, codigo);
			return respuesta(sala, usuarioId, ahora);
		}
	}

	public RespuestaSala unirse(UUID usuarioId, String codigo) {
		if (codigo == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.DATOS_INVALIDOS);
		}
		limitador.comprobar(usuarioId);
		String nombre = servicioCuentas.obtenerCuenta(usuarioId).nombre();

		synchronized (this) {
			Instant ahora = atender(usuarioId);
			Sala sala = salas.get(CodigoSala.normalizar(codigo));
			if (sala == null) {
				limitador.registrarFallo(usuarioId);
				throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_ENCONTRADA);
			}

			JugadorEnSala existente = sala.jugador(usuarioId);
			if (existente != null) {
				sala.registrarContacto(existente, ahora);
				if (!estaVinculado(existente, sala)) {
					comprobarQueNoEstaEnPartidaComenzada(usuarioId);
					sacarDeSalaActual(usuarioId, ahora);
					salaPorUsuario.put(usuarioId, sala.codigo());
				}
			} else {
				comprobarQueNoEstaEnPartidaComenzada(usuarioId);
				if (sala.comenzada()) {
					throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_COMENZADA);
				}
				if (sala.estaLlena()) {
					throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_LLENA);
				}
				sacarDeSalaActual(usuarioId, ahora);
				sala.agregar(new JugadorEnSala(usuarioId, nombre), ahora);
				salaPorUsuario.put(usuarioId, sala.codigo());
			}
			limitador.reiniciar(usuarioId);
			return respuesta(sala, usuarioId, ahora);
		}
	}

	public synchronized Optional<RespuestaSala> actual(UUID usuarioId) {
		Instant ahora = atender(usuarioId);
		String codigo = salaPorUsuario.get(usuarioId);
		if (codigo == null) {
			return Optional.empty();
		}
		return Optional.of(consultarComoJugador(salas.get(codigo), usuarioId, ahora));
	}

	public synchronized RespuestaSala consultar(UUID usuarioId, String codigo) {
		Instant ahora = atender(usuarioId);
		return consultarComoJugador(salas.get(CodigoSala.normalizar(codigo)), usuarioId, ahora);
	}

	public synchronized RespuestaSala marcarListo(UUID usuarioId, String codigo, Boolean listo) {
		if (listo == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.DATOS_INVALIDOS);
		}
		Instant ahora = atender(usuarioId);
		Sala sala = salas.get(CodigoSala.normalizar(codigo));
		if (sala != null && sala.jugador(usuarioId) != null && !sala.comenzada()) {
			sala.marcarListo(usuarioId, listo);
		}
		return consultarComoJugador(sala, usuarioId, ahora);
	}

	public synchronized RespuestaSala enviarTablero(UUID usuarioId, String codigo, List<Integer> numeros) {
		Instant ahora = atender(usuarioId);
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
			avanzarSiCorresponde(sala, ahora);
		}
		return consultarComoJugador(sala, usuarioId, ahora);
	}

	public synchronized RespuestaSala terminarTurno(UUID usuarioId, String codigo, Integer ronda,
			Boolean tableroLleno, Integer zona) {
		Instant ahora = atender(usuarioId);
		Sala sala = salaDeJugador(usuarioId, codigo);
		if (ronda == null || tableroLleno == null
				|| (zona != null && (zona < 1 || zona > PuntosPorMapa.cantidadZonas(sala.mapa())))) {
			throw new SolicitudInvalidaExcepcion(CodigoError.DATOS_INVALIDOS);
		}
		if (!sala.comenzada()) {
			throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_COMENZADA);
		}
		sala.registrarTurno(usuarioId, ronda, tableroLleno, zona);
		avanzarSiCorresponde(sala, ahora);
		return consultarComoJugador(sala, usuarioId, ahora);
	}

	public synchronized RespuestaSala conceder(UUID usuarioId, String codigo) {
		Instant ahora = atender(usuarioId);
		Sala sala = salaDeJugador(usuarioId, codigo);
		if (!sala.comenzada()) {
			throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_COMENZADA);
		}
		concederEn(sala, usuarioId, ahora);
		return consultarComoJugador(sala, usuarioId, ahora);
	}

	public synchronized void salir(UUID usuarioId, String codigo) {
		Instant ahora = atender(usuarioId);
		sacarDeSala(salaDeJugador(usuarioId, codigo), usuarioId, ahora);
	}

	private Instant atender(UUID usuarioId) {
		Instant ahora = reloj.instant();
		revisarSalas(ahora);
		String codigo = salaPorUsuario.get(usuarioId);
		if (codigo != null) {
			Sala sala = salas.get(codigo);
			sala.registrarContacto(sala.jugador(usuarioId), ahora);
		}
		return ahora;
	}

	private void revisarSalas(Instant ahora) {
		for (Sala sala : List.copyOf(salas.values())) {
			if (!sala.comenzada()) {
				expulsarSinContacto(sala, ahora);
			} else if (sala.terminada()) {
				if (sala.terminadaHace(ahora, tiempos.salaTerminada())) {
					cerrar(sala);
				}
			} else {
				for (JugadorEnSala jugador : sala.activos()) {
					if (sala.abandono(jugador, ahora, tiempos.abandono())) {
						concederEn(sala, jugador.usuarioId(), ahora);
					}
				}
				avanzarSiCorresponde(sala, ahora);
			}
		}
	}

	private void expulsarSinContacto(Sala sala, Instant ahora) {
		for (JugadorEnSala jugador : sala.jugadores()) {
			if (salas.get(sala.codigo()) == sala && !jugador.contactoReciente(ahora, tiempos.sinContactoSala())) {
				sacarDeSala(sala, jugador.usuarioId(), ahora);
			}
		}
	}

	private void concederEn(Sala sala, UUID usuarioId, Instant ahora) {
		JugadorEnSala jugador = sala.jugador(usuarioId);
		if (sala.terminada() || jugador.concedio()) {
			return;
		}
		jugador.conceder();
		List<JugadorEnSala> activos = sala.activos();
		if (activos.size() <= 1) {
			sala.terminar(FIN_ABANDONO, activos.stream().map(JugadorEnSala::nombre).toList(), ahora);
		} else {
			avanzarSiCorresponde(sala, ahora);
		}
	}

	// Quien está desconectado no detiene la ronda; si nadie activo está conectado, se espera en lugar de
	// lanzar rondas vacías una tras otra.
	private void avanzarSiCorresponde(Sala sala, Instant ahora) {
		if (!sala.comenzada() || sala.terminada() || !sala.hayActivoConectado(ahora, tiempos.desconexion())) {
			return;
		}
		if (sala.ronda() == 0) {
			if (sala.todosTienenTablero(ahora, tiempos.desconexion())) {
				sala.comenzarRonda(dados.lanzar(), dados.lanzar());
			}
			return;
		}
		if (!sala.todosTerminaronLaRonda(ahora, tiempos.desconexion())) {
			return;
		}
		Map<String, Integer> puntos = Puntuacion.calcular(sala.mapa(), sala.zonas());
		List<String> nombres = sala.activos().stream().map(JugadorEnSala::nombre).toList();
		boolean llegoALaMeta = nombres.stream().anyMatch(nombre -> puntos.getOrDefault(nombre, 0) >= sala.meta());
		if (llegoALaMeta || sala.todosLlenaronSuTablero()) {
			sala.terminar(llegoALaMeta ? FIN_META : FIN_SIN_TABLEROS, Puntuacion.ganadores(nombres, puntos), ahora);
		} else {
			sala.comenzarRonda(dados.lanzar(), dados.lanzar());
		}
	}

	private Sala salaDeJugador(UUID usuarioId, String codigo) {
		Sala sala = salas.get(CodigoSala.normalizar(codigo));
		if (sala == null || sala.jugador(usuarioId) == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_ENCONTRADA);
		}
		return sala;
	}

	private void comprobarQueNoEstaEnPartidaComenzada(UUID usuarioId) {
		String codigo = salaPorUsuario.get(usuarioId);
		Sala sala = codigo == null ? null : salas.get(codigo);
		if (sala != null && sala.comenzada() && !sala.terminada() && !sala.jugador(usuarioId).concedio()) {
			throw new SolicitudInvalidaExcepcion(CodigoError.YA_EN_PARTIDA);
		}
	}

	private RespuestaSala consultarComoJugador(Sala sala, UUID usuarioId, Instant ahora) {
		JugadorEnSala jugador = sala == null ? null : sala.jugador(usuarioId);
		if (jugador == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.PARTIDA_NO_ENCONTRADA);
		}
		sala.registrarContacto(jugador, ahora);
		return respuesta(sala, usuarioId, ahora);
	}

	private RespuestaSala respuesta(Sala sala, UUID usuarioId, Instant ahora) {
		return RespuestaSala.de(sala, usuarioId, ahora, tiempos.desconexion());
	}

	private void sacarDeSalaActual(UUID usuarioId, Instant ahora) {
		String codigo = salaPorUsuario.get(usuarioId);
		Sala sala = codigo == null ? null : salas.get(codigo);
		if (sala == null) {
			salaPorUsuario.remove(usuarioId);
			return;
		}
		sacarDeSala(sala, usuarioId, ahora);
	}

	// Con la partida en juego, quien sale concede y sigue en la lista para que los demás vean que se rindió.
	private void sacarDeSala(Sala sala, UUID usuarioId, Instant ahora) {
		salaPorUsuario.remove(usuarioId, sala.codigo());
		if (!sala.comenzada()) {
			if (sala.esAnfitrion(usuarioId)) {
				cerrar(sala);
				return;
			}
			sala.quitar(usuarioId);
		} else if (sala.terminada()) {
			sala.quitar(usuarioId);
		} else {
			concederEn(sala, usuarioId, ahora);
		}
		if (sala.jugadores().stream().noneMatch(jugador -> estaVinculado(jugador, sala))) {
			salas.remove(sala.codigo());
		}
	}

	private boolean estaVinculado(JugadorEnSala jugador, Sala sala) {
		return sala.codigo().equals(salaPorUsuario.get(jugador.usuarioId()));
	}

	private void cerrar(Sala sala) {
		salas.remove(sala.codigo());
		for (JugadorEnSala jugador : sala.jugadores()) {
			salaPorUsuario.remove(jugador.usuarioId(), sala.codigo());
		}
	}
}
