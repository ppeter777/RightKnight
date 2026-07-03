package dev.rightknight.controller.admin;

import dev.rightknight.service.admin.AdminActivityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller exposing the activity log for administrators.
 */
@Controller
@RequestMapping("/admin/activity")
public class AdminActivityController {
    private final AdminActivityService adminActivityService;

    public AdminActivityController(AdminActivityService adminActivityService) {
        this.adminActivityService = adminActivityService;
    }

    @GetMapping
    public String activityLog(@RequestParam(name = "limit", required = false, defaultValue = "100") int limit,
                              Model model) {
        model.addAttribute("activities", adminActivityService.getRecentActivities(limit));
        model.addAttribute("limit", limit);
        model.addAttribute("activePage", "admin-activity");
        return "admin/activity";
    }
}