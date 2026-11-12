package com.staffdesk.config;

import com.staffdesk.domain.Employee;
import com.staffdesk.domain.Role;
import com.staffdesk.repository.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Veritabanında hiç yönetici yoksa, yapılandırmadaki bilgilerle ilk yöneticiyi oluşturur. */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final StaffDeskProperties props;
    private final EmployeeRepository employees;
    private final PasswordEncoder encoder;
    private final Clock clock;

    AdminBootstrap(StaffDeskProperties props, EmployeeRepository employees, PasswordEncoder encoder, Clock clock) {
        this.props = props;
        this.employees = employees;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var admin = props.admin();
        if (admin == null || !admin.configured() || employees.existsByRole(Role.ADMIN)) {
            return;
        }
        employees.save(new Employee("ADM-0001", admin.fullName() == null ? "Sistem Yöneticisi" : admin.fullName(),
                admin.email().trim().toLowerCase(), encoder.encode(admin.password()), Role.ADMIN, "Yönetici", null,
                LocalDate.now(clock), LocalDateTime.now(clock)));
        log.info("İlk yönetici oluşturuldu: {}", admin.email());
    }
}
