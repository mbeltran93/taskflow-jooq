package com.taskflow.service;

import com.taskflow.dto.TaskRequest;
import com.taskflow.dto.TaskStatus;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.jooq.generated.tables.records.TasksRecord;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TaskRow;
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
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TaskService taskService;

    private static TaskRow row(Long id) {
        return new TaskRow(id, "Titulo", "desc", TaskStatus.TODO, 1L, "Proyecto", null, null, null, LocalDateTime.now());
    }

    @Test
    void create_conProyectoInexistente_lanzaResourceNotFound() {
        when(projectRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.create(new TaskRequest("t", null, 1L, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_conAssigneeInexistente_lanzaResourceNotFound() {
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(userRepository.existsById(55L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.create(new TaskRequest("t", null, 1L, 55L, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_conDatosValidos_creaYRefetcheaConJoins() {
        when(projectRepository.existsById(1L)).thenReturn(true);
        var inserted = new TasksRecord();
        inserted.setId(10L);
        when(taskRepository.insert("t", null, 1L, null, null)).thenReturn(inserted);
        when(taskRepository.findRowById(10L)).thenReturn(Optional.of(row(10L)));

        var response = taskService.create(new TaskRequest("t", null, 1L, null, null));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.projectName()).isEqualTo("Proyecto");
    }

    @Test
    void findAll_conProjectIdInexistente_lanzaResourceNotFound() {
        when(projectRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.findAll(404L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_sobreTareaInexistente_lanzaResourceNotFound() {
        when(taskRepository.updateStatus(999L, TaskStatus.DONE)).thenReturn(false);

        assertThatThrownBy(() -> taskService.updateStatus(999L, TaskStatus.DONE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_sobreTareaExistente_devuelveLaTareaActualizada() {
        when(taskRepository.updateStatus(10L, TaskStatus.DONE)).thenReturn(true);
        when(taskRepository.findRowById(10L)).thenReturn(Optional.of(row(10L)));

        var response = taskService.updateStatus(10L, TaskStatus.DONE);

        assertThat(response.id()).isEqualTo(10L);
    }
}
