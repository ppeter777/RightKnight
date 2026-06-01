package dev.rightknight.controller;

import dev.rightknight.repository.AppUserRepository;
import dev.rightknight.security.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final CurrentUserService currentUserService;
    private final AppUserRepository appUserRepository;

    public ProfileController(
            CurrentUserService currentUserService,
            AppUserRepository appUserRepository
    ) {
        this.currentUserService = currentUserService;
        this.appUserRepository = appUserRepository;
    }

    @PostMapping("/profile/lichess")
    @Transactional
    public String updateLichessUsername(
            @RequestParam String lichessUsername,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        var currentUser = currentUserService.getCurrentUser(authentication);

        if (currentUser.isEmpty()) {
            return "redirect:/login";
        }

        String normalized = normalizeLichessUsername(lichessUsername);

        if (normalized == null) {
            redirectAttributes.addFlashAttribute("lichessError", "Укажите ник Lichess");
            return "redirect:/";
        }

        var existingUser = appUserRepository.findByLichessUsernameIgnoreCase(normalized);
        if (existingUser.isPresent() && !existingUser.get().getId().equals(currentUser.get().getId())) {
            redirectAttributes.addFlashAttribute("lichessError", "Этот Lichess-ник уже привязан к другому аккаунту");
            return "redirect:/";
        }

        var user = currentUser.get();
        user.setLichessUsername(normalized);
        appUserRepository.save(user);

        redirectAttributes.addFlashAttribute("lichessSuccess", "Lichess-ник сохранён");
        return "redirect:/";
    }

    private String normalizeLichessUsername(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
