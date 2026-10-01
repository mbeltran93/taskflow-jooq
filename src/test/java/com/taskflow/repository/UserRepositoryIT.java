package com.taskflow.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private UserRepository userRepository;

    @Test
    void insertYFindById_funcionan() {
        var inserted = userRepository.insert("Maria", "maria@taskflow.dev", "hashed-pw");

        assertThat(inserted.getId()).isNotNull();

        var found = userRepository.findById(inserted.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Maria");
        assertThat(found.getEmail()).isEqualTo("maria@taskflow.dev");
        assertThat(found.getPasswordHash()).isEqualTo("hashed-pw");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void existsByEmail_distingueUsuariosExistentesDeInexistentes() {
        userRepository.insert("Maria", "maria@taskflow.dev", "hash");

        assertThat(userRepository.existsByEmail("maria@taskflow.dev")).isTrue();
        assertThat(userRepository.existsByEmail("nadie@taskflow.dev")).isFalse();
    }

    @Test
    void findByEmail_esInsensibleAMayusculasPorElCollationPorDefectoDeMySQL() {
        userRepository.insert("Maria", "Maria@Taskflow.dev", "hash");

        assertThat(userRepository.findByEmail("maria@taskflow.dev")).isPresent();
    }

    @Test
    void findAll_ordenaPorId() {
        var u1 = userRepository.insert("A", "a@taskflow.dev", "hash");
        var u2 = userRepository.insert("B", "b@taskflow.dev", "hash");

        assertThat(userRepository.findAll())
                .extracting(r -> r.getId())
                .containsExactly(u1.getId(), u2.getId());
    }
}
