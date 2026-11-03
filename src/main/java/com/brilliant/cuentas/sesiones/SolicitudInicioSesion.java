package com.brilliant.cuentas.sesiones;

public record SolicitudInicioSesion(String nombre, String contrasena) {

	@Override
	public String toString() {
		return "SolicitudInicioSesion[nombre=" + nombre + ", contrasena=****]";
	}
}
