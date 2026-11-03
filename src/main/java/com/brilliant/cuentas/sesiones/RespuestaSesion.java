package com.brilliant.cuentas.sesiones;

import com.brilliant.cuentas.RespuestaCuenta;

public record RespuestaSesion(String token, String tipo, long expiraEnSegundos, RespuestaCuenta usuario) {

	@Override
	public String toString() {
		return "RespuestaSesion[token=****, tipo=" + tipo + ", expiraEnSegundos=" + expiraEnSegundos
				+ ", usuario=" + usuario + "]";
	}
}
