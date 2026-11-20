package dev.rightknight.controller.admin;

import dev.rightknight.analysis.GameDecisionDiagnosticReport;
import dev.rightknight.security.SecurityConfig;
import dev.rightknight.service.DecisionProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDecisionProfileController.class)
@Import(SecurityConfig.class)
class AdminDecisionProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DecisionProfileService service;
    private static final String PATH = "/admin/games/demo/decision-profile.json";

    @Test
    void onlyAdminMayDownloadTheSelectedRun() throws Exception {
        mvc.perform(get(PATH).param("gameAnalysisId", "17")).andExpect(status().is3xxRedirection());
        mvc.perform(get(PATH).session(session("ROLE_USER")).param("gameAnalysisId", "17")).andExpect(status().isForbidden());
        var admin = session("ROLE_ADMIN");
        mvc.perform(get(PATH).session(admin)).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).session(admin).param("gameAnalysisId", "17").param("topPvLimit", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).session(admin).param("gameAnalysisId", "18")).andExpect(status().isNotFound());
        when(service.exportGame("demo", 17, 5)).thenReturn(Optional.of(new GameDecisionDiagnosticReport(null, List.of())));
        mvc.perform(get(PATH).session(admin).param("gameAnalysisId", "17"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"decision-profile-17.json\""))
                .andExpect(jsonPath("$.positions").isArray());
    }

    private MockHttpSession session(String role) {
        var session = new MockHttpSession();
        var auth = new UsernamePasswordAuthenticationToken("tester", null, List.of(new SimpleGrantedAuthority(role)));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(auth));
        return session;
    }
}
