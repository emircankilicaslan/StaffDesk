package com.staffdesk.api;

import com.staffdesk.api.Dtos.AssetView;
import com.staffdesk.api.Dtos.AssignAssetRequest;
import com.staffdesk.api.Dtos.AssignmentView;
import com.staffdesk.api.Dtos.CreateAssetRequest;
import com.staffdesk.api.Dtos.ReturnAssetRequest;
import com.staffdesk.domain.AssetStatus;
import com.staffdesk.service.AssetService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
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
@RequestMapping("/api")
class AssetController {

    private final AssetService assets;

    AssetController(AssetService assets) {
        this.assets = assets;
    }

    @PostMapping("/assets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    AssetView create(@Valid @RequestBody CreateAssetRequest request) {
        return assets.create(request);
    }

    @GetMapping("/assets")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    Page<AssetView> list(@RequestParam(required = false) AssetStatus status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return assets.list(status, EmployeeController.pageable(page, size));
    }

    @PostMapping("/assets/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    AssignmentView assign(@PathVariable Long id, @Valid @RequestBody AssignAssetRequest request) {
        return assets.assign(id, request);
    }

    @PostMapping("/assets/{id}/return")
    @PreAuthorize("hasRole('ADMIN')")
    AssignmentView giveBack(@PathVariable Long id, @Valid @RequestBody(required = false) ReturnAssetRequest request) {
        return assets.giveBack(id, request);
    }

    @PostMapping("/assets/{id}/retire")
    @PreAuthorize("hasRole('ADMIN')")
    AssetView retire(@PathVariable Long id) {
        return assets.retire(id);
    }

    @GetMapping("/employees/{id}/assets")
    List<AssignmentView> history(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return assets.historyOf(id, CurrentUser.from(jwt));
    }
}
