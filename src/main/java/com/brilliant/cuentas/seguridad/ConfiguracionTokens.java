package com.brilliant.cuentas.seguridad;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class ConfiguracionTokens {

	public static final String EMISOR = "brilliant-cuentas";
	public static final String AUDIENCIA = "brilliant-juego";
	public static final String CLAIM_SESION = "sid";

	@Bean
	public Clock reloj() {
		return Clock.systemUTC();
	}

	@Bean
	public SecretKey claveFirma(@Value("${cuentas.clave-firma}") String claveBase64,
			@Value("${cuentas.directorio-datos}") Path directorioDatos) throws IOException {
		return new SecretKeySpec(ClaveFirma.obtener(claveBase64, directorioDatos), "HmacSHA256");
	}

	@Bean
	public JwtEncoder codificadorTokens(SecretKey claveFirma) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(claveFirma));
	}

	@Bean
	public JwtDecoder decodificadorTokens(SecretKey claveFirma, Clock reloj) {
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(claveFirma)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();

		JwtTimestampValidator vigencia = new JwtTimestampValidator(Duration.ZERO);
		vigencia.setClock(reloj);

		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
				vigencia,
				new JwtIssuerValidator(EMISOR),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
						audiencia -> audiencia != null && audiencia.contains(AUDIENCIA))));
		return decodificador;
	}
}
