package com.taskflow.dto;

/**
 * Enum de dominio, independiente del enum que genera jOOQ a partir de la columna MySQL
 * {@code tasks.status}. El mapeo entre ambos vive en {@code TaskRepository} para no filtrar
 * el tipo generado hacia la capa de servicio/controller.
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
