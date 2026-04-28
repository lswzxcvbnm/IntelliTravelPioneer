package com.travel.intellitravel_pioneer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableAsync
public class IntellitravelPioneerApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntellitravelPioneerApplication.class, args);
	}

	@Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

	@Bean
	public ObjectMapper objectMapper() {
		JavaTimeModule javaTimeModule = new JavaTimeModule();
		return new ObjectMapper()
				.registerModule(javaTimeModule)
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

}


