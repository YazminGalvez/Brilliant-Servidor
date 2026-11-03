package com.brilliant.cuentas;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentasControlador {

	private final ServicioCuentas servicio;

	public CuentasControlador(ServicioCuentas servicio) {
		this.servicio = servicio;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RespuestaCuenta crearCuenta(@RequestBody SolicitudCrearCuenta solicitud) {
		return RespuestaCuenta.de(servicio.crearCuenta(solicitud.nombre(), solicitud.contrasena()));
	}
}
