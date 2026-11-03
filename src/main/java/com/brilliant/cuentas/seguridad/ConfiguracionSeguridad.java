package com.brilliant.cuentas.seguridad;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;

@Configuration
public class ConfiguracionSeguridad {

	private static final RequestMatcher RUTAS_PUBLICAS = new OrRequestMatcher(
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/salud"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/cuentas"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/sesiones"));

	@Bean
	public SecurityFilterChain cadenaSeguridad(HttpSecurity http, ConvertidorSesionJwt convertidor,
			ManejadorErroresSeguridad manejador, CorsConfigurationSource fuenteCors) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(fuenteCors))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.sessionManagement(sesiones -> sesiones.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rutas -> rutas
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(RUTAS_PUBLICAS).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(servidor -> servidor
						.bearerTokenResolver(resolutorToken())
						.authenticationEntryPoint(manejador)
						.accessDeniedHandler(manejador)
						.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor)))
				.exceptionHandling(errores -> errores
						.authenticationEntryPoint(manejador)
						.accessDeniedHandler(manejador));
		return http.build();
	}

	private static BearerTokenResolver resolutorToken() {
		DefaultBearerTokenResolver predeterminado = new DefaultBearerTokenResolver();
		return solicitud -> RUTAS_PUBLICAS.matches(solicitud) ? null : predeterminado.resolve(solicitud);
	}

	@Bean
	public CorsConfigurationSource fuenteCors(@Value("${cuentas.origenes-permitidos}") String origenes,
			@Value("${cuentas.produccion}") String produccion) {
		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/**", crearConfiguracionCors(origenes, "true".equalsIgnoreCase(produccion.strip())));
		return fuente;
	}

	public static CorsConfiguration crearConfiguracionCors(String origenes, boolean produccion) {
		List<String> permitidos = new ArrayList<>();
		for (String origen : origenes.split(",")) {
			String recortado = origen.strip();
			if (recortado.isEmpty()) {
				continue;
			}
			if (recortado.contains("*")) {
				throw new IllegalStateException("ORIGENES_PERMITIDOS no admite comodines: " + recortado);
			}
			permitidos.add(recortado);
		}

		CorsConfiguration configuracion = new CorsConfiguration();
		configuracion.setAllowedOrigins(permitidos);
		if (!produccion) {
			configuracion.setAllowedOriginPatterns(List.of("http://localhost:[*]", "http://127.0.0.1:[*]"));
		}
		configuracion.setAllowedMethods(List.of("GET", "POST", "DELETE"));
		configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuracion.setAllowCredentials(false);
		configuracion.setMaxAge(Duration.ofHours(1));
		return configuracion;
	}
}
