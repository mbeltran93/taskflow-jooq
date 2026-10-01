package com.taskflow.repository;

import com.taskflow.dto.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Proyeccion de una tarea con su proyecto y su assignee ya resueltos (join), tal como la
 * devuelve {@link TaskRepository}. No es un record jOOQ: es la forma que necesita la API,
 * construida con la DSL a partir de varias tablas.
 */
public record TaskRow(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Long projectId,
        String projectName,
        Long assigneeId,
        String assigneeName,
        LocalDate dueDate,
        LocalDateTime createdAt
) {
}
