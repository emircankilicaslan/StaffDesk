package com.staffdesk.api;

import com.staffdesk.api.Dtos.DecisionRequest;
import com.staffdesk.api.Dtos.LeaveCreateRequest;
import com.staffdesk.api.Dtos.LeaveView;
import com.staffdesk.domain.LeaveStatus;
import com.staffdesk.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaves")
class LeaveController {

    private final LeaveService leaves;

    LeaveController(LeaveService leaves) {
        this.leaves = leaves;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LeaveView request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LeaveCreateRequest request) {
        return leaves.request(CurrentUser.from(jwt).id(), request);
    }

    @GetMapping("/mine")
    Page<LeaveView> mine(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return leaves.mine(CurrentUser.from(jwt).id(),
                EmployeeController.pageable(page, size, Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by("id"))));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    Page<LeaveView> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "PENDING") LeaveStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return leaves.listByStatus(CurrentUser.from(jwt), status, EmployeeController.pageable(page, size));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    LeaveView approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionRequest body) {
        return leaves.approve(id, CurrentUser.from(jwt), body == null ? null : body.note());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    LeaveView reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionRequest body) {
        return leaves.reject(id, CurrentUser.from(jwt), body == null ? null : body.note());
    }

    @PostMapping("/{id}/cancel")
    LeaveView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return leaves.cancel(id, CurrentUser.from(jwt).id());
    }
}
