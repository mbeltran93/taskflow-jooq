package com.taskflow.service;

import com.taskflow.dto.ProjectTaskStatusCount;
import com.taskflow.dto.TaskRequest;
import com.taskflow.dto.TaskResponse;
import com.taskflow.dto.TaskStatus;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TaskRow;
import com.taskflow.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    public List<TaskResponse> findAll(Long projectId, TaskStatus status) {
        if (projectId != null && !projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Proyecto " + projectId + " no existe");
        }
        return taskRepository.findRows(projectId, status).stream().map(TaskService::toResponse).toList();
    }

    public TaskResponse findById(Long id) {
        return taskRepository.findRowById(id)
                .map(TaskService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea " + id + " no encontrada"));
    }

    public TaskResponse create(TaskRequest request) {
        validateProjectAndAssignee(request.projectId(), request.assigneeId());
        var created = taskRepository.insert(
                request.title(), request.description(), request.projectId(), request.assigneeId(), request.dueDate());
        return findById(created.getId());
    }

    public TaskResponse update(Long id, TaskRequest request) {
        validateProjectAndAssignee(request.projectId(), request.assigneeId());
        boolean updated = taskRepository.update(
                id, request.title(), request.description(), request.projectId(), request.assigneeId(), request.dueDate());
        if (!updated) {
            throw new ResourceNotFoundException("Tarea " + id + " no encontrada");
        }
        return findById(id);
    }

    public TaskResponse updateStatus(Long id, TaskStatus status) {
        boolean updated = taskRepository.updateStatus(id, status);
        if (!updated) {
            throw new ResourceNotFoundException("Tarea " + id + " no encontrada");
        }
        return findById(id);
    }

    public void delete(Long id) {
        if (!taskRepository.deleteById(id)) {
            throw new ResourceNotFoundException("Tarea " + id + " no encontrada");
        }
    }

    /** Reporte agregado (GROUP BY con la DSL de jOOQ): cantidad de tareas por status, por proyecto. */
    public List<ProjectTaskStatusCount> taskStatusSummary() {
        return taskRepository.countByStatusGroupedByProject();
    }

    private void validateProjectAndAssignee(Long projectId, Long assigneeId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Proyecto " + projectId + " no existe");
        }
        if (assigneeId != null && !userRepository.existsById(assigneeId)) {
            throw new ResourceNotFoundException("Usuario (assignee) " + assigneeId + " no existe");
        }
    }

    private static TaskResponse toResponse(TaskRow row) {
        return new TaskResponse(
                row.id(), row.title(), row.description(), row.status(),
                row.projectId(), row.projectName(),
                row.assigneeId(), row.assigneeName(),
                row.dueDate(), row.createdAt());
    }
}
