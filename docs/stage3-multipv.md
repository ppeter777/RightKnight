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
