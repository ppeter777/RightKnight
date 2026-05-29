package dev.rightknight.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserModelAdvice {

    @ModelAttribute("currentUsername")
    public String currentUsername(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        if (!authentication.isAuthenticated()) {
            return null;
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return authentication.getName();
    }

    @ModelAttribute("csrfParameterName")
    public String csrfParameterName(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        return csrfToken == null ? "_csrf" : csrfToken.getParameterName();
    }

    @ModelAttribute("csrfToken")
    public String csrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        return csrfToken == null ? "" : csrfToken.getToken();
    }
}
