package com.brilliant.cuentas.seguridad;

import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.brilliant.cuentas.sesiones.Sesion;

@Service
public class ServicioTokens {

	private final JwtEncoder codificador;

	public ServicioTokens(JwtEncoder codificador) {
		this.codificador = codificador;
	}

	public String emitir(Sesion sesion) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ConfiguracionTokens.EMISOR)
				.audience(List.of(ConfiguracionTokens.AUDIENCIA))
				.subject(sesion.usuarioId().toString())
				.claim(ConfiguracionTokens.CLAIM_SESION, sesion.id().toString())
				.issuedAt(sesion.creadoEn())
				.expiresAt(sesion.expiraEn())
				.build();
		JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
		return codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
	}
}
