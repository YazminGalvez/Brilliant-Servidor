package com.brilliant.cuentas;

import com.brilliant.cuentas.usuarios.Usuario;

public record RespuestaCuenta(String id, String nombre) {

	public static RespuestaCuenta de(Usuario usuario) {
		return new RespuestaCuenta(usuario.id().toString(), usuario.nombre());
	}
}
