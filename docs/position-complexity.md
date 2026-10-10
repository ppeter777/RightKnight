# Position complexity: recognition v0.1

`PositionComplexityCalculator` classifies the position **before** a move from the existing `GameMoveAnalysisEntity` metrics. It makes no engine calls or database queries. `complexityFactor()` is always `1.0`, including for a single legal move. No migration or change to expected time is needed.

| Feature | Existing input |
| --- | --- |
| IN_CHECK | inCheck == true |
| SINGLE_LEGAL_MOVE | legalMovesCount == 1 |
| PREVIOUS_MOVE_WAS_CAPTURE | previousMoveCapture == true |
| RECAPTURE_AVAILABLE | recaptureMovesCount > 0 |
| MULTIPLE_RECAPTURES_AVAILABLE | recaptureMovesCount > 1 |

Features overlap: multiple recaptures also sets RECAPTURE_AVAILABLE. A single legal move does not require check. Zero legal moves never sets SINGLE_LEGAL_MOVE; checkmate/stalemate classification is outside this five-feature set.

Recapture counts come from `MoveContextCalculator`, across **all legal moves**, not just MultiPV candidates. A recapture captures the opponent's piece that captured on the immediately preceding move. Capturing a piece after its quiet move is not a recapture. After an en-passant capture, the target is the pawn on its destination square, not the square of the removed pawn. Illegal captures by pinned pieces are excluded by legal move generation. Four promotion choices are four legal recapture moves even if the same pawn captures the same target; this feature does not imply multiple capturing pieces or high difficulty.

## Usage

Inject `PositionComplexityCalculator` into the consumer that needs the features:

```java
PositionComplexity complexity = positionComplexityCalculator.calculate(analysis);
Set<PositionFeature> features = complexity.features();
Set<PositionFeature> unknown = complexity.unknownFeatures();
double factor = complexity.complexityFactor(); // 1.0 in this version
```

For freshly generated data, call after `MoveAnalysisPersistenceService.save(...)` has populated previousMoveCapture and recaptureMovesCount, or on an already saved analysis. `MoveAnalysis.analyzeMove(...)` alone only fills the position metrics; classifying at that point will correctly report history-dependent features as unknown. No production consumer is changed yet: this stage provides the recognition component for inspection, without time-calibration or UI changes.

Both sets are immutable snapshots. A feature in neither set is known false; one in unknownFeatures is not known. `complete()` means all five features are known, not that the model covers every chess pattern. Missing historical metrics are not converted to false/zero. Negative counts, recaptures exceeding legal moves, or positive recaptures after an explicitly non-capturing move are rejected as inconsistent data. Unknown previousMoveCapture remains unknown even if another field is populated.

## Validation

`PositionComplexityCalculatorTest` covers overlaps, empty/partial/historical data, terminal counts, invalid metrics and immutability. `PositionComplexityIntegrationTest` runs the real chesslib position/context calculators on two existing game positions and special cases: check with a single reply, en passant, pins, promotions and unavailable history. No Stockfish is required.

Run:

```sh
./gradlew test --tests 'dev.rightknight.analysis.PositionComplexity*Test' \
  --tests 'dev.rightknight.service.MoveContextCalculatorTest' \
  --tests 'dev.rightknight.service.PositionMetricsCalculatorTest'
```

## Later calibration

Keep recognition separate from assigning difficulty. The next pattern can distinguish a piece attacked by a pawn from a newly created pawn attack. Later, sample positions by recognized features for tests with players of different strengths. Record the position, player's rating band, selected move, correctness and solve time; obtain a relative difficulty judgement before showing the engine answer. Account for whether a position or motif was familiar. Use separate positions for calibration and evaluation. No player study, invitation, personal-data collection or factor fitting is implemented here.
