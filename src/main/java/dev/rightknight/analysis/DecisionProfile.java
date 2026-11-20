package dev.rightknight.analysis;

import java.util.List;

/** Everything known about the choice before the current player acts. */
public record DecisionProfile(String schemaVersion, RootPosition root, SourceRef source,
        ComplexityAssessment assessment, List<CandidateEvidence> candidateEvidence, List<DiagnosticIssue> issues) {
    public static final String VERSION = "decision-profile-v1";

    public DecisionProfile {
        candidateEvidence = List.copyOf(candidateEvidence);
        issues = List.copyOf(issues);
    }

    public record RootPosition(long gameMoveId, int ply, int moveNumber, String fenBefore, String sideToMove,
            PreviousMove previousMove, RootMetrics metrics) {}

    public record RootMetrics(Boolean inCheck, Boolean previousMoveCapture, Integer legalMovesCount,
            Integer captureMovesCount, Integer checkMovesCount, Integer promotionMovesCount, Integer recaptureMovesCount) {}

    public enum HistoryStatus { AVAILABLE, NO_PREDECESSOR_IN_IMPORT, MISSING_HISTORY, INCONSISTENT_HISTORY }
    public record PreviousMove(HistoryStatus status, Long gameMoveId, String uci, String san) {}

    public record SourceRef(String gameId, long gameAnalysisId, Long gameMoveAnalysisId,
            Integer attackFeaturesVersion, Integer snapshotSchemaVersion) {}

    public record CandidateEvidence(long candidateId, int pvRank, String moveUci, String pvUci,
            Integer selectiveDepth, Integer attackDeltaSchemaVersion, DirectRelationEvents events) {}

    public enum IssueCode {
        MISSING_MOVE_ANALYSIS, INVALID_INPUT, MISSING_HISTORY, INCONSISTENT_HISTORY,
        MISSING_FEN, UNKNOWN_VERSION, UNFINISHED_RUN
    }
    public record DiagnosticIssue(IssueCode code, String path, String message) {}
}
