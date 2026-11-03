package com.brilliant.cuentas.seguridad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.brilliant.cuentas.usuarios.RepositorioUsuarios;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Los contadores viven solo en memoria: se pierden al reiniciar el servidor.
 */
@Component
public class LimitadorIntentos {

	private record Registro(Instant inicioVentana, int fallos, Instant bloqueadoHasta) {
	}

	private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();
	private final Clock reloj;
	private final int maxIntentos;
	private final Duration bloqueo;
	private final AtomicReference<Instant> ultimaLimpieza;

	public LimitadorIntentos(Clock reloj,
			@Value("${cuentas.max-intentos-fallidos}") int maxIntentos,
			@Value("${cuentas.bloqueo-segundos}") long bloqueoSegundos) {
		if (maxIntentos <= 0) {
			throw new IllegalStateException("MAX_INTENTOS_FALLIDOS debe ser mayor que cero");
		}
		if (bloqueoSegundos <= 0) {
			throw new IllegalStateException("BLOQUEO_SEGUNDOS debe ser mayor que cero");
		}
		this.reloj = reloj;
		this.maxIntentos = maxIntentos;
		this.bloqueo = Duration.ofSeconds(bloqueoSegundos);
		this.ultimaLimpieza = new AtomicReference<>(reloj.instant());
	}

	public static String ipDe(HttpServletRequest solicitud) {
		// Se usa la dirección real de la conexión: X-Forwarded-For lo escribe el cliente y cualquiera puede falsificarlo.
		return solicitud.getRemoteAddr();
	}

	public Optional<Duration> tiempoBloqueado(String nombre, String ip) {
		Instant ahora = reloj.instant();
		return Stream.of(claveUsuario(nombre), claveIp(ip))
				.map(registros::get)
				.filter(Objects::nonNull)
				.filter(registro -> registro.bloqueadoHasta() != null && ahora.isBefore(registro.bloqueadoHasta()))
				.map(registro -> Duration.between(ahora, registro.bloqueadoHasta()))
				.max(Comparator.naturalOrder());
	}

	public void registrarFallo(String nombre, String ip) {
		Instant ahora = reloj.instant();
		sumarFallo(claveUsuario(nombre), ahora);
		sumarFallo(claveIp(ip), ahora);
		limpiarSiCorresponde(ahora);
	}

	public void reiniciarUsuario(String nombre) {
		registros.remove(claveUsuario(nombre));
	}

	int cantidadRegistros() {
		return registros.size();
	}

	private void sumarFallo(String clave, Instant ahora) {
		registros.compute(clave, (llave, actual) -> {
			Registro vigente = actual == null || vencido(actual, ahora) ? new Registro(ahora, 0, null) : actual;
			if (vigente.bloqueadoHasta() != null) {
				return vigente;
			}
			int fallos = vigente.fallos() + 1;
			return new Registro(vigente.inicioVentana(), fallos, fallos >= maxIntentos ? ahora.plus(bloqueo) : null);
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

	private static String claveUsuario(String nombre) {
		return "usuario:" + RepositorioUsuarios.normalizarNombre(nombre);
	}

	private static String claveIp(String ip) {
		return "ip:" + ip;
	}
}
