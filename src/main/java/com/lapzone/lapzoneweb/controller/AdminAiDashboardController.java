package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.AdminAiDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiDashboardController {
    private final AdminAiDashboardService dashboard;

    public AdminAiDashboardController(AdminAiDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    public String show(@RequestParam(defaultValue = "7d") String period,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("dashboard", dashboard.load(period, page));
        return "admin/ai-dashboard";
    }
}
