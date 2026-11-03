package com.brilliant.cuentas.errores;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.brilliant.cuentas.usuarios.NombreUsuarioDuplicadoExcepcion;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ManejadorErrores {

	private static final Logger registro = LoggerFactory.getLogger(ManejadorErrores.class);

	public static ResponseEntity<RespuestaError> responder(CodigoError codigo) {
		return ResponseEntity.status(codigo.estado()).body(RespuestaError.de(codigo));
	}

	@ExceptionHandler(SolicitudInvalidaExcepcion.class)
	public ResponseEntity<RespuestaError> solicitudInvalida(SolicitudInvalidaExcepcion excepcion) {
		return responder(excepcion.codigo());
	}

	@ExceptionHandler(NombreUsuarioDuplicadoExcepcion.class)
	public ResponseEntity<RespuestaError> nombreEnUso() {
		return responder(CodigoError.NOMBRE_EN_USO);
	}

	@ExceptionHandler(DemasiadosIntentosExcepcion.class)
	public ResponseEntity<RespuestaError> demasiadosIntentos(DemasiadosIntentosExcepcion excepcion) {
		CodigoError codigo = CodigoError.DEMASIADOS_INTENTOS;
		return ResponseEntity.status(codigo.estado())
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(excepcion.segundosRestantes()))
				.body(RespuestaError.de(codigo));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<RespuestaError> cuerpoIlegible(HttpMessageNotReadableException excepcion) {
		for (Throwable causa = excepcion; causa != null; causa = causa.getCause()) {
			if (causa instanceof CuerpoDemasiadoGrandeExcepcion) {
				return responder(CodigoError.CUERPO_DEMASIADO_GRANDE);
			}
		}
		return responder(CodigoError.CUERPO_INVALIDO);
	}

	@ExceptionHandler(CuerpoDemasiadoGrandeExcepcion.class)
	public ResponseEntity<RespuestaError> cuerpoDemasiadoGrande() {
		return responder(CodigoError.CUERPO_DEMASIADO_GRANDE);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<RespuestaError> tipoContenidoNoSoportado() {
		return responder(CodigoError.TIPO_CONTENIDO_NO_SOPORTADO);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<RespuestaError> metodoNoPermitido() {
		return responder(CodigoError.METODO_NO_PERMITIDO);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<RespuestaError> recursoNoEncontrado() {
		return responder(CodigoError.RECURSO_NO_ENCONTRADO);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<RespuestaError> errorInesperado(Exception excepcion, HttpServletRequest solicitud) {
		if (excepcion instanceof ErrorResponse respuesta && respuesta.getStatusCode().is4xxClientError()) {
			return ResponseEntity.status(respuesta.getStatusCode())
					.body(RespuestaError.de(CodigoError.SOLICITUD_INVALIDA));
		}
		registro.error("Error inesperado en {} {}", solicitud.getMethod(), solicitud.getRequestURI(), excepcion);
		return responder(CodigoError.ERROR_INTERNO);
	}
}
