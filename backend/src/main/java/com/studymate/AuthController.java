package com.studymate;

import com.studymate.ApiDtos.AuthResponse;
import com.studymate.ApiDtos.LoginRequest;
import com.studymate.ApiDtos.RegisterRequest;
import com.studymate.ApiDtos.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = users.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password())));
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse(new UserPrincipal(user)));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        return authResponse((UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal UserPrincipal user) {
        return new UserView(user.getId(), user.getName(), user.getUsername());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() { return ResponseEntity.noContent().build(); }

    private AuthResponse authResponse(UserPrincipal user) {
        return new AuthResponse(jwtService.createToken(user), new UserView(user.getId(), user.getName(), user.getUsername()));
    }
}