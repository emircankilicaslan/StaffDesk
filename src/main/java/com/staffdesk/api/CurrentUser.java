package com.staffdesk.api;

import com.staffdesk.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;

/** Doğrulanmış JWT'den çıkarılan kullanıcı kimliği ve rolü. */
public record CurrentUser(Long id, Role role) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(Long.valueOf(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString("role")));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
