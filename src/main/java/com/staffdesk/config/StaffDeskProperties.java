package com.staffdesk.config;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "staffdesk")
public record StaffDeskProperties(Jwt jwt, List<LocalDate> holidays, Admin admin) {

    public StaffDeskProperties {
        holidays = holidays == null ? List.of() : List.copyOf(holidays);
        if (jwt == null || jwt.secret() == null || jwt.secret().length() < 32) {
            throw new IllegalStateException("staffdesk.jwt.secret (JWT_SECRET) en az 32 karakter olmalı");
        }
    }

    public record Jwt(String secret, long ttlMinutes) {}

    public record Admin(String email, String password, String fullName) {
        public boolean configured() {
            return email != null && !email.isBlank() && password != null && password.length() >= 8;
        }
    }
}
