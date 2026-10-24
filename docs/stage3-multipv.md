# Stage 3: persisted pre-move MultiPV candidates

Each `game_move_analysis_candidate` belongs to one `game_move_analysis` row,
which identifies both the played move and its analysis run. A second run keeps
its own candidates. Read candidates in `pv_rank` order through
`GameMoveAnalysisCandidateRepository`.

## Contract

- `move_uci` is the first move of `pv_uci`.
- CP and signed mate scores use the side to move in `fenBefore`; no sign flip
  is applied during persistence. Exactly one score field is populated.
- `score_bound` preserves EXACT / LOWER / UPPER. EXACT denotes an unbounded
  search score, not a proof of the position's game-theoretic value.
- The parser receives `min(requested MultiPV, legalMovesCount)` from the session.
  It returns the last complete, contiguous rank set at one depth, with unique
  root moves. A partial final iteration cannot overwrite the previous complete
  set. No complete set in a non-terminal position is an analysis failure.
- Actual depth and selective depth are stored per candidate. Requested depth,
  MultiPV, engine name and threads remain on the analysis run.
- A separate Spring service commits the move result and all candidates in one
  transaction, after engine work finishes. A failed candidate insert rolls back
  that move result. Earlier successfully saved moves remain in an incomplete run
  (`completed_at` is null).
- Terminal post-move positions have no candidates. Checkmate/stalemate are
  evaluated from the board so the final played move can still be saved.
- A CP loss involving mate or bounded scores is unavailable (null), except a
  played rank-1 move retains zero loss. `LossCategory.UNKNOWN` represents missing
  CP loss; it must not silently become NEGLIGIBLE.

## Migration and rollout

`V13__create_game_move_analysis_candidates.sql` targets existing Stage 1/2
installations. Existing analyses are not backfilled; reanalyse a game to populate
candidates. Empty candidate lists on historical analysis rows mean unavailable
candidate data, not zero legal moves.

V11.1 now creates the analysis parent tables before V12/V13. Installations
that previously created these tables through Hibernate can adopt V11.1 using
the documented one-time out-of-order migration procedure; existing migrations
retain their checksums.

## Validation

Run `./gradlew test --no-daemon` with Java 21. Tests cover captured engine output,
partial/mixed-depth sets, fewer legal root moves, bounds, mate, large counters,
pre-move routing, terminal positions, and database round trips/transaction rollback.
The persistence test executes V13 against H2 in PostgreSQL mode; this does not
replace testing the migration on a PostgreSQL staging database.

## Later complexity research

Persisted top-K moves are not all legal moves. If all K pass a quality threshold,
the number of good moves is at least K. A best/second CP gap needs two comparable
unbounded CP scores. Forced legal moves and a single acceptable move are distinct.

Compare MultiPV settings (e.g. 3/5/10) in separate runs to study both the decline
from rank 1 to rank K and changes in evaluations/ranking of the same root moves.
Keep fixed-depth and fixed-time experiments separate. These distributions measure
how demanding move selection is; human difficulty needs separate validation.

## Position and previous-move context (V14)

New analyses store `in_check` (the side to move in `fenBefore` is checked),
`previous_move_capture`, and `recapture_moves_count` on `game_move_analysis`.
Each candidate stores `capture` and `recapture` for its first move.

A recapture captures the piece that made the immediately preceding capture:
its destination is the previous move's destination. A capture following a quiet
move is not a recapture. This also handles a reply to en passant and captures of
promoted pieces. All classification uses legal moves, so pinned, illegal replies
are excluded. `recapture_moves_count` counts ALL legal recaptures, not only the
MultiPV candidates; four promotion choices count as four moves.

The first imported ply has no predecessor: previous capture is false and the
recapture count is zero. If history is unavailable for a later ply, previous
capture, recapture count, and candidate recapture flags are NULL (unknown).
Capture and in-check can still be computed from FEN. A supplied predecessor must
match the previous ply and its stored `fenAfter` must equal the current `fenBefore`.

The game-analysis loop passes its existing `previousMove` into the atomic
persistence service; there is no per-move history database query. A separate
`MoveContextCalculator` computes these features without Stockfish. Engine DTOs
remain unchanged. `inCheck` is computed by `PositionMetricsCalculator` before
trying moves and copied by `MoveAnalysis`. A shared capture detector also prevents
quiet non-pawn moves to the en passant target from being counted as captures.

V14 adds nullable columns without defaults or automatic backfill. Historical
rows stay unknown. New game analysis fills the fields; an offline backfill from
existing FENs and moves is possible but is not implemented here.

These are descriptive features, not complexity scores: neither recapture nor
being in check implies that a human decision is easy or hard.

## Candidate check and promotion (V15)

Each candidate additionally stores `gives_check` and `promotion` for its first
move. `gives_check` tests the opponent's king after making the legal candidate
move, including discovered checks, en passant, castling and mating moves. The
board is restored before the next candidate. `promotion` is true for any pawn
promotion (Q/R/B/N), with or without a capture or check.

Both features are computed from `fenBefore`, without Stockfish or prior history.
They are independent of the position's `in_check` flag. V15 adds nullable columns
without defaults/backfill: existing candidates retain NULL, new analysis stores
true/false. No complexity or sacrifice classification is introduced.

## Played features and new direct attacks (V16)

V16 adds nullable `played_capture`, `played_recapture`, `played_gives_check`,
`played_promotion`, `played_new_attack_targets` to `game_move_analysis`, and
`new_attack_targets` to each candidate. The target lists are JSONB arrays such as
`[{"square":"f5","piece":"BLACK_KNIGHT"}]`. NULL means not calculated;
`[]` means calculated with no new targets. Existing flags and records are kept.
Reanalysis populates new fields; no automatic historical backfill is performed.

The same `MoveContextCalculator` calculates features for every legal root move.
Persistence selects the played UCI independently of MultiPV, so a played move
outside top-K receives the same annotations. Missing or illegal played UCI fails
that move's transaction.

A new direct attack is an enemy non-king target attacked by the moving piece
after the move but not before it. Targets are compared by square and piece.
Sliding pieces respect blockers; pawns respect their color's attack direction.
Geometric attacks are recorded even if the attacker is pinned or the move is a
poor tactical choice. King attacks are represented by `gives_check` instead.
Discovered attacks by other pieces are outside this version. For castling only
the moving king is examined; rook attacks are outside this version. Promotion
uses the promoted piece's attacks.

`MoveFeatureClassifier` classifies both candidates and played moves using the
same rules. Supply the piece on the destination after making the move, including
promotion, rather than the pawn before promotion:

```java
var candidateCategories = classifier.classify(candidate, previousMoveCapture, Piece.WHITE_BISHOP);
var playedCategories = classifier.classifyPlayed(analysis, Piece.WHITE_PAWN);
```

- `NEW_ATTACK_ON_MORE_VALUABLE_PIECE`: at least one new target has greater
  nominal value (pawn 1, knight/bishop 3, rook 5, queen 9; king is excluded).
- `NEW_PAWN_ATTACK_ON_PIECE`: a pawn newly attacks an enemy non-pawn, non-king.
- `OTHER_CAPTURE`: previous move was a capture, this move is a capture, and
  it is not a recapture. This remains derived from raw flags for both played
  and candidate moves; it does not assert that the captured piece is undefended
  or the capture is beneficial.

The existing `CandidateMoveClassifier` API delegates to the shared classifier.
Its two-argument overload cannot classify nonempty attack targets without the
moving piece: those categories are unknown. An empty target list is known false
regardless of attacker; a NULL list is unknown. Categories may overlap.
No quality judgement or complexity coefficient is added.

Tests include the Bg5/g4/Bxa6 position, retained attacks, blockers, pawn direction,
pins, promotion, persistence and transactional rollback. CI runs the V11.1–V16
analysis-schema subset on a disposable PostgreSQL 16 service, testing historical
NULLs, JSONB arrays and a repeated Flyway run. ORM round trips run on H2 in
PostgreSQL mode. To run the PostgreSQL test locally, provide
`RIGHTKNIGHT_TEST_PG_URL`, `RIGHTKNIGHT_TEST_PG_USER`, and
`RIGHTKNIGHT_TEST_PG_PASSWORD` for a disposable test database whose user can
create schemas. This is not a test of the full V1–V16 application migration chain.

## Full attack/defence snapshot and move deltas (V17)

V17 adds three nullable JSONB columns and one nullable integer:

| Table | Column | Content |
| --- | --- | --- |
| game_move_analysis | attack_snapshot_before | Root pieces and all geometric relations, for both colors |
| game_move_analysis | played_attack_delta | Changes made by the played move, including outside MultiPV |
| game_move_analysis | attack_features_version | Extraction rules version, currently 1, covering root and all deltas |
| game_move_analysis_candidate | attack_delta | Changes made by this candidate |

Despite the `attack_*` column names, both ATTACK and DEFENCE are calculated.
The snapshot has `schemaVersion: 1`, `calculatedKinds: ["ATTACK", "DEFENCE"]`,
`pieces` and `relations`. Each piece map key is its lowercase square in the
ROOT position, used as its stable local ID for every alternative. Piece states
contain `color`, `type` and current `square`. A relation contains `kind`,
`sourceId` and `targetId`; it is directed and never points to itself.

ATTACK connects opposite colors; DEFENCE connects equal colors. Both are
geometric occupied-square relations. Blockers stop rays, pawns use diagonals,
and kings control adjacent squares. Pinned pieces still contribute geometric
relations. King targets are included (an ATTACK to a king is a check relation),
but no king value, exchange profit or legal-capture claim is assigned. Empty
square control, x-rays through blockers, overloaded defenders and tactical
quality are outside this version.

Each delta has `schemaVersion: 1`, `moveUci`, `pieceChanges`, `addedRelations`
and `removedRelations`. Each piece change contains `pieceId`, `before` and
`after` states (including color/type/square); `after: null` means captured.
The moving piece keeps its ID, even when it captures an identical enemy piece
or promotes. Standard castling changes king and rook; en passant removes the
pawn off the destination square. Chess960 castling is not supported.

Example for `7k/4r3/8/8/R7/2N5/8/7K w - - 0 1`, after `c3d5`:

```json
{
  "schemaVersion": 1,
  "moveUci": "c3d5",
  "pieceChanges": [{
    "pieceId": "c3",
    "before": {"color": "WHITE", "type": "KNIGHT", "square": "c3"},
    "after": {"color": "WHITE", "type": "KNIGHT", "square": "d5"}
  }],
  "addedRelations": [{"kind": "ATTACK", "sourceId": "c3", "targetId": "e7"}],
  "removedRelations": [{"kind": "DEFENCE", "sourceId": "c3", "targetId": "a4"}]
}
```

The root snapshot in this example has five pieces and one relation:
`DEFENCE(c3, a4)`. Apply piece changes by ID (delete captured pieces); subtract
removed relations from the root set and add added relations. Retained relations
are the root set minus removed relations. A relation retained after promotion
can have a different nominal value comparison because the piece type changed.
Do not compare or apply deltas against another root or extraction version.

Comparing IDs rather than current squares avoids false remove/add events when
a target moves but remains under attack from the same piece. Full-board
comparison captures discovered attacks, exposure of one's own pieces, blocked
lines and lost defenders in either direction. Mechanism labels (discovered,
escape, lost last defender, etc.) are not persisted or classified yet; the raw
relations permit those later classifications. Capture deletes incident relations
and is not an escape from attack. Losing a defender is not automatically an error.

Nominal piece values and their differences remain computed from states; they
are not stored in the payload. New fields do not change the V16 direct-target
contract, existing move categories or complexityFactor. `AttackGraphCalculator`
can run without Stockfish. `MoveContextCalculator` computes a single root
snapshot and deltas for all legal moves, restoring the board after each move.
Persistence saves the root, rule version, played delta and candidate deltas in
the existing transaction.

Migration does not backfill or change V16 data. SQL NULL means not calculated;
an object with empty relation arrays means calculated with no such relations
(or no changes). Even a quiet move with no relation changes has a piece change.
`calculatedKinds` distinguishes uncomputed relation kinds from an empty result.
`schemaVersion` describes JSON format; `attack_features_version` describes
calculation semantics and must change if the extraction rules change. New data
is populated by reanalysis; a separate offline backfill is not implemented.

Validation covers the discussed knight position, retained source/target attacks,
mutual defence, pinned defenders, both pawn colors, discovered and blocked lines,
captures, both-color EP, all four standard castles, promotion/underpromotion,
root-board restoration and reconstruction of after-states. ORM tests reload the
nested JSON and captured nulls. PostgreSQL CI migrates V16 data to V17, round-trips
real typed payloads through JSONB, preserves historical NULLs, and checks Flyway
idempotence. This remains an analysis-schema subset test, not full V1–V17 bootstrap.
