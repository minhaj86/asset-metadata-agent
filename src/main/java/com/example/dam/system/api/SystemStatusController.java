package com.example.dam.system.api;

import com.example.dam.system.application.SystemStatusService;
import com.example.dam.system.application.SystemStatusService.SystemStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "System", description = "Capability status and governance settings for clients")
class SystemStatusController {

    private final SystemStatusService statusService;

    SystemStatusController(SystemStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/api/system/status")
    @Operation(summary = "Environment, AI / vector search / DAM availability and governance thresholds")
    SystemStatus status() {
        return statusService.status();
    }
}
