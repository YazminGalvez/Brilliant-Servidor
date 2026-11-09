package com.brilliant.cuentas.partidas;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.brilliant.cuentas.seguridad.UsuarioAutenticado;

@RestController
@RequestMapping("/api/v1/partidas")
public class PartidasControlador {

	private final ServicioPartidas servicio;

	public PartidasControlador(ServicioPartidas servicio) {
		this.servicio = servicio;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RespuestaSala crear(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@RequestBody SolicitudCrearPartida solicitud) {
		return servicio.crear(usuario.usuarioId(), solicitud.mapa(), solicitud.jugadores());
	}

	@PostMapping("/unirse")
	public RespuestaSala unirse(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@RequestBody SolicitudUnirsePartida solicitud) {
		return servicio.unirse(usuario.usuarioId(), solicitud.codigo());
	}

	@GetMapping("/actual")
	public ResponseEntity<RespuestaSala> actual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return servicio.actual(usuario.usuarioId())
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{codigo}/listo")
	public RespuestaSala marcarListo(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@PathVariable String codigo, @RequestBody SolicitudListo solicitud) {
		return servicio.marcarListo(usuario.usuarioId(), codigo, solicitud.listo());
	}

	@PostMapping("/{codigo}/tablero")
	public RespuestaSala enviarTablero(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@PathVariable String codigo, @RequestBody SolicitudTablero solicitud) {
		return servicio.enviarTablero(usuario.usuarioId(), codigo, solicitud.numeros());
	}

	@PostMapping("/{codigo}/turno")
	public RespuestaSala terminarTurno(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@PathVariable String codigo, @RequestBody SolicitudTurno solicitud) {
		return servicio.terminarTurno(usuario.usuarioId(), codigo, solicitud.ronda(), solicitud.tableroLleno(),
				solicitud.zona());
	}

	@PostMapping("/{codigo}/conceder")
	public RespuestaSala conceder(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String codigo) {
		return servicio.conceder(usuario.usuarioId(), codigo);
	}

	@PostMapping("/{codigo}/salir")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void salir(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String codigo) {
		servicio.salir(usuario.usuarioId(), codigo);
	}

	@GetMapping("/{codigo}")
	public RespuestaSala consultar(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@PathVariable String codigo) {
		return servicio.consultar(usuario.usuarioId(), codigo);
	}
}
