package com.brilliant.cuentas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ConfiguracionRelojPrueba {

	@Bean
	@Primary
	public RelojPrueba relojPrueba() {
		return new RelojPrueba();
	}
}
