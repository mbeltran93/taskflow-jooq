# TaskFlow API (Java + Spring Boot + jOOQ + MySQL)

![CI](https://github.com/mbeltran93/taskflow-jooq/actions/workflows/ci.yml/badge.svg)

API REST de gestion de tareas y proyectos tipo Trello/Jira reducido. Es parte de un portafolio
comparativo: el mismo dominio y el mismo contrato de API implementados con distintas tecnologias
backend. Esta version existe especificamente para mostrar **jOOQ + MySQL** como capa de acceso a
datos, en vez de JPA/Hibernate (que es lo que usan los otros repos Spring Boot del portafolio).

## Por que jOOQ (y no JPA)

JPA/Hibernate modela el acceso a datos alrededor de **entidades** y un grafo de objetos que el
ORM sincroniza con la base; jOOQ modela el acceso a datos alrededor del **SQL mismo**: la DSL es
SQL tipado en Java, no una abstraccion que lo reemplaza. Dos consecuencias practicas de esto:

- **Lo que compila es SQL valido para el dialecto real** (aqui MySQL): `TASKS.STATUS.eq(...)`
  solo compila si `STATUS` existe en `TASKS` y el tipo coincide: un error de nombre de columna o
  de tipo se detecta en compilacion, no en runtime contra la base.
- **No hay sorpresas de N+1 ni de generacion de SQL implicita**: cada metodo del repository hace
  exactamente el SELECT/INSERT/UPDATE que uno escribe con la DSL, ni mas ni menos. Para queries
  con varios joins y agregaciones (ver `TaskRepository`) eso es una ventaja directa sobre mapear
  todo a traves de relaciones JPA.

El costo es que jOOQ necesita conocer el esquema real para generar esas clases tipadas: de ahi
que el **codegen contra una base ya migrada** sea el punto central de usar jOOQ en serio (la
consigna de este repo es justamente no esquivarlo escribiendo todo a mano).

## Flujo de codegen de jOOQ

Flyway es la unica fuente de verdad del esquema (`src/main/resources/db/migration`). El codegen
de jOOQ corre **despues**, contra esa base ya migrada, y genera clases Java tipadas (tablas,
records, y hasta un enum Java para la columna `tasks.status`, que en MySQL es un `ENUM` nativo).

**Las clases generadas estan commiteadas** en
`src/main/java/com/taskflow/jooq/generated` (alternativa que la propia consigna habilita
explicitamente). Eso significa que:

- `mvn clean package` / `docker build` **no necesitan Docker ni MySQL corriendo**: compilan
  directo con lo que ya esta en el repo, como cualquier otro codigo fuente.
- Solo hace falta regenerarlas cuando cambia el esquema (una migracion nueva de Flyway).

Para regenerar (requiere Docker Desktop corriendo):

```bash
# Windows (PowerShell)
./scripts/regenerate-jooq.ps1

# Linux/Mac/Git Bash
./scripts/regenerate-jooq.sh
```

Cada script hace, en este orden:

1. Levanta un MySQL 8.4 descartable con `docker run` (puerto `3310`, se tira al final).
2. `mvn -P jooq-codegen flyway:migrate` - aplica todas las migraciones contra esa base.
3. `mvn -P jooq-codegen generate-sources` - corre `jooq-codegen-maven` contra la base ya migrada
   y sobreescribe `src/main/java/com/taskflow/jooq/generated`.
4. Tira el contenedor.

Despues solo falta revisar el diff y commitear las clases si el esquema cambio de verdad.

**Por que CLI de docker y no Testcontainers para esto**: Testcontainers (y cualquier plugin de
Maven que use `docker-java` para orquestar contenedores, como un hipotetico profile
"todo-automatico-con-Testcontainers") necesitan hablar con el daemon de Docker a traves de su
propio cliente Java. En esta maquina (Windows + Docker Desktop) ese cliente no logra conectarse
(ver seccion de tests mas abajo) aunque la CLI de `docker` funciona perfectamente - por eso los
scripts usan `docker run` / `docker exec` directo, que es exactamente lo que `docker compose`
tambien usa y por que ese sí funciona siempre en esta maquina.

**CI tambien verifica que el codegen no diverja**: el job `jooq-codegen-check` de
`.github/workflows/ci.yml` corre este mismo flujo (con un MySQL de servicio nativo de GitHub
Actions) y falla si `git diff` encuentra algo distinto a lo commiteado - es la prueba de que las
clases generadas realmente corresponden al esquema actual y no quedaron desactualizadas a mano.

## Arquitectura

Monolito en capas:

```
controller/   -> expone los endpoints REST, valida el request (Bean Validation) y delega al service
service/      -> logica de negocio: valida relaciones (owner/project/assignee existen), orquesta repos
repository/   -> acceso a datos con la DSL de jOOQ (joins, filtros dinamicos, GROUP BY)
dto/          -> records de entrada/salida; las clases generadas de jOOQ nunca se exponen en la API
security/     -> JwtService (genera/valida tokens) y JwtAuthFilter (filtro que autentica cada request)
config/       -> SecurityConfig, OpenApiConfig y JooqConfig (ver nota de catalog/schema abajo)
exception/    -> excepciones de dominio + @RestControllerAdvice centralizado (GlobalExceptionHandler)
jooq/generated/ -> clases generadas por jOOQ (tablas, records, el enum de status). No se edita a mano.
```

Puntos de diseño:

- **DTOs como Java records**, nunca se exponen los records de jOOQ (`UsersRecord`, etc.) ni sus
  tipos (p. ej. el enum generado `TasksStatus`) fuera de `repository`: la capa de servicio usa un
  enum de dominio propio (`dto.TaskStatus`), con el mapeo viviendo en `TaskRepository`.
- **Joins reales con la DSL**: `TaskRepository.findRows`/`findRowById` traen cada tarea con el
  nombre de su proyecto y el nombre de su assignee resueltos con `join`/`leftJoin` (el assignee
  es nullable, de ahi el `leftJoin`), sin SQL en strings.
- **Filtros dinamicos**: `GET /api/tasks?projectId=&status=` arma la lista de `Condition` segun
  que parametros vengan presentes y la pasa como coleccion a `.where(...)` - ni un `if` con SQL
  concatenado, ni una query distinta por combinacion de filtros.
- **Query agregada con GROUP BY**: `TaskRepository.countByStatusGroupedByProject` (expuesta en
  `GET /api/projects/task-status-summary`) agrupa tareas por proyecto y status y cuenta, todo con
  la DSL (`groupBy(...)`, `count()`).
- **`JooqConfig`**: el codegen corre contra un esquema efimero (`taskflow_codegen`) cuyo nombre
  queda grabado en las clases generadas. En runtime la base real se llama distinto segun el
  entorno (`taskflow` en dev/docker, el nombre que le de Testcontainers en los tests), asi que
  `JooqConfig` desactiva `renderSchema`/`renderCatalog`: jOOQ genera el SQL sin calificar las
  tablas con el esquema y usa la base por default de la conexion JDBC, la correcta en cada
  entorno sin tener que regenerar nada.
- **JWT stateless**: no hay sesiones ni cookies; cada request trae su propio token y
  `JwtAuthFilter` reconstruye la autenticacion en el `SecurityContext`. El secreto se toma como
  texto plano (no Base64) para poder setearlo como una variable de entorno simple.
- **Passwords con BCrypt** (`spring-security-crypto`), nunca en texto plano ni en las respuestas.
- **Manejo de errores centralizado**: `ResourceNotFoundException` -> 404,
  `DuplicateResourceException` -> 409, `InvalidCredentialsException` -> 401, errores de
  validacion de Bean Validation -> 400 con el detalle de cada campo. Las excepciones de
  autenticacion/autorizacion de Spring Security (token ausente/invalido, sin permisos) tambien
  responden en el mismo formato JSON (`SecurityConfig` las intercepta con un
  `authenticationEntryPoint`/`accessDeniedHandler` propios).

## Modelo de datos

```
users       id, name, email (unico), password_hash, created_at
projects    id, name, description, owner_id (FK -> users), created_at
tasks       id, title, description, status (ENUM: TODO|IN_PROGRESS|DONE),
            project_id (FK -> projects), assignee_id (FK -> users, nullable),
            due_date (nullable), created_at
```

La migracion Flyway (`V1__init_schema.sql`) crea las tablas, las foreign keys (`ON DELETE CASCADE`
para project->task y owner->project, `ON DELETE SET NULL` para el assignee de una tarea) y los
indices sobre las columnas mas consultadas (`project_id`, `status`, `assignee_id`).

## Como correrlo

Requisitos: Docker y Docker Compose.

```bash
docker compose up --build
```

Esto levanta:

- `db`: MySQL 8.4 (puerto `3308` en el host para no chocar con otro MySQL/Postgres local;
  `3306` puerto interno de siempre para la red de Docker).
- `app`: la API en `http://localhost:8080`, esperando a que MySQL este saludable y aplicando las
  migraciones de Flyway automaticamente al arrancar (el `app` **no** corre codegen de jOOQ: usa
  las clases ya commiteadas, ver seccion anterior).

Swagger UI: `http://localhost:8080/swagger-ui/index.html`
Salud: `http://localhost:8080/actuator/health`

Verificado de punta a punta en esta maquina con `docker compose up --build` + el flujo curl
completo de mas abajo (login, crear proyecto, crear tarea con join, filtros, reporte GROUP BY,
casos 401/404/409) y tambien con la coleccion de Postman via Newman (ver mas abajo).

### Correrlo sin Docker (desarrollo local)

Necesitas un MySQL local (o cambiar `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`):

```bash
mvn spring-boot:run
```

Variables de entorno relevantes (todas tienen default para dev):

| Variable | Default | Descripcion |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/taskflow` | conexion a MySQL |
| `DB_USERNAME` / `DB_PASSWORD` | `taskflow` / `taskflow` | credenciales de la DB |
| `JWT_SECRET` | clave de ejemplo (cambiarla siempre en produccion) | secreto HMAC para firmar los JWT (texto plano, minimo 32 caracteres) |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | vigencia del token |
| `SERVER_PORT` | `8080` | puerto HTTP |

## Tests

```bash
mvn test      # unitarios (rapidos, sin Docker)
mvn verify    # + integracion con MySQL real via Testcontainers (necesita Docker)
```

- **Tests unitarios** (`src/test/java/com/taskflow/service`, sufijo `Test`): JUnit 5 + Mockito,
  prueban la logica de `AuthService`/`UserService`/`ProjectService`/`TaskService` mockeando los
  repositories jOOQ (validaciones de existencia de relaciones, hasheo de password, propagacion
  de las excepciones de dominio). No tocan la base, corren siempre.
- **Tests de repository/integracion** (`src/test/java/com/taskflow/repository`, sufijo `IT`):
  levantan un **MySQL real con Testcontainers** (no H2 - jOOQ genera SQL del dialecto MySQL,
  incluido el `ENUM` nativo de `status`, asi que la honestidad del test depende de correr contra
  el motor real) y verifican, contra ese MySQL, los joins (`TaskRepositoryIT`), los filtros
  dinamicos combinados, el `GROUP BY` agregado, y el CRUD de `ProjectRepositoryIT`/`UserRepositoryIT`.

> **Nota sobre Testcontainers en esta maquina (Windows + Docker Desktop)**: igual que en los
> repos hermanos de este portafolio, el cliente `docker-java` que usa Testcontainers no logra
> hablar con el pipe que expone Docker Desktop
> (`NpipeSocketClientProviderStrategy: failed with exception BadRequestException (Status 400: ...)`),
> reproducido tal cual al escribir este repo (2026-10-01, Docker Desktop 4.56 / Engine 29.1.3),
> aunque la CLI de `docker` y `docker compose` funcionan perfectamente (son las que usan los
> scripts de codegen y el `docker compose up` de arriba). Por eso los `*IT` estan atados a
> `mvn verify` (goal `failsafe`, no `surefire`) y en este entorno no corren localmente.
>
> A diferencia del fallback de los repos JPA (H2 en modo compatibilidad), aqui **no se cambio el
> motor de los tests** porque la consigna de este repo pide explicitamente no usar H2 para jOOQ.
> En su lugar:
> - El codigo de los `*IT` es el real (Testcontainers + MySQL), y **corre de verdad en CI**
>   (`build-test` en `.github/workflows/ci.yml`, GitHub Actions/Linux, donde Testcontainers
>   funciona sin problemas: es el entorno estandar que el proyecto soporta oficialmente).
> - Localmente, la misma logica de joins/filtros/GROUP BY quedo verificada de otra forma: con el
>   stack completo de `docker compose up --build` (MySQL real + la app real) y el flujo curl /
>   Postman de este README, que ejercita exactamente ese codigo de `TaskRepository` contra un
>   MySQL real corriendo en Docker.

## Documentacion de la API

Con la app corriendo, Swagger UI queda en `http://localhost:8080/swagger-ui/index.html` (spec
OpenAPI en `http://localhost:8080/v3/api-docs`). Las rutas de escritura requieren el boton
**Authorize** con el JWT devuelto por `/api/auth/login`.

### Tabla de endpoints

| Metodo | Ruta | Auth | Descripcion |
|---|---|---|---|
| POST | `/api/auth/login` | No | Login, devuelve `{ token }` |
| GET | `/api/users` | No | Lista usuarios |
| GET | `/api/users/{id}` | No | Busca un usuario por id |
| POST | `/api/users` | No | Registra un usuario nuevo (no forma parte del contrato original del portafolio, pero es necesaria para poder loguearse) |
| GET | `/api/projects` | No | Lista proyectos |
| GET | `/api/projects/{id}` | No | Busca un proyecto por id |
| GET | `/api/projects/task-status-summary` | No | Reporte agregado: cantidad de tareas por status, por proyecto (GROUP BY con la DSL) |
| POST | `/api/projects` | JWT | Crea un proyecto |
| PUT | `/api/projects/{id}` | JWT | Actualiza un proyecto |
| DELETE | `/api/projects/{id}` | JWT | Elimina un proyecto |
| GET | `/api/tasks?projectId=&status=` | No | Lista tareas con su proyecto y assignee resueltos (join); filtrable por proyecto y/o status |
| GET | `/api/tasks/{id}` | No | Busca una tarea por id (con join) |
| POST | `/api/tasks` | JWT | Crea una tarea |
| PUT | `/api/tasks/{id}` | JWT | Actualiza una tarea |
| PATCH | `/api/tasks/{id}/status` | JWT | Cambia solo el estado de una tarea |
| DELETE | `/api/tasks/{id}` | JWT | Elimina una tarea |

### Ejemplo de uso con curl

```bash
# 1. Registrarse (owner)
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Maria","email":"maria@taskflow.dev","password":"password123"}'

# 2. Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"maria@taskflow.dev","password":"password123"}' | jq -r .token)

# 3. Crear un proyecto (requiere JWT)
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"TaskFlow","description":"demo","ownerId":1}'

# 4. Crear una tarea
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"Escribir README","projectId":1}'

# 5. Cambiar el estado de la tarea
curl -X PATCH http://localhost:8080/api/tasks/1/status \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"status":"IN_PROGRESS"}'

# 6. Reporte agregado (GROUP BY)
curl http://localhost:8080/api/projects/task-status-summary
```

### Coleccion de Postman

El archivo [`postman_collection.json`](./postman_collection.json) en la raiz del repo tiene el
mismo flujo de arriba ya armado, mas casos de error (401/404/409):

1. En Postman: **File > Import** y seleccionar `postman_collection.json`.
2. La coleccion trae su variable `baseUrl` en `http://localhost:8080`; editarla en
   **Collection > Variables** si la API corre en otro puerto/host.
3. Correr la carpeta **"Flujo completo"** de arriba hacia abajo: cada request guarda en
   variables de coleccion (`token`, `ownerId`, `assigneeId`, `projectId`, `taskId`) lo que el
   siguiente necesita, asi que no hay que copiar/pegar nada a mano.
4. La carpeta **"Casos de error"** es independiente del flujo principal.

Validado de punta a punta con [Newman](https://www.npmjs.com/package/newman) contra el stack de
`docker compose` (17/17 assertions ok):

```bash
npx newman run postman_collection.json
```

## Limitaciones conocidas

- No hay roles/permisos finos: cualquier usuario autenticado puede crear/editar/borrar cualquier
  proyecto o tarea (no solo las propias). Se prioriza dejar clara la mecanica de JWT y de jOOQ
  sobre un modelo de autorizacion granular.
- El endpoint de registro (`POST /api/users`) es publico y no estaba en el contrato original del
  portafolio; se agrego porque sin el no habria forma de crear cuentas para loguearse.
- `GET /api/projects/task-status-summary` tampoco esta en el contrato original: se agrego para
  demostrar la query agregada con `GROUP BY` que pide la consigna.
- Los tests `*IT` (Testcontainers + MySQL real) no corren localmente en esta maquina por el
  problema de Docker Desktop + `docker-java` descripto arriba; si corren en CI. Es la misma clase
  de limitacion ya documentada en los repos JPA hermanos, con la diferencia de que aqui el
  workaround no cambia el motor de base de datos (sigue siendo MySQL real, solo que verificado
  via `docker compose` + curl/Postman en vez de via Testcontainers, localmente).
- No hay paginacion en los listados (`GET /api/users`, `/api/projects`, `/api/tasks`): para el
  alcance de este portafolio se devuelven completos.
- No hay refresh tokens: el JWT expira (24h por default) y hay que volver a loguearse.
- Las clases de `jooq/generated` se regeneran a mano con los scripts de `scripts/`; no hay un
  hook de pre-commit que las regenere automaticamente si alguien olvida correrlo despues de
  tocar una migracion (el job `jooq-codegen-check` de CI es la red de seguridad para eso).
