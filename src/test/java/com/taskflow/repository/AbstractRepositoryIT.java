package com.taskflow.repository;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.taskflow.jooq.generated.Tables.PROJECTS;
import static com.taskflow.jooq.generated.Tables.TASKS;
import static com.taskflow.jooq.generated.Tables.USERS;

/**
 * Base de los tests de repository: levanta un MySQL REAL con Testcontainers (no H2) porque
 * jOOQ genera SQL del dialecto MySQL (p.ej. el ENUM nativo de tasks.status) y el objetivo es
 * verificar ese SQL contra el motor real, no contra una base compatible "a medias".
 *
 * Nota sobre el entorno de desarrollo (Windows + Docker Desktop): ver README, seccion
 * "Tests e integracion con MySQL real" - en esta maquina el cliente docker-java que usa
 * Testcontainers no logra hablar con el pipe que expone Docker Desktop (mismo problema ya
 * documentado en los repos hermanos del portafolio), aunque la CLI de docker funciona bien.
 * Por eso estos tests estan sufijados *IT y corren con `mvn verify`, no con `mvn test`; en
 * CI (GitHub Actions, Linux) se ejecutan de verdad.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class AbstractRepositoryIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("taskflow_test")
            .withUsername("taskflow")
            .withPassword("taskflow");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected DSLContext dsl;

    /** Limpia las tablas en orden (hijos primero) antes de cada test, para que no se pisen entre si. */
    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(TASKS).execute();
        dsl.deleteFrom(PROJECTS).execute();
        dsl.deleteFrom(USERS).execute();
    }
}
