package com.taskflow.service;

import com.taskflow.dto.CreateUserRequest;
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.jooq.generated.tables.records.UsersRecord;
import com.taskflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void register_conEmailNuevo_hasheaLaPasswordYCrea() {
        when(userRepository.existsByEmail("maria@taskflow.dev")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        when(userRepository.insert("Maria", "maria@taskflow.dev", "hashed-pw"))
                .thenReturn(new UsersRecord(1L, "Maria", "maria@taskflow.dev", "hashed-pw", LocalDateTime.now()));

        var response = userService.register(new CreateUserRequest("Maria", "maria@taskflow.dev", "password123"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("maria@taskflow.dev");
    }

    @Test
    void register_conEmailDuplicado_lanzaDuplicateResource() {
        when(userRepository.existsByEmail("maria@taskflow.dev")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(new CreateUserRequest("Maria", "maria@taskflow.dev", "password123")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void findById_conIdInexistente_lanzaResourceNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
