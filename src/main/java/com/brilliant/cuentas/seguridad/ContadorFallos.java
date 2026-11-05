package com.brilliant.cuentas.seguridad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Los contadores viven solo en memoria: se pierden al reiniciar el servidor.
 */
public class ContadorFallos {

	private record Registro(Instant inicioVentana, int fallos, Instant bloqueadoHasta) {
	}

	private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();
	private final Clock reloj;
	private final int maxFallos;
	private final Duration bloqueo;
	private final AtomicReference<Instant> ultimaLimpieza;

	public ContadorFallos(Clock reloj, int maxFallos, Duration bloqueo) {
		this.reloj = reloj;
		this.maxFallos = maxFallos;
		this.bloqueo = bloqueo;
		this.ultimaLimpieza = new AtomicReference<>(reloj.instant());
	}

	public Optional<Duration> tiempoBloqueado(String... claves) {
		Instant ahora = reloj.instant();
		return Arrays.stream(claves)
				.map(registros::get)
				.filter(Objects::nonNull)
				.filter(registro -> registro.bloqueadoHasta() != null && ahora.isBefore(registro.bloqueadoHasta()))
				.map(registro -> Duration.between(ahora, registro.bloqueadoHasta()))
				.max(Comparator.naturalOrder());
	}

	public void registrarFallo(String... claves) {
		Instant ahora = reloj.instant();
		for (String clave : claves) {
			sumarFallo(clave, ahora);
		}
		limpiarSiCorresponde(ahora);
	}

	public void reiniciar(String clave) {
		registros.remove(clave);
	}

	public int cantidadRegistros() {
		return registros.size();
	}

	private void sumarFallo(String clave, Instant ahora) {
		registros.compute(clave, (llave, actual) -> {
			Registro vigente = actual == null || vencido(actual, ahora) ? new Registro(ahora, 0, null) : actual;
			if (vigente.bloqueadoHasta() != null) {
				return vigente;
			}
			int fallos = vigente.fallos() + 1;
			return new Registro(vigente.inicioVentana(), fallos, fallos >= maxFallos ? ahora.plus(bloqueo) : null);
		});
	}

	private boolean vencido(Registro registro, Instant ahora) {
		Instant fin = registro.bloqueadoHasta() != null
				? registro.bloqueadoHasta()
				: registro.inicioVentana().plus(bloqueo);
		return !ahora.isBefore(fin);
	}

	private void limpiarSiCorresponde(Instant ahora) {
		Instant anterior = ultimaLimpieza.get();
		if (ahora.isBefore(anterior.plus(bloqueo)) || !ultimaLimpieza.compareAndSet(anterior, ahora)) {
			return;
		}
		registros.entrySet().removeIf(entrada -> vencido(entrada.getValue(), ahora));
	}
}
