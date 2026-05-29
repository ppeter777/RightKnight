package dev.rightknight.auth;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.repository.AppUserRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String login(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "registered", required = false) String registered,
            Model model
    ) {
        model.addAttribute("hasError", error != null);
        model.addAttribute("registered", registered != null);
        return "pages/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterRequest());
        model.addAttribute("errorMessage", null);
        return "pages/register";
    }

    @PostMapping("/register")
    @Transactional
    public String register(
            @Valid @ModelAttribute("form") RegisterRequest form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage", "Проверьте корректность заполнения формы");
            return "pages/register";
        }

        String username = form.getUsername().trim();
        String email = normalizeEmail(form.getEmail());

        if (!form.getPassword().equals(form.getPasswordConfirm())) {
            model.addAttribute("errorMessage", "Пароли не совпадают");
            return "pages/register";
        }

        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            model.addAttribute("errorMessage", "Пользователь с таким именем уже существует");
            return "pages/register";
        }

        if (email != null && appUserRepository.existsByEmailIgnoreCase(email)) {
            model.addAttribute("errorMessage", "Пользователь с таким email уже существует");
            return "pages/register";
        }

        AppUserEntity user = new AppUserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(username);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setRole("USER");
        user.setEnabled(true);

        appUserRepository.save(user);

        return "redirect:/login?registered";
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim();
    }
}
