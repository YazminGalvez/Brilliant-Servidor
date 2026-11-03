package com.brilliant.cuentas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import com.brilliant.cuentas.errores.CodigoError;
import com.brilliant.cuentas.errores.CuerpoDemasiadoGrandeExcepcion;
import com.brilliant.cuentas.errores.RespuestaError;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class FiltroTamanoCuerpo extends OncePerRequestFilter {

	private final long limite;
	private final ObjectMapper mapeador;

	public FiltroTamanoCuerpo(@Value("${cuentas.tamano-maximo-cuerpo}") DataSize limite, ObjectMapper mapeador) {
		this.limite = limite.toBytes();
		this.mapeador = mapeador;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		if (solicitud.getContentLengthLong() > limite) {
			CodigoError codigo = CodigoError.CUERPO_DEMASIADO_GRANDE;
			respuesta.setStatus(codigo.estado().value());
			respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
			mapeador.writeValue(respuesta.getOutputStream(), RespuestaError.de(codigo));
			return;
		}
		cadena.doFilter(new SolicitudLimitada(solicitud, limite), respuesta);
	}

	private static final class SolicitudLimitada extends HttpServletRequestWrapper {

		private final long limite;
		private ServletInputStream entrada;

		SolicitudLimitada(HttpServletRequest solicitud, long limite) {
			super(solicitud);
			this.limite = limite;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (entrada == null) {
				entrada = new EntradaLimitada(super.getInputStream(), limite);
			}
			return entrada;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			String codificacion = getCharacterEncoding();
			Charset juegoCaracteres = codificacion != null ? Charset.forName(codificacion) : StandardCharsets.UTF_8;
			return new BufferedReader(new InputStreamReader(getInputStream(), juegoCaracteres));
		}
	}

	private static final class EntradaLimitada extends ServletInputStream {

		private final ServletInputStream original;
		private final long limite;
		private long leidos;

		EntradaLimitada(ServletInputStream original, long limite) {
			this.original = original;
			this.limite = limite;
		}

		@Override
		public int read() throws IOException {
			int dato = original.read();
			if (dato != -1) {
				contar(1);
			}
			return dato;
		}

		@Override
		public int read(byte[] destino, int desde, int cantidad) throws IOException {
			int leidosAhora = original.read(destino, desde, cantidad);
			if (leidosAhora > 0) {
				contar(leidosAhora);
			}
			return leidosAhora;
		}

		private void contar(int cantidad) throws CuerpoDemasiadoGrandeExcepcion {
			leidos += cantidad;
			if (leidos > limite) {
				throw new CuerpoDemasiadoGrandeExcepcion();
			}
		}

		@Override
		public boolean isFinished() {
			return original.isFinished();
		}

		@Override
		public boolean isReady() {
			return original.isReady();
		}

		@Override
		public void setReadListener(ReadListener oyente) {
			original.setReadListener(oyente);
		}
	}
}
