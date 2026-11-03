package com.brilliant.cuentas;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SaludControlador {

	@GetMapping("/salud")
	public Map<String, String> consultarSalud() {
		return Map.of("estado", "ok");
	}
}
