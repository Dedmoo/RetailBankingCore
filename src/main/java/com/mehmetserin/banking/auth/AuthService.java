package com.mehmetserin.banking.auth;

import com.mehmetserin.banking.auth.dto.AuthResponse;
import com.mehmetserin.banking.auth.dto.LoginRequest;
import com.mehmetserin.banking.auth.dto.RegisterRequest;
import com.mehmetserin.banking.account.HouseFundingService;
import com.mehmetserin.banking.common.exception.InvalidCredentialsException;
import com.mehmetserin.banking.common.exception.UsernameAlreadyTakenException;
import com.mehmetserin.banking.security.JwtService;
import com.mehmetserin.banking.user.AppUser;
import com.mehmetserin.banking.user.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (HouseFundingService.SYSTEM_USERNAME.equalsIgnoreCase(request.username())) {
            throw new UsernameAlreadyTakenException(request.username());
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyTakenException(request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new UsernameAlreadyTakenException(request.email());
        }
        AppUser user = new AppUser(request.username(), request.email(), passwordEncoder.encode(request.password()));
        userRepository.save(user);
        String token = jwtService.generateToken(user.getUsername());
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds());
    }

    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String token = jwtService.generateToken(user.getUsername());
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds());
    }
}
