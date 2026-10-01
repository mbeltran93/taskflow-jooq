package com.taskflow.service;

import com.taskflow.dto.ProjectRequest;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.jooq.generated.tables.records.ProjectsRecord;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream().map(ProjectService::toResponse).toList();
    }

    public ProjectResponse findById(Long id) {
        return projectRepository.findById(id)
                .map(ProjectService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto " + id + " no encontrado"));
    }

    public ProjectResponse create(ProjectRequest request) {
        requireOwnerExists(request.ownerId());
        ProjectsRecord created = projectRepository.insert(request.name(), request.description(), request.ownerId());
        return toResponse(created);
    }

    public ProjectResponse update(Long id, ProjectRequest request) {
        requireOwnerExists(request.ownerId());
        return projectRepository.update(id, request.name(), request.description(), request.ownerId())
                .map(ProjectService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto " + id + " no encontrado"));
    }

    public void delete(Long id) {
        if (!projectRepository.deleteById(id)) {
            throw new ResourceNotFoundException("Proyecto " + id + " no encontrado");
        }
    }

    private void requireOwnerExists(Long ownerId) {
        if (!userRepository.existsById(ownerId)) {
            throw new ResourceNotFoundException("Usuario (owner) " + ownerId + " no existe");
        }
    }

    private static ProjectResponse toResponse(ProjectsRecord r) {
        return new ProjectResponse(r.getId(), r.getName(), r.getDescription(), r.getOwnerId(), r.getCreatedAt());
    }
}
