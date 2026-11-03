package com.brilliant.cuentas;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class RelojPrueba extends Clock {

	private volatile Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);

	public void avanzar(Duration tiempo) {
		ahora = ahora.plus(tiempo);
	}

	@Override
	public Instant instant() {
		return ahora;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zona) {
		return this;
	}
}
