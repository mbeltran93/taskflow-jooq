package com.taskflow.service;

import com.taskflow.dto.CreateUserRequest;
import com.taskflow.dto.UserResponse;
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.jooq.generated.tables.records.UsersRecord;
import com.taskflow.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserService::toResponse).toList();
    }

    public UserResponse findById(Long id) {
        return userRepository.findById(id)
                .map(UserService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario " + id + " no encontrado"));
    }

    public UserResponse register(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Ya existe un usuario con ese email");
        }
        UsersRecord created = userRepository.insert(
                request.name(), request.email(), passwordEncoder.encode(request.password()));
        return toResponse(created);
    }

    private static UserResponse toResponse(UsersRecord r) {
        return new UserResponse(r.getId(), r.getName(), r.getEmail(), r.getCreatedAt());
    }
}
