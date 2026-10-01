package com.taskflow.config;

import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqConfig {

    /**
     * El codegen corre contra un esquema efimero ("taskflow_codegen", ver
     * scripts/regenerate-jooq.*) cuyo nombre queda grabado en las clases generadas
     * (DefaultCatalog/DefaultSchema). En runtime la base real se llama distinto segun el
     * entorno (taskflow en dev/docker, taskflow_test en los tests con Testcontainers), asi que
     * desactivamos el render de catalog/schema: jOOQ genera el SQL sin calificar las tablas y
     * usa la base por default de la conexion JDBC (la del path de la URL), que es la correcta
     * en cada entorno sin tener que regenerar nada.
     */
    @Bean
    public DefaultConfigurationCustomizer jooqConfigurationCustomizer() {
        return configuration -> configuration.settings()
                .withRenderSchema(false)
                .withRenderCatalog(false);
    }
}
