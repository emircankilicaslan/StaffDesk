package com.staffdesk.api;

import com.staffdesk.api.Dtos.DashboardView;
import com.staffdesk.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
class DashboardController {

    private final DashboardService dashboard;

    DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    /** Oturumdaki kullanıcının rolüne göre kapsamlandırılmış özet (yönetici: tümü, müdür: birimi, personel: kendisi). */
    @GetMapping
    DashboardView summary(@AuthenticationPrincipal Jwt jwt) {
        return dashboard.summary(CurrentUser.from(jwt));
    }
}
