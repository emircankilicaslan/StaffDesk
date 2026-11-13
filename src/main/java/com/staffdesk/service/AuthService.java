package com.staffdesk.service;

import com.staffdesk.api.Dtos.LoginRequest;
import com.staffdesk.api.Dtos.TokenResponse;
import com.staffdesk.config.StaffDeskProperties;
import com.staffdesk.domain.Employee;
import com.staffdesk.repository.EmployeeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // Kullanıcı yoksa da hash karşılaştırması yapılır: yanıt süresiyle hesap varlığı sızmasın.
    private final String dummyHash;

    private final EmployeeRepository employees;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final StaffDeskProperties props;
    private final Clock clock;
    private final LoginAttemptLimiter limiter;

    public AuthService(EmployeeRepository employees, PasswordEncoder encoder, JwtEncoder jwtEncoder,
            StaffDeskProperties props, Clock clock, LoginAttemptLimiter limiter) {
        this.employees = employees;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.clock = clock;
        this.limiter = limiter;
        this.dummyHash = encoder.encode("dummy-password");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String key = request.email().trim();
        limiter.checkAllowed(key);

        Employee e = employees.findByEmailIgnoreCase(key).orElse(null);
        boolean ok = encoder.matches(request.password(), e == null ? dummyHash : e.getPasswordHash());
        if (e == null || !ok || !e.isActive()) {
            limiter.recordFailure(key);
            throw new BadCredentialsException("E-posta veya şifre hatalı");
        }
        limiter.recordSuccess(key);

        Instant now = Instant.now(clock);
        Duration ttl = Duration.ofMinutes(props.jwt().ttlMinutes());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(e.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("role", e.getRole().name())
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, ttl.toSeconds(), e.getRole());
    }
}
