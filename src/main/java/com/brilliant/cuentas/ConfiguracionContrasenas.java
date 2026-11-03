package com.brilliant.cuentas;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ConfiguracionContrasenas {

	@Bean
	public PasswordEncoder codificadorContrasenas() {
		return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
	}
}
