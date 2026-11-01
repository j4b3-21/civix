package com.civix.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civix.auth.dto.LoginRequest;
import com.civix.auth.dto.RegisterRequest;
import com.civix.auth.dto.UserDto;
import com.civix.auth.service.AuthCookies;
import com.civix.auth.service.AuthResult;
import com.civix.auth.service.AuthService;
import com.civix.common.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final AuthCookies cookies;

    public AuthController(AuthService auth, AuthCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        AuthResult result = auth.register(request, http.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.status(HttpStatus.CREATED).headers(cookies.issue(result)).body(result.user());
    }

    @PostMapping("/login")
    public ResponseEntity<UserDto> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthResult result = auth.login(request, http.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.ok().headers(cookies.issue(result)).body(result.user());
    }

    @PostMapping("/refresh")
    public ResponseEntity<UserDto> refresh(@CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken,
                                           HttpServletRequest http) {
        AuthResult result = auth.refresh(refreshToken, http.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.ok().headers(cookies.issue(result)).body(result.user());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.noContent().headers(cookies.clear()).build();
    }

    @GetMapping("/me")
    public UserDto me() {
        return auth.me(CurrentUser.id());
    }
}
