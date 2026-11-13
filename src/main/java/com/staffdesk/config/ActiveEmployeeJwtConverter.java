package com.staffdesk.config;

import com.staffdesk.domain.Employee;
import com.staffdesk.repository.EmployeeRepository;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * İmzası geçerli bir JWT'yi kimliğe çevirirken personeli veritabanından yeniden okur: hesabı pasife alınan
 * veya silinen personelin token'ı süresi dolmayı beklemeden reddedilir (401) ve yetki (rol) token'daki
 * eski değerden değil güncel kayıttan okunur. Her istekte tek bir birincil anahtar sorgusu yapılır.
 */
@Component
class ActiveEmployeeJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final EmployeeRepository employees;

    ActiveEmployeeJwtConverter(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("Token konusu geçersiz");
        }
        Employee employee = employees.findById(id)
                .filter(Employee::isActive)
                .orElseThrow(() -> new InvalidBearerTokenException("Hesap pasif veya bulunamadı"));
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + employee.getRole().name())), jwt.getSubject());
    }
}
