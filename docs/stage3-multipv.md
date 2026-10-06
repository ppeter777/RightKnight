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

Known pre-existing limitation: the repository has no migrations creating
`game_analysis` and `game_move_analysis`, while V12 already alters `game_analysis`.
Therefore the full Flyway history cannot currently bootstrap a completely empty
production database. V13 assumes those parent tables already exist. Existing
migration checksums are deliberately unchanged.

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
