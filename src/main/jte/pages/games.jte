@param java.util.List<dev.rightknight.model.GameEntity> games
@param String search = ""

@template.layout.main(content = @`
    <div class="bg-white shadow rounded-lg p-6 mb-4">
        <h1 class="text-2xl font-bold mb-4">Game History</h1>

        <div class="overflow-x-auto">
            <div class="mb-3">
                <form action="/games" method="get" class="row g-2 align-items-end">
                    <div class="col-md-4">
                        <input type="text" name="search" class="form-control" value="${search}" placeholder="Search opening...">
                    </div>
                    <div class="col-md-2">
                        <select name="side" class="form-select">
                            <option value="all">Any Side</option>
                            <option value="white">As White</option>
                            <option value="black">As Black</option>
                        </select>
                    </div>
                    <div class="col-md-2">
                        <button type="submit" class="btn btn-primary w-100">Find</button>
                    </div>
                </form>
            </div>
            <table class="table table-hover">
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
                                <span class="text-success font-bold">1-0</span>
                            @elseif(game.getScore() == 0.0f)
                                <span class="text-danger font-bold">0-1</span>
                            @else
                                <span class="text-warning font-bold">½-½</span>
                            @endif
                        </td>
                        <td class="small text-muted">${game.getOpeningName()}</td>
                        <td>
                            <button class="btn btn-sm btn-outline-primary"
                                    data-pgn="${game.getPgn()}"
                                    onclick="viewGame(
                                        this.getAttribute('data-pgn'),
                                        '${game.isWhite() ? "zx316" : game.getOpponentId()}',
                                        '${game.isWhite() ? "" : String.valueOf(game.getOpponentRating())}',
                                        '${game.isWhite() ? game.getOpponentId() : "zx316"}',
                                        '${game.isWhite() ? String.valueOf(game.getOpponentRating()) : ""}',
                                        '${game.isWhite()}'
                                    )">View</button>
                        </td>
                    </tr>
                @endfor
                </tbody>
            </table>
        </div>
    </div>

    <!-- Модальное окно -->
    <div class="modal fade" id="gameModal" tabindex="-1">
        <div class="modal-dialog modal-lg">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title">Game Review</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body">
                    <div class="row mb-2 px-3">
                        <!-- Информация об игроках -->
                        <div class="col-12 d-flex justify-content-between bg-dark text-white p-2 rounded">
                            <div id="player-black">Black: Loading...</div>
                            <div class="fw-bold text-warning" id="evaluation">0.0</div>
                            <div id="player-white">White: Loading...</div>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col-md-7">
                            <div id="myBoard" style="width: 100%"></div>
                            <div class="mt-2">
                                <button class="btn btn-sm btn-outline-secondary" onclick="board.flip()">Flip Board</button>
                            </div>
                        </div>
                        <div class="col-md-5">
                            <div id="pgn-moves" class="p-2 bg-light border" style="height: 400px; overflow-y: auto;"></div>
                            <div class="mt-3 text-center">
                                <button class="btn btn-secondary" onclick="moveStart()">|&lt;</button>
                                <button class="btn btn-secondary" onclick="movePrev()">&lt;</button>
                                <button class="btn btn-secondary" onclick="moveNext()">&gt;</button>
                                <button class="btn btn-secondary" onclick="moveEnd()">&gt;|</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>


    <script>
        var board = null;
        var chess = null;
        var moveHistory = [];
        var currentMoveIndex = -1;

        var engine = new Worker('/js/stockfish-18-lite-single.js');
        var engineStatus = false;

        engine.onmessage = function(event) {
            var line = event.data;

            // Игнорируем промежуточные данные, ловим только финальный скор на глубине
            if (line.indexOf('depth 12') !== -1 || line.indexOf('mate') !== -1) {
                parseScore(line);
            }
        };

        function parseScore(line) {
            var scoreMatch = line.match(/score cp (-?\d+)/);
            if (scoreMatch) {
                var cp = parseInt(scoreMatch[1]);
                // Инвертируем, если ход черных (чтобы + всегда был за белых)
                var score = (chess.turn() === 'b' ? -cp : cp) / 100;
                $('#evaluation').text((score > 0 ? '+' : '') + score.toFixed(1));
                return;
            }

            var mateMatch = line.match(/score mate (-?\d+)/);
            if (mateMatch) {
                var mate = parseInt(mateMatch[1]);
                $('#evaluation').text('M' + Math.abs(mate));
            }
        }

        function viewGame(pgn, whiteName, whiteRating, blackName, blackRating, userIsWhite) {
            // Формируем красивые строки для игроков
            var whiteInfo = whiteName + (whiteRating ? ' (' + whiteRating + ')' : '');
            var blackInfo = blackName + (blackRating ? ' (' + blackRating + ')' : '');

            $('#player-white').text(whiteInfo);
            $('#player-black').text(blackInfo);
            $('#evaluation').text('...');



            var modalElement = document.getElementById('gameModal');
            var bootstrapModal = bootstrap.Modal.getOrCreateInstance(modalElement);
            bootstrapModal.show();

            modalElement.addEventListener('shown.bs.modal', function () {
                // Определяем ориентацию ЗАРАНЕЕ
                var targetOrientation = (userIsWhite === 'true') ? 'white' : 'black';

                var config = {
                    position: 'start',
                    orientation: targetOrientation, // Передаем сразу сюда
                    draggable: false,
                    pieceTheme: '/img/chesspieces/wikipedia/{piece}.svg'
                };

                // Создаем доску уже с нужной ориентацией
                board = Chessboard('myBoard', config);

                evaluatePosition();
            }, { once: true });

            chess = new Chess();
            if (!chess.load_pgn(pgn)) return;

            moveHistory = chess.history();
            chess.reset();
            currentMoveIndex = -1;

            // Render the moves as clickable spans
            renderMoves();

            // Заполняем имена
            $('#player-white').text(whiteName + ' (' + whiteRating + ')');
            $('#player-black').text(blackName + ' (' + blackRating + ')');
            $('#evaluation').text('0.0');

            var modalElement = document.getElementById('gameModal');
            bootstrap.Modal.getOrCreateInstance(modalElement).show();

            modalElement.addEventListener('shown.bs.modal', function () {
                board = Chessboard('myBoard', {
                    position: 'start',
                    pieceTheme: '/img/chesspieces/{piece}.svg'
                });
            }, { once: true });
        }

        function renderMoves() {
            var html = '<div class="d-flex flex-wrap">';
            for (var i = 0; i < moveHistory.length; i++) {
                var move = moveHistory[i];
                // Начало строки (номер хода)
                if (i % 2 === 0) {
                    var moveNum = (i / 2) + 1;
                    html += '<div class="w-100 mt-1"><strong>' + moveNum + '.</strong> ';
                }

                // Сама фигура/ход
                html += '<span id="move-' + i + '" class="px-1 cursor-pointer" onclick="goToMove(' + i + ')">' + move + '</span> ';

                // Конец строки после хода черных
                if (i % 2 !== 0 || i === moveHistory.length - 1) {
                    html += '</div>';
                }
            }
            html += '</div>';
            $('#pgn-moves').html(html);
        }

        function goToMove(index) {
            chess.reset();
            for (let i = 0; i <= index; i++) {
                chess.move(moveHistory[i]);
            }
            currentMoveIndex = index;
            board.position(chess.fen(), false);
            evaluatePosition();
            updateHighlight();
        }

        function updateHighlight() {
            $('.cursor-pointer').removeClass('bg-yellow-200 font-bold text-blue-600');
            $('#move-' + currentMoveIndex).addClass('bg-yellow-200 font-bold text-blue-600');
        }

        function moveNext() {
            if (chess && currentMoveIndex < moveHistory.length - 1) {
                currentMoveIndex++;
                chess.move(moveHistory[currentMoveIndex]);
                board.position(chess.fen(), false);

                $('#evaluation').text('...'); // Показываем, что думаем
                evaluatePosition();
                updateHighlight();
            }
        }

        function movePrev() {
            if (chess && currentMoveIndex >= 0) {
                chess.undo();
                currentMoveIndex--;
                board.position(chess.fen(), false); // 'false' turns off animation
                evaluatePosition();
                updateHighlight();
            }
        }

        function startEngine() {
            engine.postMessage('uci');
            engine.postMessage('ucinewgame'); // Clear engine hash/state
            engine.postMessage('isready');
            engineStatus = true;
        }

        var evalTimer = null;

        function evaluatePosition() {
            if (!engineStatus) startEngine();

            engine.postMessage('position fen ' + chess.fen());
            engine.postMessage('go depth 12'); // Глубина 13 достаточно быстрая для мгновенного отклика
        }

        function moveStart() { if(chess) { chess.reset(); currentMoveIndex = -1; board.start(); } }
        function moveEnd() { while(chess && currentMoveIndex < moveHistory.length - 1) moveNext(); }
    </script>

`)
