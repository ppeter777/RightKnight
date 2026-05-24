@param java.util.List<dev.rightknight.model.GameEntity> games
@param String search = ""
@param String side = "all"

@template.layout.main(title = "RightKnight - Games", activePage = "games", content = @`
    <div class="card shadow-sm mb-4">
        <div class="card-body">
            <h1 class="h3 mb-4">Game History</h1>

            <div class="mb-3">
                <form action="/games" method="get" class="row g-2 align-items-end">
                    <div class="col-md-4">
                        <label class="form-label">Opening</label>
                        <input type="text" name="search" class="form-control" value="${search}" placeholder="Search opening...">
                    </div>
                    <div class="col-md-2">
                        <label class="form-label">Side</label>
                        <select name="side" class="form-select">
                            <option value="all" selected="${side.equals("all")}">Any Side</option>
                            <option value="white" selected="${side.equals("white")}">As White</option>
                            <option value="black" selected="${side.equals("black")}">As Black</option>
                        </select>
                    </div>
                    <div class="col-md-2">
                        <button type="submit" class="btn btn-primary w-100">Find</button>
                    </div>
                </form>
            </div>

            <div class="table-responsive">
                <table class="table table-hover align-middle mb-0">
                    <thead class="table-light">
                    <tr>
                        <th>Date</th>
                        <th>Mode</th>
                        <th>Opponent</th>
                        <th>Result</th>
                        <th>Opening</th>
                        <th>Actions</th>
                    </tr>
                    </thead>
                    <tbody>
                    @for(var game : games)
                        <tr>
                            <td>${game.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}</td>
                            <td><span class="badge bg-secondary">${game.getMode()}</span></td>
                            <td>
                                <span class="${game.isWhite() ? "text-primary" : "text-dark"}">
                                    ${game.isWhite() ? "W" : "B"}
                                </span>
                                ${game.getOpponentId()} (${game.getOpponentRating()})
                            </td>
                            <td>
                                @if(game.getScore() == 1.0f)
                                    <span class="text-success fw-bold">1-0</span>
                                @elseif(game.getScore() == 0.0f)
                                    <span class="text-danger fw-bold">0-1</span>
                                @else
                                    <span class="text-warning fw-bold">½-½</span>
                                @endif
                            </td>
                            <td class="small text-muted">${game.getOpeningName()}</td>
                            <td>
                                <button type="button"
                                    class="btn btn-sm btn-outline-primary js-view-game"
                                    data-pgn="${game.getPgn()}"
                                    data-white-name="${game.isWhite() ? game.getUserId() : game.getOpponentId()}"
                                    data-white-rating="${game.isWhite() ? String.valueOf(game.getUserRating()) : String.valueOf(game.getOpponentRating())}"
                                    data-black-name="${game.isWhite() ? game.getOpponentId() : game.getUserId()}"
                                    data-black-rating="${game.isWhite() ? String.valueOf(game.getOpponentRating()) : String.valueOf(game.getUserRating())}"
                                    data-board-orientation="${game.isWhite() ? "white" : "black"}">
                                    View
                                </button>
                            </td>
                        </tr>
                    @endfor
                    </tbody>
                </table>
            </div>
        </div>
    </div>

    <div class="modal fade" id="gameModal" tabindex="-1">
        <div class="modal-dialog modal-lg">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title">Game Review</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body">
                    <div class="row mb-2 px-3">
                        <div class="col-12 d-flex justify-content-between bg-dark text-white p-2 rounded">
                            <div id="player-black">Black: Loading...</div>

<button type="button"
        id="flipBoardBtn"
        class="btn btn-sm btn-outline-light game-flip-button d-none d-md-inline-flex"
        title="Flip board">
    Flip
</button>

                            <div class="fw-bold text-warning" id="evaluation">0.0</div>
                            <div id="player-white">White: Loading...</div>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col-12 col-md-7 d-flex flex-column align-items-center">
                            <div id="myBoard" class="game-modal-board" style="width: 100%"></div>
                            <div class="mt-2">
</div>
                        </div>
                        <div class="col-12 col-md-5 game-modal-side d-flex flex-column">
                            <div id="pgn-moves" class="pgn-moves-panel p-2 bg-light border"></div>
                            <div class="game-nav-controls mt-auto pt-3 mt-3 text-center">

<button type="button"
        id="flipBoardMobileBtn"
        class="btn btn-outline-secondary btn-sm game-flip-button-mobile d-md-none"
        title="Flip board">
    Flip
</button>

                                <button type="button" id="moveStartBtn" class="btn btn-secondary">|&lt;</button>
                                <button type="button" id="movePrevBtn" class="btn btn-secondary">&lt;</button>
                                <button type="button" id="moveNextBtn" class="btn btn-secondary">&gt;</button>
                                <button type="button" id="moveEndBtn" class="btn btn-secondary">&gt;|</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <script src="/js/games.js"></script>

`)
