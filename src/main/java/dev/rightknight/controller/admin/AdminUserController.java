package dev.rightknight.controller.admin;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.UserRole;
import dev.rightknight.model.UserStatus;
import dev.rightknight.security.CurrentUserService;
import dev.rightknight.service.admin.AdminUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for administering users. Provides endpoints for listing users,
 * viewing details and performing actions such as enabling/disabling,
 * granting/revoking admin privileges and deleting/clearing games.
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AdminUserService adminUserService;
    private final CurrentUserService currentUserService;

    public AdminUserController(AdminUserService adminUserService, CurrentUserService currentUserService) {
        this.adminUserService = adminUserService;
        this.currentUserService = currentUserService;
    }

    /** Lists users with optional filters. */
    @GetMapping
    public String listUsers(@RequestParam(required = false) String q,
                            @RequestParam(required = false) String lichess,
                            @RequestParam(required = false) UserStatus status,
                            @RequestParam(required = false) UserRole role,
                            Model model) {
        List<AdminUserService.UserSummary> users = adminUserService.findUsers(q, lichess, status, role);
        model.addAttribute("users", users);
        model.addAttribute("filterQ", q);
        model.addAttribute("filterLichess", lichess);
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterRole", role);
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("activePage", "admin-users");
        return "admin/users";
    }

    /** Shows details for a single user. */
    @GetMapping("/{userId}")
    public String userDetails(@PathVariable("userId") Long userId, Model model) {
        AppUserEntity user = adminUserService.requireUser(userId);
        AdminUserService.UserSummary summary = adminUserService.buildSummary(user);
        model.addAttribute("user", user);
        model.addAttribute("summary", summary);
        return "admin/user-details";
    }

    /** Disables a user. */
    @PostMapping("/{userId}/disable")
    public String disableUser(@PathVariable Long userId) {
        AppUserEntity user = adminUserService.requireUser(userId);
        adminUserService.disableUser(user);
        return "redirect:/admin/users/" + userId;
    }

    /** Enables a user. */
    @PostMapping("/{userId}/enable")
    public String enableUser(@PathVariable Long userId) {
        AppUserEntity user = adminUserService.requireUser(userId);
        adminUserService.enableUser(user);
        return "redirect:/admin/users/" + userId;
    }

    /** Clears all games of a user. */
    @PostMapping("/{userId}/clear-games")
    public String clearGames(@PathVariable Long userId) {
        AppUserEntity user = adminUserService.requireUser(userId);
        adminUserService.clearUserGames(user);
        return "redirect:/admin/users/" + userId;
    }

    /** Grants admin role to a user. */
    @PostMapping("/{userId}/grant-admin")
    public String grantAdmin(@PathVariable Long userId) {
        AppUserEntity user = adminUserService.requireUser(userId);
        adminUserService.grantAdminRole(user);
        return "redirect:/admin/users/" + userId;
    }

    /** Revokes admin role from a user. */
    @PostMapping("/{userId}/revoke-admin")
    public String revokeAdmin(@PathVariable Long userId) {
        AppUserEntity user = adminUserService.requireUser(userId);
        adminUserService.revokeAdminRole(user);
        return "redirect:/admin/users/" + userId;
    }

    /** Soft deletes a user. */
    @PostMapping("/{userId}/delete")
    public String deleteUser(@PathVariable Long userId) {
        AppUserEntity user = adminUserService.requireUser(userId);
        adminUserService.deleteUser(user);
        return "redirect:/admin/users";
    }
}