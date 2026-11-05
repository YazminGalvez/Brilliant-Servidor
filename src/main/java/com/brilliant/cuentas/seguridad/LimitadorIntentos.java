package com.brilliant.cuentas.seguridad;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.brilliant.cuentas.usuarios.RepositorioUsuarios;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class LimitadorIntentos {

	private final ContadorFallos contador;

	public LimitadorIntentos(Clock reloj,
			@Value("${cuentas.max-intentos-fallidos}") int maxIntentos,
			@Value("${cuentas.bloqueo-segundos}") long bloqueoSegundos) {
		if (maxIntentos <= 0) {
			throw new IllegalStateException("MAX_INTENTOS_FALLIDOS debe ser mayor que cero");
		}
		if (bloqueoSegundos <= 0) {
			throw new IllegalStateException("BLOQUEO_SEGUNDOS debe ser mayor que cero");
		}
		this.contador = new ContadorFallos(reloj, maxIntentos, Duration.ofSeconds(bloqueoSegundos));
	}

	public static String ipDe(HttpServletRequest solicitud) {
		// Se usa la dirección real de la conexión: X-Forwarded-For lo escribe el cliente y cualquiera puede falsificarlo.
		return solicitud.getRemoteAddr();
	}

	public Optional<Duration> tiempoBloqueado(String nombre, String ip) {
		return contador.tiempoBloqueado(claveUsuario(nombre), claveIp(ip));
	}

	public void registrarFallo(String nombre, String ip) {
		contador.registrarFallo(claveUsuario(nombre), claveIp(ip));
	}

	public void reiniciarUsuario(String nombre) {
		contador.reiniciar(claveUsuario(nombre));
	}

	int cantidadRegistros() {
		return contador.cantidadRegistros();
	}

	private static String claveUsuario(String nombre) {
		return "usuario:" + RepositorioUsuarios.normalizarNombre(nombre);
	}

	private static String claveIp(String ip) {
		return "ip:" + ip;
	}
}
