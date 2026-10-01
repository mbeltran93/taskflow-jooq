package com.taskflow.repository;

import com.taskflow.dto.ProjectTaskStatusCount;
import com.taskflow.dto.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TaskRepository taskRepository;

    @Test
    void findRows_resuelveProjectYAssigneeConJoins() {
        var owner = userRepository.insert("Maria", "maria@taskflow.dev", "hash");
        var assignee = userRepository.insert("Juan", "juan@taskflow.dev", "hash");
        var project = projectRepository.insert("TaskFlow", "API de tareas", owner.getId());
        var task = taskRepository.insert("Escribir README", "detalle", project.getId(), assignee.getId(), LocalDate.of(2026, 12, 1));

        TaskRow row = taskRepository.findRowById(task.getId()).orElseThrow();

        assertThat(row.title()).isEqualTo("Escribir README");
        assertThat(row.projectId()).isEqualTo(project.getId());
        assertThat(row.projectName()).isEqualTo("TaskFlow");
        assertThat(row.assigneeId()).isEqualTo(assignee.getId());
        assertThat(row.assigneeName()).isEqualTo("Juan");
        assertThat(row.status()).isEqualTo(TaskStatus.TODO);
        assertThat(row.dueDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void findRows_sinAssignee_dejaAssigneeEnNull() {
        var owner = userRepository.insert("Maria", "maria@taskflow.dev", "hash");
        var project = projectRepository.insert("TaskFlow", null, owner.getId());
        taskRepository.insert("Tarea sin asignar", null, project.getId(), null, null);

        TaskRow row = taskRepository.findRows(project.getId(), null).get(0);

        assertThat(row.assigneeId()).isNull();
        assertThat(row.assigneeName()).isNull();
    }

    @Test
    void findRows_filtraPorProjectIdYStatus_dinamicamente() {
        var owner = userRepository.insert("Maria", "maria@taskflow.dev", "hash");
        var projectA = projectRepository.insert("Proyecto A", null, owner.getId());
        var projectB = projectRepository.insert("Proyecto B", null, owner.getId());

        var t1 = taskRepository.insert("A - todo", null, projectA.getId(), null, null);
        var t2 = taskRepository.insert("A - en progreso", null, projectA.getId(), null, null);
        taskRepository.updateStatus(t2.getId(), TaskStatus.IN_PROGRESS);
        taskRepository.insert("B - todo", null, projectB.getId(), null, null);

        // Sin filtros: las 3
        assertThat(taskRepository.findRows(null, null)).hasSize(3);

        // Solo por proyecto
        assertThat(taskRepository.findRows(projectA.getId(), null))
                .extracting(TaskRow::id)
                .containsExactlyInAnyOrder(t1.getId(), t2.getId());

        // Solo por status
        assertThat(taskRepository.findRows(null, TaskStatus.IN_PROGRESS))
                .extracting(TaskRow::id)
                .containsExactly(t2.getId());

        // Por proyecto y status combinados
        assertThat(taskRepository.findRows(projectA.getId(), TaskStatus.TODO))
                .extracting(TaskRow::id)
                .containsExactly(t1.getId());
    }

    @Test
    void countByStatusGroupedByProject_agregaConGroupBy() {
        var owner = userRepository.insert("Maria", "maria@taskflow.dev", "hash");
        var projectA = projectRepository.insert("Proyecto A", null, owner.getId());
        var projectB = projectRepository.insert("Proyecto B", null, owner.getId());

        var t1 = taskRepository.insert("A1", null, projectA.getId(), null, null);
        var t2 = taskRepository.insert("A2", null, projectA.getId(), null, null);
        var t3 = taskRepository.insert("A3", null, projectA.getId(), null, null);
        taskRepository.insert("B1", null, projectB.getId(), null, null);

        taskRepository.updateStatus(t1.getId(), TaskStatus.DONE);
        taskRepository.updateStatus(t2.getId(), TaskStatus.DONE);
        taskRepository.updateStatus(t3.getId(), TaskStatus.IN_PROGRESS);

        List<ProjectTaskStatusCount> summary = taskRepository.countByStatusGroupedByProject();

        assertThat(summary).containsExactlyInAnyOrder(
                new ProjectTaskStatusCount(projectA.getId(), "Proyecto A", TaskStatus.DONE, 2L),
                new ProjectTaskStatusCount(projectA.getId(), "Proyecto A", TaskStatus.IN_PROGRESS, 1L),
                new ProjectTaskStatusCount(projectB.getId(), "Proyecto B", TaskStatus.TODO, 1L)
        );
    }

    @Test
    void updateYDelete_funcionanYReportanNotFoundComoFalse() {
        var owner = userRepository.insert("Maria", "maria@taskflow.dev", "hash");
        var project = projectRepository.insert("TaskFlow", null, owner.getId());
        var task = taskRepository.insert("Original", null, project.getId(), null, null);

        boolean updated = taskRepository.update(task.getId(), "Actualizado", "nueva desc", project.getId(), null, null);
        assertThat(updated).isTrue();
        assertThat(taskRepository.findRowById(task.getId()).orElseThrow().title()).isEqualTo("Actualizado");

        assertThat(taskRepository.update(999_999L, "x", null, project.getId(), null, null)).isFalse();

        assertThat(taskRepository.deleteById(task.getId())).isTrue();
        assertThat(taskRepository.findRowById(task.getId())).isEmpty();
        assertThat(taskRepository.deleteById(task.getId())).isFalse();
    }
}
