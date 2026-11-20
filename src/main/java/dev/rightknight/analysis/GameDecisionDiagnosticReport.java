package dev.rightknight.analysis;

import dev.rightknight.analysis.DecisionProfile.DiagnosticIssue;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

public record GameDecisionDiagnosticReport(ExportMetadata metadata, List<DecisionDiagnosticRow> positions) {
    public GameDecisionDiagnosticReport { positions = List.copyOf(positions); }

    public record DecisionDiagnosticRow(DecisionProfile decision, PlayedObservation played) {}

    /** Scores/loss here come from the persisted played move analysis; clocks come from the import. */
    public record PlayedObservation(String uci, String san, Integer bestEvalCp, Integer bestMateIn,
            Integer playedMoveEvalCp, Integer playedMoveMateIn, Integer storedLossCp, LossCategory lossCategory,
            Long clockBeforeMs, Long clockAfterMs, Long moveTimeMs, Integer playedDeltaSchemaVersion,
            DirectRelationEvents playedEvents, String qualitySource, LossPolicy qualityPolicy, List<DiagnosticIssue> issues) {
        public PlayedObservation { issues = List.copyOf(issues); }
    }

    public record ExportMetadata(String exportSchemaVersion, Instant exportedAt, String gameId, long gameAnalysisId,
            String engineName, Integer requestedDepth, Integer requestedMultiPv, Integer threads,
            ZonedDateTime runCreatedAt, ZonedDateTime runCompletedAt, String exporterGitRevision,
            String eventRulesVersion, String scorePerspective, int topPvLimit, int advantageThresholdCp,
            LossPolicy lossPolicy, int importedMoveCount, int exportedPositionCount, int analyzedPositionCount,
            int missingAnalysisCount, int invalidInputCount, List<DiagnosticIssue> issues) {
        public ExportMetadata { issues = List.copyOf(issues); }
    }
}
