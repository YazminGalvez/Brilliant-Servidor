package com.brilliant.cuentas;

public record SolicitudBorrarCuenta(String contrasena) {

	@Override
	public String toString() {
		return "SolicitudBorrarCuenta[contrasena=****]";
	}
}
