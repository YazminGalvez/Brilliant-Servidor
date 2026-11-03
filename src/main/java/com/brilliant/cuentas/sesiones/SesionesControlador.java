package com.brilliant.cuentas.sesiones;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.brilliant.cuentas.seguridad.LimitadorIntentos;
import com.brilliant.cuentas.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/sesiones")
public class SesionesControlador {

	private final ServicioSesiones servicio;

	public SesionesControlador(ServicioSesiones servicio) {
		this.servicio = servicio;
	}

	@PostMapping
	public RespuestaSesion iniciarSesion(@RequestBody SolicitudInicioSesion solicitud, HttpServletRequest peticion) {
		return servicio.iniciarSesion(solicitud.nombre(), solicitud.contrasena(), LimitadorIntentos.ipDe(peticion));
	}

	@DeleteMapping("/actual")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cerrarSesion(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		servicio.cerrarSesion(usuario.sesionId());
	}
}
