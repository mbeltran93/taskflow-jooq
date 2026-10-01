package com.taskflow.repository;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import static com.taskflow.jooq.generated.Tables.PROJECTS;
import static com.taskflow.jooq.generated.Tables.TASKS;
import static com.taskflow.jooq.generated.Tables.USERS;

/**
 * Base de los tests de repository: levanta un MySQL REAL con Testcontainers (no H2) porque
 * jOOQ genera SQL del dialecto MySQL (p.ej. el ENUM nativo de tasks.status) y el objetivo es
 * verificar ese SQL contra el motor real, no contra una base compatible "a medias".
 *
 * El contenedor se levanta como "singleton container" (patron documentado por Testcontainers):
 * se arranca a mano en un bloque {@code static}, SIN la anotacion {@code @Container}, para que
 * ninguna extension de JUnit lo pare al terminar una clase de test. Con {@code @Container} (ciclo
 * de vida por clase) la primera clase *IT que corre lo para en su {@code afterAll}, pero Spring
 * reutiliza el mismo ApplicationContext (y su DataSource, ya apuntando al puerto del contenedor
 * muerto) para la siguiente clase *IT -> "Connection refused". Con el patron singleton el
 * contenedor vive durante toda la JVM de test y Ryuk lo limpia al final.
 *
 * Nota sobre el entorno de desarrollo (Windows + Docker Desktop): ver README, seccion
 * "Tests e integracion con MySQL real" - en esta maquina el cliente docker-java que usa
 * Testcontainers no logra hablar con el pipe que expone Docker Desktop (mismo problema ya
 * documentado en los repos hermanos del portafolio), aunque la CLI de docker funciona bien.
 * Por eso estos tests estan sufijados *IT y corren con `mvn verify`, no con `mvn test`; en
 * CI (GitHub Actions, Linux) se ejecutan de verdad.
 */
// webEnvironment MOCK (el default): estos tests solo necesitan el DSLContext, pero con NONE
// Spring Boot excluye la autoconfiguracion de servlet y SecurityConfig.filterChain(HttpSecurity)
// no encuentra el bean HttpSecurity (lo registra HttpSecurityConfiguration, condicionado a un
// ApplicationContext de tipo SERVLET). MOCK carga ese contexto sin levantar un puerto real.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class AbstractRepositoryIT {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("taskflow_test")
            .withUsername("taskflow")
            .withPassword("taskflow");

    static {
        MYSQL.start();
    }

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
