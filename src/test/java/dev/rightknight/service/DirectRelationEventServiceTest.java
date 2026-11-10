package dev.rightknight.service;

import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.repository.GameMoveAnalysisCandidateRepository;
import dev.rightknight.repository.GameMoveAnalysisRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectRelationEventServiceTest {
    @Test
    void readsPlayedAndRankedCandidateDeltasIndependentlyFromOneRoot() {
        var move = new GameMoveEntity();
        move.setFenBefore("6qk/4rb2/8/8/R7/2N5/8/7K w - - 0 1");
        move.setUci("c3d5");
        var context = new MoveContextCalculator().calculate(move, null);
        var playedDelta = context.legalMoveFeatures().get("c3d5").attackDelta();
        var alternate = context.legalMoveFeatures().get("c3b1").attackDelta();
        var analysis = new GameMoveAnalysisEntity();
        analysis.setGameMove(move);
        analysis.setAttackSnapshotBefore(context.attackSnapshotBefore());
        analysis.setPlayedAttackDelta(playedDelta);

        var analyses = mock(GameMoveAnalysisRepository.class);
        var candidates = mock(GameMoveAnalysisCandidateRepository.class);
        when(analyses.findById(17L)).thenReturn(Optional.of(analysis));
        when(candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(17L)).thenReturn(List.of(
                candidate(1, "c3b1", alternate), candidate(2, "c3d5", playedDelta), candidate(3, "c3a2", null)));

        var report = new DirectRelationEventService(analyses, candidates).assess(17L);
        assertEquals(List.of(1, 2, 3), report.candidates().stream().map(DirectRelationEventService.Candidate::pvRank).toList());
        var first = report.candidates().getFirst().events();
        assertEquals(Status.AVAILABLE, first.status());
        assertEquals("c3b1", first.moveUci());
        assertTrue(first.targets().stream().noneMatch(t -> t.id().equals("e7")));
        assertTrue(report.played().targets().stream().anyMatch(t -> t.id().equals("e7")));
        assertTrue(report.played().batteries().changes().stream().anyMatch(c -> c.square().equals("d5")));
        assertTrue(first.batteries().changes().stream().noneMatch(c -> c.square().equals("d5")));
        assertTrue(report.played().squareControl().changes().stream().anyMatch(c -> c.square().equals("d5")));
        assertTrue(first.squareControl().changes().stream().anyMatch(c -> c.square().equals("b1")));
        assertTrue(first.squareControl().changes().stream().noneMatch(c -> c.square().equals("d5")));
        assertEquals(Status.MISSING_DATA, report.candidates().get(2).events().squareControl().status());
        assertEquals(report.played(), report.candidates().get(1).events());
        assertEquals(Status.MISSING_DATA, report.candidates().get(2).events().status());
    }

    private static GameMoveAnalysisCandidateEntity candidate(int rank, String uci, AttackDelta delta) {
        var candidate = new GameMoveAnalysisCandidateEntity();
        candidate.setPvRank(rank);
        candidate.setMoveUci(uci);
        candidate.setAttackDelta(delta);
        return candidate;
    }
}
