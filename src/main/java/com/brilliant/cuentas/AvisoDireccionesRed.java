package com.brilliant.cuentas;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class AvisoDireccionesRed implements ApplicationListener<WebServerInitializedEvent> {

	static final String SOLO_LOCAL =
			"Solo disponible en esta computadora; para recibir celulares inicia con BIND_ADDRESS=0.0.0.0";
	static final String SIN_DIRECCIONES =
			"No se encontró ninguna dirección de red; revisa que la computadora esté conectada a la red";

	private static final Set<String> DIRECCIONES_LOCALES = Set.of("127.0.0.1", "localhost", "::1");
	private static final Set<String> TODAS_LAS_DIRECCIONES = Set.of("", "0.0.0.0", "::");
	// Los adaptadores de máquinas virtuales y contenedores tienen IP pero un celular no puede llegar a ellos.
	private static final List<String> NOMBRES_VIRTUALES =
			List.of("virtual", "vmware", "vethernet", "hyper-v", "wsl", "docker", "vbox");

	private static final Logger registro = LoggerFactory.getLogger(AvisoDireccionesRed.class);

	private final String direccionEnlace;

	public AvisoDireccionesRed(@Value("${server.address:}") String direccionEnlace) {
		this.direccionEnlace = direccionEnlace;
	}

	@Override
	public void onApplicationEvent(WebServerInitializedEvent evento) {
		int puerto = evento.getWebServer().getPort();
		mensajes(direccionEnlace, puerto, ipsDeRed()).forEach(registro::info);
	}

	static List<String> mensajes(String direccionEnlace, int puerto, List<String> ips) {
		String direccion = direccionEnlace == null ? "" : direccionEnlace.strip().toLowerCase(Locale.ROOT);
		if (DIRECCIONES_LOCALES.contains(direccion)) {
			return List.of(SOLO_LOCAL);
		}
		if (!TODAS_LAS_DIRECCIONES.contains(direccion)) {
			return List.of(disponibleEn(direccion, puerto));
		}
		if (ips.isEmpty()) {
			return List.of(SIN_DIRECCIONES);
		}
		return ips.stream().map(ip -> disponibleEn(ip, puerto)).toList();
	}

	private static String disponibleEn(String ip, int puerto) {
		return "Disponible en http://" + ip + ":" + puerto;
	}

	static List<String> ipsDeRed() {
		List<String> ips = new ArrayList<>();
		try {
			for (NetworkInterface interfaz : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!interfaz.isUp() || interfaz.isLoopback() || interfaz.isVirtual() || esVirtual(interfaz)) {
					continue;
				}
				Collections.list(interfaz.getInetAddresses()).stream()
						.filter(Inet4Address.class::isInstance)
						.filter(ip -> !ip.isLoopbackAddress() && !ip.isLinkLocalAddress())
						.map(ip -> ip.getHostAddress())
						.forEach(ips::add);
			}
		} catch (SocketException excepcion) {
			registro.warn("No se pudieron leer las interfaces de red: {}", excepcion.getMessage());
		}
		return ips;
	}

	private static boolean esVirtual(NetworkInterface interfaz) {
		String nombre = (interfaz.getName() + " " + interfaz.getDisplayName()).toLowerCase(Locale.ROOT);
		return NOMBRES_VIRTUALES.stream().anyMatch(nombre::contains);
	}
}
