package com.brilliant.cuentas.seguridad;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.brilliant.cuentas.sesiones.RepositorioSesiones;

@Component
public class ConvertidorSesionJwt implements Converter<Jwt, AbstractAuthenticationToken> {

	private final RepositorioSesiones repositorio;
	private final Clock reloj;

	public ConvertidorSesionJwt(RepositorioSesiones repositorio, Clock reloj) {
		this.repositorio = repositorio;
		this.reloj = reloj;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		UUID usuarioId = comoUuid(jwt.getSubject());
		UUID sesionId = comoUuid(jwt.getClaimAsString(ConfiguracionTokens.CLAIM_SESION));
		Instant ahora = reloj.instant();

		boolean valida = repositorio.buscarPorId(sesionId)
				.filter(sesion -> sesion.usuarioId().equals(usuarioId))
				.filter(sesion -> sesion.esValidaEn(ahora))
				.isPresent();
		if (!valida) {
			throw new InvalidBearerTokenException("La sesión no es válida");
		}

		return UsernamePasswordAuthenticationToken.authenticated(
				new UsuarioAutenticado(usuarioId, sesionId), null, List.of());
	}

	private static UUID comoUuid(String valor) {
		try {
			return UUID.fromString(valor);
		} catch (IllegalArgumentException | NullPointerException excepcion) {
			throw new InvalidBearerTokenException("El token no identifica una sesión");
		}
	}
}
