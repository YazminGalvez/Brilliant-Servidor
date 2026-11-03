package com.brilliant.cuentas.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaveFirmaPrueba {

	@TempDir
	Path directorioDatos;

	@Test
	void generaClaveDe256BitsYLaReutiliza() throws Exception {
		byte[] primera = ClaveFirma.obtener("", directorioDatos);
		byte[] segunda = ClaveFirma.obtener(null, directorioDatos);

		assertThat(primera).hasSize(32).isEqualTo(segunda);
		assertThat(directorioDatos.resolve(ClaveFirma.NOMBRE_ARCHIVO)).isRegularFile().hasBinaryContent(primera);
	}

	@Test
	void creaElDirectorioSiNoExiste() throws Exception {
		Path anidado = directorioDatos.resolve("a").resolve("b");
		assertThat(ClaveFirma.obtener(null, anidado)).hasSize(32);
		assertThat(anidado.resolve(ClaveFirma.NOMBRE_ARCHIVO)).isRegularFile();
	}

	@Test
	void archivoGeneradoTienePermisosRestringidos() throws Exception {
		ClaveFirma.obtener(null, directorioDatos);
		Path archivo = directorioDatos.resolve(ClaveFirma.NOMBRE_ARCHIVO);

		PosixFileAttributeView posix = Files.getFileAttributeView(archivo, PosixFileAttributeView.class);
		if (posix != null) {
			assertThat(PosixFilePermissions.toString(posix.readAttributes().permissions())).isEqualTo("rw-------");
			return;
		}
		AclFileAttributeView acl = Files.getFileAttributeView(archivo, AclFileAttributeView.class);
		if (acl != null) {
			List<AclEntry> entradas = acl.getAcl();
			assertThat(entradas).hasSize(1);
			assertThat(entradas.get(0).principal()).isEqualTo(Files.getOwner(archivo));
		}
	}

	@Test
	void usaLaClaveDeLaVariableSinCrearArchivo() throws Exception {
		byte[] clave = new byte[32];
		clave[0] = 7;

		assertThat(ClaveFirma.obtener(Base64.getEncoder().encodeToString(clave), directorioDatos)).isEqualTo(clave);
		assertThat(directorioDatos.resolve(ClaveFirma.NOMBRE_ARCHIVO)).doesNotExist();
	}

	@Test
	void rechazaClaveCortaOBase64Invalido() {
		String corta = Base64.getEncoder().encodeToString(new byte[16]);

		assertThatThrownBy(() -> ClaveFirma.obtener(corta, directorioDatos))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining(corta);
		assertThatThrownBy(() -> ClaveFirma.obtener("no-es-base64!!", directorioDatos))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("no-es-base64");
	}
}
