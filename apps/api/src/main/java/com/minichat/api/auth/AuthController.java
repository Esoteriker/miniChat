package com.minichat.api.auth;

import com.minichat.api.common.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthDtos.TokenResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return new AuthDtos.TokenResponse(authService.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return new AuthDtos.TokenResponse(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh() {
        return new AuthDtos.TokenResponse(authService.refresh(SecurityUtils.currentUser()));
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me() {
        return authService.me(SecurityUtils.currentUserId());
    }
}
