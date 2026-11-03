package com.brilliant.cuentas;

import java.text.Normalizer;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.brilliant.cuentas.errores.CodigoError;
import com.brilliant.cuentas.errores.DemasiadosIntentosExcepcion;
import com.brilliant.cuentas.errores.SolicitudInvalidaExcepcion;
import com.brilliant.cuentas.seguridad.LimitadorIntentos;
import com.brilliant.cuentas.usuarios.RepositorioUsuarios;
import com.brilliant.cuentas.usuarios.Usuario;

@Service
public class ServicioCuentas {

	private static final Pattern PATRON_NOMBRE = Pattern.compile("[A-Za-z0-9_\\-ÁÉÍÓÚÜÑáéíóúüñ]{3,32}");
	private static final int LONGITUD_MINIMA_CONTRASENA = 8;
	private static final int LONGITUD_MAXIMA_CONTRASENA = 128;

	private final RepositorioUsuarios repositorio;
	private final PasswordEncoder codificador;
	private final ListaContrasenasComunes contrasenasComunes;
	private final LimitadorIntentos limitador;

	public ServicioCuentas(RepositorioUsuarios repositorio, PasswordEncoder codificador,
			ListaContrasenasComunes contrasenasComunes, LimitadorIntentos limitador) {
		this.repositorio = repositorio;
		this.codificador = codificador;
		this.contrasenasComunes = contrasenasComunes;
		this.limitador = limitador;
	}

	public Usuario crearCuenta(String nombre, String contrasena) {
		if (nombre == null || contrasena == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.CAMPOS_FALTANTES);
		}

		String nombreRecortado = Normalizer.normalize(nombre, Normalizer.Form.NFC).strip();
		if (!PATRON_NOMBRE.matcher(nombreRecortado).matches()) {
			throw new SolicitudInvalidaExcepcion(CodigoError.NOMBRE_INVALIDO);
		}

		int longitud = contrasena.codePointCount(0, contrasena.length());
		if (longitud < LONGITUD_MINIMA_CONTRASENA || longitud > LONGITUD_MAXIMA_CONTRASENA) {
			throw new SolicitudInvalidaExcepcion(CodigoError.CONTRASENA_LONGITUD_INVALIDA);
		}
		if (contrasena.strip().equalsIgnoreCase(nombreRecortado)) {
			throw new SolicitudInvalidaExcepcion(CodigoError.CONTRASENA_IGUAL_AL_NOMBRE);
		}
		if (contrasenasComunes.contiene(contrasena)) {
			throw new SolicitudInvalidaExcepcion(CodigoError.CONTRASENA_COMUN);
		}

		String hash = codificador.encode(contrasena);
		return repositorio.crear(nombreRecortado, hash);
	}

	public Usuario obtenerCuenta(UUID usuarioId) {
		return repositorio.buscarPorId(usuarioId)
				.orElseThrow(() -> new SolicitudInvalidaExcepcion(CodigoError.NO_AUTORIZADO));
	}

	public void borrarCuenta(UUID usuarioId, String contrasena, String ip) {
		if (contrasena == null) {
			throw new SolicitudInvalidaExcepcion(CodigoError.CAMPOS_FALTANTES);
		}
		Usuario usuario = obtenerCuenta(usuarioId);
		limitador.tiempoBloqueado(usuario.nombre(), ip).ifPresent(restante -> {
			throw new DemasiadosIntentosExcepcion(restante);
		});
		if (!codificador.matches(contrasena, usuario.hashContrasena())) {
			limitador.registrarFallo(usuario.nombre(), ip);
			throw new SolicitudInvalidaExcepcion(CodigoError.CREDENCIALES_INVALIDAS);
		}
		repositorio.borrar(usuarioId);
		limitador.reiniciarUsuario(usuario.nombre());
	}
}
