package com.brilliant.cuentas.usuarios;

public class NombreUsuarioDuplicadoExcepcion extends RuntimeException {

	public NombreUsuarioDuplicadoExcepcion(String nombre) {
		super("Ya existe un usuario con el nombre: " + nombre);
	}
}
