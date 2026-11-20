package dev.rightknight.controller.admin;

import dev.rightknight.analysis.GameDecisionDiagnosticReport;
import dev.rightknight.service.DecisionProfileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminDecisionProfileController {
    private final DecisionProfileService profiles;

    public AdminDecisionProfileController(DecisionProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping(value = "/admin/games/{gameId}/decision-profile.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GameDecisionDiagnosticReport> export(@PathVariable String gameId,
            @RequestParam long gameAnalysisId, @RequestParam(defaultValue = "5") int topPvLimit) {
        if (topPvLimit < 1) return ResponseEntity.badRequest().build();
        return profiles.exportGame(gameId, gameAnalysisId, topPvLimit)
                .map(report -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"decision-profile-" + gameAnalysisId + ".json\"")
                        .body(report))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
