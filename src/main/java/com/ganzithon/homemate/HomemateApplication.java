package com.ganzithon.homemate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class HomemateApplication {

	public static void main(String[] args) {
		SpringApplication.run(HomemateApplication.class, args);
	}

}
