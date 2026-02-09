package com.minichat.api.auth;

import com.minichat.api.common.ConflictException;
import com.minichat.api.common.UnauthorizedException;
import com.minichat.api.event.DomainEventPublisher;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final DomainEventPublisher eventPublisher;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       DomainEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public String register(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        UserEntity saved = userRepository.save(user);

        eventPublisher.publishAudit(saved.getId(), "register", Map.of("email", saved.getEmail()));
        return tokenProvider.generateAccessToken(saved.getId(), saved.getEmail());
    }

    @Transactional(readOnly = true)
    public String login(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        UserEntity user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        eventPublisher.publishAudit(user.getId(), "login", Map.of("email", user.getEmail()));
        return tokenProvider.generateAccessToken(user.getId(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthDtos.MeResponse me(UUID userId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));
        return new AuthDtos.MeResponse(user.getId(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public String refresh(JwtUserPrincipal principal) {
        return tokenProvider.generateAccessToken(principal.userId(), principal.email());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
