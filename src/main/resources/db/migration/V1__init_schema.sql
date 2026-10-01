-- Esquema inicial de TaskFlow (jOOQ edition).
-- Esta migracion es la UNICA fuente de verdad del esquema: jOOQ no crea ni altera tablas,
-- solo genera clases Java a partir de lo que Flyway ya aplico.

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(120)  NOT NULL,
    email         VARCHAR(190)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE projects (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    owner_id    BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_projects_owner
        FOREIGN KEY (owner_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_projects_owner_id ON projects (owner_id);

CREATE TABLE tasks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description VARCHAR(4000),
    status      ENUM ('TODO', 'IN_PROGRESS', 'DONE') NOT NULL DEFAULT 'TODO',
    project_id  BIGINT       NOT NULL,
    assignee_id BIGINT,
    due_date    DATE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee
        FOREIGN KEY (assignee_id) REFERENCES users (id)
        ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE INDEX idx_tasks_project_id ON tasks (project_id);
CREATE INDEX idx_tasks_status ON tasks (status);
CREATE INDEX idx_tasks_assignee_id ON tasks (assignee_id);
