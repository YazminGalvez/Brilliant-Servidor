package com.brilliant.cuentas.seguridad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClaveFirma {

	public static final String NOMBRE_ARCHIVO = "clave-firma.bin";
	public static final int LONGITUD_BYTES = 32;

	private static final Logger registro = LoggerFactory.getLogger(ClaveFirma.class);

	private ClaveFirma() {
	}

	public static byte[] obtener(String claveBase64, Path directorioDatos) throws IOException {
		if (claveBase64 != null && !claveBase64.isBlank()) {
			return desdeBase64(claveBase64.strip());
		}

		Path archivo = directorioDatos.resolve(NOMBRE_ARCHIVO);
		if (Files.exists(archivo)) {
			byte[] clave = Files.readAllBytes(archivo);
			if (clave.length < LONGITUD_BYTES) {
				throw new IllegalStateException("El archivo " + archivo + " no contiene una clave de 256 bits");
			}
			return clave;
		}

		byte[] clave = new byte[LONGITUD_BYTES];
		new SecureRandom().nextBytes(clave);
		Files.createDirectories(directorioDatos);
		crearArchivoRestringido(archivo);
		Files.write(archivo, clave, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
		registro.info("Se generó una clave de firma nueva en {}", archivo.toAbsolutePath());
		return clave;
	}

	private static byte[] desdeBase64(String claveBase64) {
		byte[] clave;
		try {
			clave = Base64.getDecoder().decode(claveBase64);
		} catch (IllegalArgumentException excepcion) {
			throw new IllegalStateException("CLAVE_FIRMA no está en base64 válido");
		}
		if (clave.length < LONGITUD_BYTES) {
			throw new IllegalStateException("CLAVE_FIRMA debe tener al menos 256 bits");
		}
		return clave;
	}

	private static void crearArchivoRestringido(Path archivo) throws IOException {
		if (archivo.getFileSystem().supportedFileAttributeViews().contains("posix")) {
			Files.createFile(archivo, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
			return;
		}

		Files.createFile(archivo);
		AclFileAttributeView acl = Files.getFileAttributeView(archivo, AclFileAttributeView.class);
		if (acl != null) {
			AclEntry soloDueno = AclEntry.newBuilder()
					.setType(AclEntryType.ALLOW)
					.setPrincipal(Files.getOwner(archivo))
					.setPermissions(EnumSet.allOf(AclEntryPermission.class))
					.build();
			acl.setAcl(List.of(soloDueno));
		}
	}
}
