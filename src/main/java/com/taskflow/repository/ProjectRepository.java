package com.taskflow.repository;

import com.taskflow.jooq.generated.tables.records.ProjectsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.taskflow.jooq.generated.Tables.PROJECTS;

@Repository
public class ProjectRepository {

    private final DSLContext dsl;

    public ProjectRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<ProjectsRecord> findAll() {
        return dsl.selectFrom(PROJECTS).orderBy(PROJECTS.ID).fetch();
    }

    public Optional<ProjectsRecord> findById(Long id) {
        return dsl.selectFrom(PROJECTS).where(PROJECTS.ID.eq(id)).fetchOptional();
    }

    public boolean existsById(Long id) {
        return dsl.fetchExists(dsl.selectOne().from(PROJECTS).where(PROJECTS.ID.eq(id)));
    }

    public ProjectsRecord insert(String name, String description, Long ownerId) {
        return dsl.insertInto(PROJECTS)
                .set(PROJECTS.NAME, name)
                .set(PROJECTS.DESCRIPTION, description)
                .set(PROJECTS.OWNER_ID, ownerId)
                .returning(PROJECTS.fields())
                .fetchOne();
    }

    public Optional<ProjectsRecord> update(Long id, String name, String description, Long ownerId) {
        int updated = dsl.update(PROJECTS)
                .set(PROJECTS.NAME, name)
                .set(PROJECTS.DESCRIPTION, description)
                .set(PROJECTS.OWNER_ID, ownerId)
                .where(PROJECTS.ID.eq(id))
                .execute();
        return updated == 0 ? Optional.empty() : findById(id);
    }

    public boolean deleteById(Long id) {
        return dsl.deleteFrom(PROJECTS).where(PROJECTS.ID.eq(id)).execute() > 0;
    }
}
