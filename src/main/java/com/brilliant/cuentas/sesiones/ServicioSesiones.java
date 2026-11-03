package com.brilliant.cuentas.sesiones;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.brilliant.cuentas.RespuestaCuenta;
import com.brilliant.cuentas.errores.CodigoError;
import com.brilliant.cuentas.errores.DemasiadosIntentosExcepcion;
import com.brilliant.cuentas.errores.SolicitudInvalidaExcepcion;
import com.brilliant.cuentas.seguridad.LimitadorIntentos;
import com.brilliant.cuentas.seguridad.ServicioTokens;
import com.brilliant.cuentas.usuarios.RepositorioUsuarios;
import com.brilliant.cuentas.usuarios.Usuario;

@Service
public class ServicioSesiones {

	private static final String TIPO_TOKEN = "Bearer";

	private final RepositorioUsuarios repositorioUsuarios;
	private final RepositorioSesiones repositorioSesiones;
	private final PasswordEncoder codificador;
	private final ServicioTokens servicioTokens;
	private final LimitadorIntentos limitador;
	private final Clock reloj;
	private final Duration duracion;
	private final String hashFicticio;

	public ServicioSesiones(RepositorioUsuarios repositorioUsuarios, RepositorioSesiones repositorioSesiones,
			PasswordEncoder codificador, ServicioTokens servicioTokens, LimitadorIntentos limitador, Clock reloj,
			@Value("${cuentas.duracion-sesion-segundos}") long duracionSegundos) {
		if (duracionSegundos <= 0) {
			throw new IllegalStateException("DURACION_SESION_SEGUNDOS debe ser mayor que cero");
		}
		this.repositorioUsuarios = repositorioUsuarios;
		this.repositorioSesiones = repositorioSesiones;
		this.codificador = codificador;
		this.servicioTokens = servicioTokens;
		this.limitador = limitador;
		this.reloj = reloj;
		this.duracion = Duration.ofSeconds(duracionSegundos);
		this.hashFicticio = codificador.encode(UUID.randomUUID().toString());
	}

	public RespuestaSesion iniciarSesion(String nombre, String contrasena, String ip) {
		if (nombre == null || contrasena == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.CAMPOS_FALTANTES);
		}
		limitador.tiempoBloqueado(nombre, ip).ifPresent(restante -> {
			throw new DemasiadosIntentosExcepcion(restante);
		});

		Optional<Usuario> usuario = repositorioUsuarios.buscarPorNombre(nombre);
		String hash = usuario.map(Usuario::hashContrasena).orElse(hashFicticio);
		boolean coincide = codificador.matches(contrasena, hash);
		if (usuario.isEmpty() || !coincide) {
			limitador.registrarFallo(nombre, ip);
			throw new SolicitudInvalidaExcepcion(CodigoError.CREDENCIALES_INVALIDAS);
		}
		limitador.reiniciarUsuario(nombre);

		Instant ahora = reloj.instant();
		repositorioSesiones.borrarExpiradas(ahora);
		Sesion sesion = repositorioSesiones.crear(usuario.get().id(), ahora, ahora.plus(duracion));
		return new RespuestaSesion(servicioTokens.emitir(sesion), TIPO_TOKEN, duracion.toSeconds(),
				RespuestaCuenta.de(usuario.get()));
	}

	public void cerrarSesion(UUID sesionId) {
		repositorioSesiones.revocar(sesionId, reloj.instant());
	}
}
