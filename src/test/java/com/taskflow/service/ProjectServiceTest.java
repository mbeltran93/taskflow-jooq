package com.taskflow.service;

import com.taskflow.dto.ProjectRequest;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.jooq.generated.tables.records.ProjectsRecord;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void create_conOwnerInexistente_lanzaResourceNotFound() {
        when(userRepository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> projectService.create(new ProjectRequest("TaskFlow", "desc", 42L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void create_conOwnerValido_delega() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(projectRepository.insert("TaskFlow", "desc", 1L))
                .thenReturn(new ProjectsRecord(10L, "TaskFlow", "desc", 1L, LocalDateTime.now()));

        var response = projectService.create(new ProjectRequest("TaskFlow", "desc", 1L));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.ownerId()).isEqualTo(1L);
    }

    @Test
    void update_sobreProyectoInexistente_lanzaResourceNotFound() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(projectRepository.update(999L, "x", null, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.update(999L, new ProjectRequest("x", null, 1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_sobreProyectoInexistente_lanzaResourceNotFound() {
        when(projectRepository.deleteById(999L)).thenReturn(false);

        assertThatThrownBy(() -> projectService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
