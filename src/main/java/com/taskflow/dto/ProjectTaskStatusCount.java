package com.taskflow.dto;

/**
 * Fila del reporte "cantidad de tareas por status, agrupado por proyecto"
 * ({@code GET /api/projects/task-status-summary}), resuelto con un {@code GROUP BY} de la DSL de
 * jOOQ (ver {@code TaskRepository#countByStatusGroupedByProject}).
 */
public record ProjectTaskStatusCount(
        Long projectId,
        String projectName,
        TaskStatus status,
        Long count
) {
}
