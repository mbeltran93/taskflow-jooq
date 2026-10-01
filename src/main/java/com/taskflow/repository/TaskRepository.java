package com.taskflow.repository;

import com.taskflow.dto.ProjectTaskStatusCount;
import com.taskflow.dto.TaskStatus;
import com.taskflow.jooq.generated.enums.TasksStatus;
import com.taskflow.jooq.generated.tables.Users;
import com.taskflow.jooq.generated.tables.records.TasksRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.taskflow.jooq.generated.Tables.PROJECTS;
import static com.taskflow.jooq.generated.Tables.TASKS;
import static com.taskflow.jooq.generated.Tables.USERS;
import static org.jooq.impl.DSL.count;

@Repository
public class TaskRepository {

    /** Alias de USERS para poder joinear la misma tabla dos veces (owner de proyecto no, pero
     * assignee de tarea si convive con cualquier otro join futuro sobre USERS). Usarlo siempre
     * para el assignee deja la query lista para crecer sin ambiguedad de columnas. */
    private static final Users ASSIGNEE = USERS.as("assignee");

    private final DSLContext dsl;

    public TaskRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<TaskRow> findRows(Long projectId, TaskStatus status) {
        List<Condition> conditions = buildConditions(projectId, status);

        return baseJoinSelect()
                .where(conditions)
                .orderBy(TASKS.ID)
                .fetch(TaskRepository::toRow);
    }

    public Optional<TaskRow> findRowById(Long id) {
        return baseJoinSelect()
                .where(TASKS.ID.eq(id))
                .fetchOptional(TaskRepository::toRow);
    }

    public boolean existsById(Long id) {
        return dsl.fetchExists(dsl.selectOne().from(TASKS).where(TASKS.ID.eq(id)));
    }

    public TasksRecord insert(String title, String description, Long projectId, Long assigneeId, LocalDate dueDate) {
        return dsl.insertInto(TASKS)
                .set(TASKS.TITLE, title)
                .set(TASKS.DESCRIPTION, description)
                .set(TASKS.STATUS, TasksStatus.TODO)
                .set(TASKS.PROJECT_ID, projectId)
                .set(TASKS.ASSIGNEE_ID, assigneeId)
                .set(TASKS.DUE_DATE, dueDate)
                .returning(TASKS.fields())
                .fetchOne();
    }

    public boolean update(Long id, String title, String description, Long projectId, Long assigneeId, LocalDate dueDate) {
        int updated = dsl.update(TASKS)
                .set(TASKS.TITLE, title)
                .set(TASKS.DESCRIPTION, description)
                .set(TASKS.PROJECT_ID, projectId)
                .set(TASKS.ASSIGNEE_ID, assigneeId)
                .set(TASKS.DUE_DATE, dueDate)
                .where(TASKS.ID.eq(id))
                .execute();
        return updated > 0;
    }

    public boolean updateStatus(Long id, TaskStatus status) {
        int updated = dsl.update(TASKS)
                .set(TASKS.STATUS, toJooqStatus(status))
                .where(TASKS.ID.eq(id))
                .execute();
        return updated > 0;
    }

    public boolean deleteById(Long id) {
        return dsl.deleteFrom(TASKS).where(TASKS.ID.eq(id)).execute() > 0;
    }

    /**
     * Query "mas compleja" pedida en la consigna: cuenta de tareas por status, agrupada por
     * proyecto, con GROUP BY resuelto por la DSL de jOOQ (no SQL crudo).
     */
    public List<ProjectTaskStatusCount> countByStatusGroupedByProject() {
        return dsl.select(TASKS.PROJECT_ID, PROJECTS.NAME, TASKS.STATUS, count())
                .from(TASKS)
                .join(PROJECTS).on(TASKS.PROJECT_ID.eq(PROJECTS.ID))
                .groupBy(TASKS.PROJECT_ID, PROJECTS.NAME, TASKS.STATUS)
                .orderBy(TASKS.PROJECT_ID, TASKS.STATUS)
                .fetch(r -> new ProjectTaskStatusCount(
                        r.get(TASKS.PROJECT_ID),
                        r.get(PROJECTS.NAME),
                        toDomainStatus(r.get(TASKS.STATUS)),
                        r.get(count()).longValue()
                ));
    }

    private org.jooq.SelectOnConditionStep<org.jooq.Record10<Long, String, String, TasksStatus, Long, String, Long, String, LocalDate, java.time.LocalDateTime>>
            baseJoinSelect() {
        return dsl.select(
                        TASKS.ID, TASKS.TITLE, TASKS.DESCRIPTION, TASKS.STATUS,
                        TASKS.PROJECT_ID, PROJECTS.NAME,
                        TASKS.ASSIGNEE_ID, ASSIGNEE.NAME,
                        TASKS.DUE_DATE, TASKS.CREATED_AT)
                .from(TASKS)
                .join(PROJECTS).on(TASKS.PROJECT_ID.eq(PROJECTS.ID))
                .leftJoin(ASSIGNEE).on(TASKS.ASSIGNEE_ID.eq(ASSIGNEE.ID));
    }

    private static List<Condition> buildConditions(Long projectId, TaskStatus status) {
        List<Condition> conditions = new ArrayList<>();
        if (projectId != null) {
            conditions.add(TASKS.PROJECT_ID.eq(projectId));
        }
        if (status != null) {
            conditions.add(TASKS.STATUS.eq(toJooqStatus(status)));
        }
        return conditions;
    }

    private static TaskRow toRow(org.jooq.Record10<Long, String, String, TasksStatus, Long, String, Long, String, LocalDate, java.time.LocalDateTime> r) {
        return new TaskRow(
                r.get(TASKS.ID),
                r.get(TASKS.TITLE),
                r.get(TASKS.DESCRIPTION),
                toDomainStatus(r.get(TASKS.STATUS)),
                r.get(TASKS.PROJECT_ID),
                r.get(PROJECTS.NAME),
                r.get(TASKS.ASSIGNEE_ID),
                r.get(ASSIGNEE.NAME),
                r.get(TASKS.DUE_DATE),
                r.get(TASKS.CREATED_AT)
        );
    }

    private static TaskStatus toDomainStatus(TasksStatus status) {
        return TaskStatus.valueOf(status.getLiteral());
    }

    private static TasksStatus toJooqStatus(TaskStatus status) {
        return TasksStatus.valueOf(status.name());
    }
}
