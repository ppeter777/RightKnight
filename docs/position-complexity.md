# Position complexity: recognition v0.1

`PositionComplexityCalculator` classifies the position **before** a move from the existing `GameMoveAnalysisEntity` metrics. It makes no engine calls or database queries. `complexityFactor()` is always `1.0`, including for a single legal move. No migration or change to expected time is needed.

| Feature | Existing input |
| --- | --- |
| IN_CHECK | inCheck == true |
| SINGLE_LEGAL_MOVE | legalMovesCount == 1 |
| PREVIOUS_MOVE_WAS_CAPTURE | previousMoveCapture == true |
| RECAPTURE_AVAILABLE | recaptureMovesCount > 0 |
| MULTIPLE_RECAPTURES_AVAILABLE | recaptureMovesCount > 1 |
| OTHER_CAPTURE_AVAILABLE | previousMoveCapture == true and captureMovesCount > recaptureMovesCount |

Features overlap: multiple recaptures also sets RECAPTURE_AVAILABLE. A single legal move does not require check. Zero legal moves never sets SINGLE_LEGAL_MOVE; checkmate/stalemate classification is outside this six-feature set.

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

Both sets are immutable snapshots. A feature in neither set is known false; one in unknownFeatures is not known. `complete()` means all six features are known, not that the model covers every chess pattern. Missing historical metrics are not converted to false/zero. Negative counts, recaptures exceeding legal moves, or positive recaptures after an explicitly non-capturing move are rejected as inconsistent data. Unknown previousMoveCapture remains unknown even if another field is populated.

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

## Other captures and MultiPV candidate categories

OTHER_CAPTURE_AVAILABLE means that, **after an opponent capture**, a legal move can capture another opponent piece or pawn instead of the piece that just captured. It can coexist with RECAPTURE_AVAILABLE; it does not require a recapture to exist. After a quiet opponent move it is false, even if captures are available. It uses all legal captures, independently of which moves appear in MultiPV. Promotions are counted per legal move consistently in both capture counters.

For unknown history/counts the feature is unknown, unless a known false previousMoveCapture or zero captureMovesCount already rules it out. Counts must satisfy 0 <= recaptures <= captures <= legal moves when those counts are known. Existing stored metrics are sufficient, with no migration or extra Stockfish run.

`CandidateMoveClassifier` describes each saved MultiPV candidate, including alternatives that were never played:

```java
Boolean previousCapture = analysis.getPreviousMoveCapture();
var classification = candidateMoveClassifier.classify(candidate, previousCapture);
classification.categories();
classification.unknownCategories();
classification.complete();
```

Pass previousCapture from the same parent analysis. It is an explicit argument so classification does not traverse a lazy JPA association or load the parent once per candidate. Invoke on saved candidates after their flags are populated. No new production consumer/UI is introduced in this step.

Categories overlap:

- CAPTURE: saved capture flag.
- RECAPTURE: saved recapture flag.
- OTHER_CAPTURE: previousCapture && capture && !recapture.
- GIVES_CHECK: saved givesCheck flag.
- PROMOTION: saved promotion flag.

OTHER_CAPTURE and RECAPTURE are mutually exclusive. Either can coexist with CAPTURE, GIVES_CHECK and PROMOTION. Null flags remain unknown; a known false conjunct can rule out OTHER_CAPTURE even when another flag is missing. Contradictory recapture flags are rejected. Results are immutable snapshots.

These categories do not depend on PV rank, centipawn evaluation, mate score, material value or the move actually played. They describe a candidate, not its quality. A losing capture may still be OTHER_CAPTURE. Taking a defended minor piece with a queen is not labelled a sacrifice: defenders, exchanges and forced mating continuations need separate analysis. Likewise, no new attack or tactical-motif recognition is introduced here. complexityFactor remains 1.0.

Candidate tests cover overlaps, unknowns, quiet moves, rank/score independence, and contradictions. An integration fixture verifies both Bxa3 (OTHER_CAPTURE) and Rxd4 (RECAPTURE) after ...Rxd4 using the existing chesslib context calculation. The existing integration suite also covers en passant, pins and promotion recaptures.
