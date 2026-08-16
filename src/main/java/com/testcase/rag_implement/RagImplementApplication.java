package com.testcase.rag_implement;

import com.testcase.rag_implement.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class RagImplementApplication {

	public static void main(String[] args) {
		SpringApplication.run(RagImplementApplication.class, args);
	}

}
