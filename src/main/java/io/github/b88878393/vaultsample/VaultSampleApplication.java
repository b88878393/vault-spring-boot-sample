package io.github.b88878393.vaultsample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Vault + Spring Boot sample.
 *
 * <p>Secrets are loaded from HashiCorp Vault by Spring Cloud Vault through
 * {@code spring.config.import=vault://} (see {@code application.yml}) before any bean is created,
 * so the rest of the application consumes them like ordinary configuration properties.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class VaultSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaultSampleApplication.class, args);
    }
}
