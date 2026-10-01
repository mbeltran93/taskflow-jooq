package com.taskflow.service;

import com.taskflow.dto.LoginRequest;
import com.taskflow.exception.InvalidCredentialsException;
import com.taskflow.jooq.generated.tables.records.UsersRecord;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.JwtService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_conCredencialesValidas_devuelveToken() {
        var user = new UsersRecord(1L, "Maria", "maria@taskflow.dev", "hashed", LocalDateTime.now());
        when(userRepository.findByEmail("maria@taskflow.dev")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken("maria@taskflow.dev")).thenReturn("fake-jwt");

        var response = authService.login(new LoginRequest("maria@taskflow.dev", "password123"));

        assertThat(response.token()).isEqualTo("fake-jwt");
    }

    @Test
    void login_conEmailInexistente_lanzaInvalidCredentials() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@taskflow.dev", "x")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_conPasswordIncorrecta_lanzaInvalidCredentials() {
        var user = new UsersRecord(1L, "Maria", "maria@taskflow.dev", "hashed", LocalDateTime.now());
        when(userRepository.findByEmail("maria@taskflow.dev")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mala", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("maria@taskflow.dev", "mala")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
