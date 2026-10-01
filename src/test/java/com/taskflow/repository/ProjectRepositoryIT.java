package com.taskflow.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void insertUpdateDelete_ciclocompleto() {
        var owner = userRepository.insert("Maria", "maria@taskflow.dev", "hash");

        var created = projectRepository.insert("TaskFlow", "desc original", owner.getId());
        assertThat(created.getId()).isNotNull();

        var updated = projectRepository.update(created.getId(), "TaskFlow v2", "desc nueva", owner.getId())
                .orElseThrow();
        assertThat(updated.getName()).isEqualTo("TaskFlow v2");
        assertThat(updated.getDescription()).isEqualTo("desc nueva");

        assertThat(projectRepository.deleteById(created.getId())).isTrue();
        assertThat(projectRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void update_sobreIdInexistente_devuelveVacio() {
        var owner = userRepository.insert("Maria", "maria@taskflow.dev", "hash");

        assertThat(projectRepository.update(999_999L, "x", null, owner.getId())).isEmpty();
    }

    @Test
    void deleteProject_borraEnCascadaSusPropiosDatosDeOwner_peroNoAlOwner() {
        var owner = userRepository.insert("Maria", "maria@taskflow.dev", "hash");
        var project = projectRepository.insert("TaskFlow", null, owner.getId());

        projectRepository.deleteById(project.getId());

        assertThat(userRepository.findById(owner.getId())).isPresent();
    }
}
