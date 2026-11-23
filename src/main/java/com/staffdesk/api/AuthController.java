package com.staffdesk.api;

import com.staffdesk.api.Dtos.ChangePasswordRequest;
import com.staffdesk.api.Dtos.LoginRequest;
import com.staffdesk.api.Dtos.TokenResponse;
import com.staffdesk.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /** Kendi parolasını değiştirir. */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(CurrentUser.from(jwt).id(), request);
    }
}
