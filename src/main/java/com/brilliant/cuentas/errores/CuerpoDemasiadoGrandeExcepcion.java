package com.brilliant.cuentas.errores;

import java.io.IOException;

public class CuerpoDemasiadoGrandeExcepcion extends IOException {

	public CuerpoDemasiadoGrandeExcepcion() {
		super(CodigoError.CUERPO_DEMASIADO_GRANDE.name());
	}
}
