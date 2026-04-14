package com.garden.smartgarden;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartGardenApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartGardenApplication.class, args);
	}

}
