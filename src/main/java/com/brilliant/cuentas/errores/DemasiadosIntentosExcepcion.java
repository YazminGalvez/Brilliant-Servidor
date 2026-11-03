package com.brilliant.cuentas.errores;

import java.time.Duration;

public class DemasiadosIntentosExcepcion extends RuntimeException {

	private final long segundosRestantes;

	public DemasiadosIntentosExcepcion(Duration restante) {
		super(CodigoError.DEMASIADOS_INTENTOS.name());
		this.segundosRestantes = Math.max(1, (restante.toMillis() + 999) / 1000);
	}

	public long segundosRestantes() {
		return segundosRestantes;
	}
}
