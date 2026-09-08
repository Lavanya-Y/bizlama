package com.bizlama.api.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DemoDashboardService demoDashboardService;

    public DashboardController(DemoDashboardService demoDashboardService) {
        this.demoDashboardService = demoDashboardService;
    }

    @GetMapping
    public DashboardResponse getDashboard() {
        return demoDashboardService.getDashboard();
    }
}