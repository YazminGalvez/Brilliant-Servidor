package com.brilliant.cuentas.partidas;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TiemposSala {

	private final Duration sinContactoSala;
	private final Duration desconexion;
	private final Duration abandono;
	private final Duration salaTerminada;

	public TiemposSala(@Value("${cuentas.room-idle-seconds}") long segundosSinContactoSala,
			@Value("${cuentas.disconnect-seconds}") long segundosDesconexion,
			@Value("${cuentas.abandon-seconds}") long segundosAbandono,
			@Value("${cuentas.finished-room-seconds}") long segundosSalaTerminada) {
		this.sinContactoSala = segundos("SEGUNDOS_SIN_CONTACTO_SALA", segundosSinContactoSala);
		this.desconexion = segundos("SEGUNDOS_DESCONEXION", segundosDesconexion);
		this.abandono = segundos("SEGUNDOS_ABANDONO", segundosAbandono);
		this.salaTerminada = segundos("SEGUNDOS_SALA_TERMINADA", segundosSalaTerminada);
	}

	private static Duration segundos(String variable, long valor) {
		if (valor <= 0) {
			throw new IllegalStateException(variable + " debe ser mayor que cero");
		}
		return Duration.ofSeconds(valor);
	}

	public Duration sinContactoSala() {
		return sinContactoSala;
	}

	public Duration desconexion() {
		return desconexion;
	}

	public Duration abandono() {
		return abandono;
	}

	public Duration salaTerminada() {
		return salaTerminada;
	}
}
