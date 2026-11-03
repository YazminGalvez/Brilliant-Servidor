package com.brilliant.cuentas.seguridad;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.brilliant.cuentas.errores.CodigoError;
import com.brilliant.cuentas.errores.RespuestaError;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ManejadorErroresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper mapeador;

	public ManejadorErroresSeguridad(ObjectMapper mapeador) {
		this.mapeador = mapeador;
	}

	@Override
	public void commence(HttpServletRequest solicitud, HttpServletResponse respuesta,
			AuthenticationException excepcion) throws IOException {
		respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		escribir(respuesta, CodigoError.NO_AUTORIZADO);
	}

	@Override
	public void handle(HttpServletRequest solicitud, HttpServletResponse respuesta,
			AccessDeniedException excepcion) throws IOException {
		escribir(respuesta, CodigoError.ACCESO_DENEGADO);
	}

	private void escribir(HttpServletResponse respuesta, CodigoError codigo) throws IOException {
		respuesta.setStatus(codigo.estado().value());
		respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
		mapeador.writeValue(respuesta.getOutputStream(), RespuestaError.de(codigo));
	}
}
