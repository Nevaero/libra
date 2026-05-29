package io.libra.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Brands the OpenAPI definition that springdoc generates from the controllers and DTOs. The spec is
// served at /v3/api-docs and the Swagger UI at /swagger-ui.html (both open under the permit-all
// placeholder chain).
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI libraOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Libra API")
                .description("REST API for the Libra multi-asset broker: physical FX with T+2 "
                        + "settlement over a double-entry ledger. No authentication yet (placeholder).")
                .version("v1"));
    }
}
