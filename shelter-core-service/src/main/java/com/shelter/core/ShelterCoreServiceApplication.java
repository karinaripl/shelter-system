package com.shelter.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShelterCoreServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShelterCoreServiceApplication.class, args);
	}

}
