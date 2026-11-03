package com.brilliant.cuentas;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.brilliant.cuentas.seguridad.LimitadorIntentos;
import com.brilliant.cuentas.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;

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

	@GetMapping("/yo")
	public RespuestaCuenta consultarCuenta(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return RespuestaCuenta.de(servicio.obtenerCuenta(usuario.usuarioId()));
	}

	@DeleteMapping("/yo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void borrarCuenta(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@RequestBody SolicitudBorrarCuenta solicitud, HttpServletRequest peticion) {
		servicio.borrarCuenta(usuario.usuarioId(), solicitud.contrasena(), LimitadorIntentos.ipDe(peticion));
	}
}
