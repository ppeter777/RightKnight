package dev.rightknight.controller.admin;

import dev.rightknight.service.admin.AdminDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the admin dashboard. Displays aggregated metrics and
 * recent activity.
 */
@Controller
public class AdminDashboardController {
    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/admin")
    public String adminDashboard(Model model) {
        model.addAttribute("totalUsers", dashboardService.getTotalUsers());
        model.addAttribute("activeUsers", dashboardService.getActiveUsers());
        model.addAttribute("totalGames", dashboardService.getTotalGames());
        model.addAttribute("gamesLast24h", dashboardService.getGamesLast24h());
        model.addAttribute("gamesLast7d", dashboardService.getGamesLast7d());
        model.addAttribute("gamesLast30d", dashboardService.getGamesLast30d());
        model.addAttribute("recentActivities", dashboardService.getRecentActivities(10));
        model.addAttribute("recentErrors", dashboardService.getRecentErrors(10));
        model.addAttribute("activePage", "admin-dashboard");
        return "admin/dashboard";
    }
}