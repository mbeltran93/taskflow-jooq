package com.taskflow.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Incluye {@code projectName} y {@code assigneeName} resueltos con un join de jOOQ
 * (ver {@code TaskRepository#findAll}) para no obligar al cliente a pedir cada relacion aparte.
 */
public record TaskResponse(
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
