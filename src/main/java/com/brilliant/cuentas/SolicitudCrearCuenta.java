package com.brilliant.cuentas;

public record SolicitudCrearCuenta(String nombre, String contrasena) {

	@Override
	public String toString() {
		return "SolicitudCrearCuenta[nombre=" + nombre + ", contrasena=****]";
	}
}
