package dev.rightknight.service;

import dev.rightknight.analysis.*;
import dev.rightknight.analysis.DecisionProfile.*;
import dev.rightknight.analysis.GameDecisionDiagnosticReport.*;
import dev.rightknight.model.*;
import dev.rightknight.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.rightknight.analysis.DecisionProfile.IssueCode.*;

@Service
public class DecisionProfileService {
    private static final LossPolicy POLICY = LossPolicy.v2();
    private static final int ADVANTAGE_CP = 100;

    private final GameAnalysisRepository runs;
    private final GameMoveRepository moves;
    private final GameMoveAnalysisRepository analyses;
    private final GameMoveAnalysisCandidateRepository candidates;
    private final ComplexityAssessmentCalculator complexity;
    private final DirectRelationEventCalculator events = new DirectRelationEventCalculator();
    private final String revision;

    public DecisionProfileService(GameAnalysisRepository runs, GameMoveRepository moves,
            GameMoveAnalysisRepository analyses, GameMoveAnalysisCandidateRepository candidates,
            ComplexityAssessmentCalculator complexity, @Value("${rightknight.build.revision:}") String revision) {
        this.runs = runs;
        this.moves = moves;
        this.analyses = analyses;
        this.candidates = candidates;
        this.complexity = complexity;
        this.revision = revision.isBlank() ? null : revision;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<GameDecisionDiagnosticReport> exportGame(String gameId, long runId, int topPvLimit) {
        if (topPvLimit <= 0) throw new IllegalArgumentException("topPvLimit must be positive");
        var selected = runs.findByIdAndGame_Id(runId, gameId);
        if (selected.isEmpty()) return Optional.empty();
        var run = selected.get();
        var gameMoves = moves.findByGame_IdOrderByPlyAsc(gameId);
        var byMove = analyses.findByGameAnalysis_Id(runId).stream()
                .collect(Collectors.toMap(a -> a.getGameMove().getId(), Function.identity()));
        var byAnalysis = candidates.findByGameMoveAnalysis_GameAnalysis_IdOrderByPvRankAsc(runId).stream()
                .collect(Collectors.groupingBy(c -> c.getGameMoveAnalysis().getId()));
        List<DecisionDiagnosticRow> rows = new ArrayList<>();
        GameMoveEntity previous = null;
        int missing = 0, invalid = 0;
        for (var move : gameMoves) {
            var analysis = byMove.get(move.getId());
            var alternatives = analysis == null ? List.<GameMoveAnalysisCandidateEntity>of()
                    : byAnalysis.getOrDefault(analysis.getId(), List.of());
            var decision = profile(gameId, run, move, previous, analysis, alternatives, topPvLimit);
            var played = played(move, analysis);
            rows.add(new DecisionDiagnosticRow(decision, played));
            if (analysis == null) missing++;
            if (decision.issues().stream().anyMatch(i -> i.code() == INVALID_INPUT)
                    || played.issues().stream().anyMatch(i -> i.code() == INVALID_INPUT)) invalid++;
            previous = move;
        }

        List<DiagnosticIssue> issues = new ArrayList<>();
        if (revision == null) issues.add(new DiagnosticIssue(UNKNOWN_VERSION, "exporterGitRevision", "Ревизия сборки не указана."));
        if (run.getCompletedAt() == null) issues.add(new DiagnosticIssue(UNFINISHED_RUN, "runCompletedAt", "Время завершения запуска отсутствует."));
        var metadata = new ExportMetadata("decision-export-v1", Instant.now(), gameId, runId, run.getEngineName(),
                run.getRequestedDepth(), run.getMultiPv(), run.getThreads(), run.getCreatedAt(), run.getCompletedAt(),
                revision, DirectRelationEventCalculator.MODEL_VERSION, "ROOT_SIDE_TO_MOVE", topPvLimit,
                ADVANTAGE_CP, POLICY, gameMoves.size(), rows.size(), rows.size() - missing, missing, invalid, issues);
        return Optional.of(new GameDecisionDiagnosticReport(metadata, rows));
    }

    private DecisionProfile profile(String gameId, GameAnalysisEntity run, GameMoveEntity move,
            GameMoveEntity previous, GameMoveAnalysisEntity analysis, List<GameMoveAnalysisCandidateEntity> alternatives,
            int limit) {
        List<DiagnosticIssue> issues = new ArrayList<>();
        var history = previousMove(move, previous, issues);
        String side = sideToMove(move, issues);
        var root = new RootPosition(move.getId(), move.getPly(), move.getMoveNumber(), move.getFenBefore(),
                side, history, metrics(analysis));
        if (analysis == null) {
            issues.add(new DiagnosticIssue(MISSING_MOVE_ANALYSIS, "assessment", "Для полухода " + move.getPly() + " нет анализа в выбранном запуске."));
            return new DecisionProfile(DecisionProfile.VERSION, root, new SourceRef(gameId, run.getId(), null, null, null),
                    null, List.of(), issues);
        }
        var snapshot = analysis.getAttackSnapshotBefore();
        var source = new SourceRef(gameId, run.getId(), analysis.getId(), analysis.getAttackFeaturesVersion(),
                snapshot == null ? null : snapshot.schemaVersion());
        if (source.attackFeaturesVersion() == null) issues.add(new DiagnosticIssue(UNKNOWN_VERSION,
                "source.attackFeaturesVersion", "Версия извлечения графа не сохранена."));
        if (source.snapshotSchemaVersion() == null) issues.add(new DiagnosticIssue(UNKNOWN_VERSION,
                "source.snapshotSchemaVersion", "Исходный снимок графа отсутствует."));

        ComplexityAssessment assessment = null;
        List<CandidateEvidence> evidence = new ArrayList<>();
        if (issues.stream().noneMatch(i -> i.code() == INVALID_INPUT || i.code() == INCONSISTENT_HISTORY)) {
            try {
                assessment = complexity.calculate(analysis, alternatives, move.getFenBefore(), run.getMultiPv(), limit, POLICY, ADVANTAGE_CP);
            } catch (IllegalArgumentException ex) {
                issues.add(new DiagnosticIssue(INVALID_INPUT, "assessment", ex.getMessage()));
            }
        }
        if (assessment != null) {
            // The calculator checks parent, unique rank/UCI and legality; the query orders the same rows by rank.
            for (int i = 0; i < alternatives.size(); i++) {
                var candidate = alternatives.get(i);
                var assessed = assessment.candidates().get(i);
                if (candidate.getPvRank() != assessed.pvRank() || !candidate.getMoveUci().equals(assessed.moveUci()))
                    throw new IllegalStateException("Candidate order/identity mismatch in move analysis " + analysis.getId());
                var delta = candidate.getAttackDelta();
                evidence.add(new CandidateEvidence(candidate.getId(), candidate.getPvRank(), candidate.getMoveUci(),
                        candidate.getPvUci(), candidate.getSelectiveDepth(), delta == null ? null : delta.schemaVersion(),
                        events.calculate(candidate.getMoveUci(), snapshot, delta)));
                if (delta == null) issues.add(new DiagnosticIssue(UNKNOWN_VERSION, "candidateEvidence[" + i + "].attackDeltaSchemaVersion",
                        "Дельта графа кандидата не сохранена."));
            }
        }
        return new DecisionProfile(DecisionProfile.VERSION, root, source, assessment, evidence, issues);
    }

    private static PreviousMove previousMove(GameMoveEntity move, GameMoveEntity previous, List<DiagnosticIssue> issues) {
        if (move.getPly() == 1 && previous == null)
            return new PreviousMove(HistoryStatus.NO_PREDECESSOR_IN_IMPORT, null, null, null);
        if (previous == null || previous.getPly() + 1 != move.getPly() || previous.getFenAfter() == null || move.getFenBefore() == null) {
            issues.add(new DiagnosticIssue(MISSING_HISTORY, "root.previousMove", "Предыдущий полуход или FEN перехода недоступен."));
            return new PreviousMove(HistoryStatus.MISSING_HISTORY, null, null, null);
        }
        if (!previous.getFenAfter().equals(move.getFenBefore())) {
            issues.add(new DiagnosticIssue(INCONSISTENT_HISTORY, "root.previousMove", "FEN после предыдущего полухода отличается от исходного FEN."));
            return new PreviousMove(HistoryStatus.INCONSISTENT_HISTORY, null, null, null);
        }
        return new PreviousMove(HistoryStatus.AVAILABLE, previous.getId(), previous.getUci(), previous.getSan());
    }

    private static String sideToMove(GameMoveEntity move, List<DiagnosticIssue> issues) {
        if (move.getFenBefore() == null) {
            issues.add(new DiagnosticIssue(MISSING_FEN, "root.fenBefore", "Исходный FEN не сохранён."));
            return null;
        }
        var fields = move.getFenBefore().split(" ");
        if (fields.length < 4 || !Set.of("w", "b").contains(fields[1])) {
            issues.add(new DiagnosticIssue(INVALID_INPUT, "root.fenBefore", "Неверный формат FEN или сторона хода."));
            return null;
        }
        boolean white = fields[1].equals("w");
        if (white != move.isWhiteMove()) issues.add(new DiagnosticIssue(INVALID_INPUT, "root.sideToMove",
                "Сторона хода в FEN расходится с сохранённым whiteMove."));
        return white ? "WHITE" : "BLACK";
    }

    private static RootMetrics metrics(GameMoveAnalysisEntity a) {
        if (a == null) return null;
        return new RootMetrics(a.getInCheck(), a.getPreviousMoveCapture(), a.getLegalMovesCount(), a.getCaptureMovesCount(),
                a.getCheckMovesCount(), a.getPromotionMovesCount(), a.getRecaptureMovesCount());
    }

    private PlayedObservation played(GameMoveEntity move, GameMoveAnalysisEntity a) {
        List<DiagnosticIssue> issues = new ArrayList<>();
        var loss = a == null ? null : a.getLossCp();
        LossCategory category = LossCategory.UNKNOWN;
        if (loss != null && loss < 0) issues.add(new DiagnosticIssue(INVALID_INPUT, "storedLossCp", "Сохранена отрицательная потеря оценки."));
        else if (loss != null) category = POLICY.classify(loss.longValue());
        var delta = a == null ? null : a.getPlayedAttackDelta();
        if (a != null && delta == null) issues.add(new DiagnosticIssue(UNKNOWN_VERSION, "playedDeltaSchemaVersion", "Дельта сыгранного хода не сохранена."));
        return new PlayedObservation(move.getUci(), move.getSan(), a == null ? null : a.getBestEvalCp(),
                a == null ? null : a.getBestMateIn(), a == null ? null : a.getPlayedMoveEvalCp(),
                a == null ? null : a.getPlayedMoveMateIn(), loss, category, move.getClockBeforeMs(), move.getClockAfterMs(),
                move.getMoveTimeMs(), delta == null ? null : delta.schemaVersion(),
                events.calculate(move.getUci(), a == null ? null : a.getAttackSnapshotBefore(), delta),
                a == null ? null : "STORED_MOVE_ANALYSIS", POLICY, issues);
    }
}
