package dev.rightknight.auth;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.UserStatus;
import dev.rightknight.repository.AppUserRepository;
import dev.rightknight.security.AppUserDetailsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class RegistrationPersistenceTest {

    @Autowired private AppUserRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void enforceMigrationConstraint() {
        // Keep V10's constraint even with the old, incorrectly nullable entity mapping.
        // This makes the registration test reproduce the original INSERT failure.
        new JdbcTemplate(dataSource).execute(
                "ALTER TABLE app_users ALTER COLUMN status SET NOT NULL");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", " new-user@example.org "})
    void registrationPersistsActiveUserWhoCanLogIn(String email) {
        RegisterRequest form = new RegisterRequest();
        form.setUsername("new-user");
        form.setEmail(email);
        form.setPassword("test-password-123");
        form.setPasswordConfirm(form.getPassword());

        String view = new AuthController(users, passwordEncoder).register(
                form, new BeanPropertyBindingResult(form, "form"), new ExtendedModelMap());
        entityManager.flush();
        entityManager.clear();

        assertEquals("redirect:/login?registered", view);
        AppUserEntity saved = users.findByUsernameIgnoreCase("new-user").orElseThrow();
        assertEquals(UserStatus.ACTIVE.name(), saved.getStatus());
        assertEquals(email == null || email.isBlank() ? null : email.trim(), saved.getEmail());
        assertEquals("USER", saved.getRole());
        assertTrue(saved.isEnabled());
        assertTrue(passwordEncoder.matches(form.getPassword(), saved.getPasswordHash()));
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertTrue(new AppUserDetailsService(users).loadUserByUsername("new-user").isEnabled());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"DISABLED", "DELETED"})
    void explicitInactiveStatusIsPreserved(UserStatus status) {
        AppUserEntity user = new AppUserEntity();
        user.setUsername("inactive-user");
        user.setPasswordHash(passwordEncoder.encode("test-password-123"));
        user.setStatus(status.name());
        users.save(user);
        entityManager.flush();
        entityManager.clear();

        assertEquals(status.name(), users.findByUsernameIgnoreCase("inactive-user")
                .orElseThrow().getStatus());
        assertFalse(new AppUserDetailsService(users).loadUserByUsername("inactive-user").isEnabled());
    }
}
