package dev.rightknight.security;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.repository.AppUserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public CurrentUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public Optional<AppUserEntity> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return Optional.empty();
        }

        if (!authentication.isAuthenticated()) {
            return Optional.empty();
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        return appUserRepository.findByUsernameIgnoreCase(authentication.getName());
    }
}
