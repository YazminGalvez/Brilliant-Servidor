package com.brilliant.cuentas.errores;

import org.springframework.http.HttpStatus;

public enum CodigoError {

	SOLICITUD_INVALIDA(HttpStatus.BAD_REQUEST, "La solicitud no es válida."),
	CUERPO_INVALIDO(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido."),
	CAMPOS_FALTANTES(HttpStatus.BAD_REQUEST, "Faltan datos obligatorios."),
	NOMBRE_INVALIDO(HttpStatus.BAD_REQUEST,
			"El nombre debe tener de 3 a 32 caracteres: letras (con ñ y acentos), números, guion o guion bajo."),
	CONTRASENA_LONGITUD_INVALIDA(HttpStatus.BAD_REQUEST, "La contraseña debe tener de 8 a 128 caracteres."),
	CONTRASENA_IGUAL_AL_NOMBRE(HttpStatus.BAD_REQUEST, "La contraseña no puede ser igual al nombre."),
	CONTRASENA_COMUN(HttpStatus.BAD_REQUEST, "La contraseña es demasiado común."),
	NOMBRE_EN_USO(HttpStatus.CONFLICT, "El nombre ya está en uso."),
	CREDENCIALES_INVALIDAS(HttpStatus.UNAUTHORIZED, "El nombre o la contraseña no son correctos."),
	NO_AUTORIZADO(HttpStatus.UNAUTHORIZED, "Se requiere iniciar sesión."),
	ACCESO_DENEGADO(HttpStatus.FORBIDDEN, "No tienes permiso para esta acción."),
	DATOS_INVALIDOS(HttpStatus.BAD_REQUEST, "Los datos de la partida no son válidos."),
	PARTIDA_NO_ENCONTRADA(HttpStatus.NOT_FOUND, "La partida no existe."),
	PARTIDA_LLENA(HttpStatus.CONFLICT, "La partida ya está llena."),
	PARTIDA_COMENZADA(HttpStatus.CONFLICT, "La partida ya comenzó."),
	YA_EN_PARTIDA(HttpStatus.CONFLICT, "Ya estás en una partida en curso."),
	PARTIDA_NO_COMENZADA(HttpStatus.CONFLICT, "La partida aún no comienza."),
	DEMASIADOS_INTENTOS(HttpStatus.TOO_MANY_REQUESTS,
			"Demasiados intentos fallidos. Espera un momento antes de volver a intentarlo."),
	RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND, "El recurso no existe."),
	METODO_NO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED, "El método no está permitido."),
	TIPO_CONTENIDO_NO_SOPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El tipo de contenido no está soportado."),
	CUERPO_DEMASIADO_GRANDE(HttpStatus.PAYLOAD_TOO_LARGE, "El cuerpo de la solicitud es demasiado grande."),
	ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.");

	private final HttpStatus estado;
	private final String mensaje;

	CodigoError(HttpStatus estado, String mensaje) {
		this.estado = estado;
		this.mensaje = mensaje;
	}

	public HttpStatus estado() {
		return estado;
	}

	public String mensaje() {
		return mensaje;
	}
}
