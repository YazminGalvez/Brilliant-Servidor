package com.brilliant.cuentas.partidas;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.brilliant.cuentas.errores.DemasiadosIntentosExcepcion;
import com.brilliant.cuentas.seguridad.ContadorFallos;

@Component
public class LimitadorCodigos {

	private static final Duration VENTANA = Duration.ofSeconds(60);

	private final ContadorFallos contador;

	public LimitadorCodigos(Clock reloj, @Value("${cuentas.max-failed-codes}") int maxCodigosFallidos) {
		if (maxCodigosFallidos <= 0) {
			throw new IllegalStateException("MAX_CODIGOS_FALLIDOS debe ser mayor que cero");
		}
		this.contador = new ContadorFallos(reloj, maxCodigosFallidos, VENTANA);
	}

	public void comprobar(UUID usuarioId) {
		contador.tiempoBloqueado(clave(usuarioId)).ifPresent(restante -> {
			throw new DemasiadosIntentosExcepcion(restante);
		});
	}

	public void registrarFallo(UUID usuarioId) {
		contador.registrarFallo(clave(usuarioId));
	}

	public void reiniciar(UUID usuarioId) {
		contador.reiniciar(clave(usuarioId));
	}

	private static String clave(UUID usuarioId) {
		return "codigos:" + usuarioId;
	}
}
