package com.brilliant.cuentas.errores;

public class SolicitudInvalidaExcepcion extends RuntimeException {

	private final CodigoError codigo;

	public SolicitudInvalidaExcepcion(CodigoError codigo) {
		super(codigo.name());
		this.codigo = codigo;
	}

	public CodigoError codigo() {
		return codigo;
	}
}
