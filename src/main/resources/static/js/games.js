// JavaScript for pages/games.jte.
//
// The template renders HTML and server-side data.
// This file owns page behavior: opening the modal, board navigation and engine evaluation.
//
// Click handling is not inline in the template anymore:
// - .js-view-game buttons carry game data in data-* attributes;
// - .js-move spans are generated dynamically and handled through event delegation.

var board = null;
var chess = null;
var moveHistory = [];
var currentMoveIndex = -1;

var engine = new Worker('/js/stockfish-18-lite-single.js');
var engineStarted = false;

$(function () {
    bindGamePageEvents();
});

function bindGamePageEvents() {
    // Delegated handlers. The event bubbles up to document.
    // This also works for move spans created later by renderMoves().
    $(document).on('click', '.js-view-game', function () {
        viewGameFromButton(this);
    });

    $(document).on('click', '.js-move', function () {
        var moveIndex = Number(this.getAttribute('data-move-index'));
        goToMove(moveIndex);
    });

    $('#flipBoardBtn').on('click', function () {
        if (board) {
            board.flip();
        }
    });

    $('#moveStartBtn').on('click', moveStart);
    $('#movePrevBtn').on('click', movePrev);
    $('#moveNextBtn').on('click', moveNext);
    $('#moveEndBtn').on('click', moveEnd);
    $(document).on('keydown', handleGameKeyboardNavigation);
}

function isGameModalOpen() {
    var modalElement = document.getElementById('gameModal');
    return !!modalElement && modalElement.classList.contains('show');
}

function isEditableElement(element) {
    if (!element) {
        return false;
    }

    var tagName = element.tagName ? element.tagName.toLowerCase() : '';
    return tagName === 'input'
        || tagName === 'textarea'
        || tagName === 'select'
        || element.isContentEditable;
}

function handleGameKeyboardNavigation(event) {
    if (!isGameModalOpen() || isEditableElement(event.target)) {
        return;
    }

    if (event.key === 'ArrowLeft') {
        event.preventDefault();
        movePrev();
        return;
    }

    if (event.key === 'ArrowRight') {
        event.preventDefault();
        moveNext();
    }
}

engine.onmessage = function(event) {
    var line = event.data;

    // Keep the existing behavior: update evaluation on depth 12 or mate lines.
    if (line.indexOf('depth 12') !== -1 || line.indexOf('mate') !== -1) {
        parseScore(line);
    }
};

function viewGameFromButton(button) {
    viewGame(
        button.getAttribute('data-pgn'),
        button.getAttribute('data-white-name'),
        button.getAttribute('data-white-rating'),
        button.getAttribute('data-black-name'),
        button.getAttribute('data-black-rating'),
        button.getAttribute('data-board-orientation')
    );
}

function viewGame(pgn, whiteName, whiteRating, blackName, blackRating, boardOrientation) {
    $('#player-white').text(formatPlayer(whiteName, whiteRating));
    $('#player-black').text(formatPlayer(blackName, blackRating));
    $('#evaluation').text('...');

    chess = new Chess();

    if (!chess.load_pgn(pgn)) {
        $('#evaluation').text('PGN error');
        return;
    }

    moveHistory = chess.history();
    chess.reset();
    currentMoveIndex = -1;

    renderMoves();
    clearMoveHighlight();

    var modalElement = document.getElementById('gameModal');
    var bootstrapModal = bootstrap.Modal.getOrCreateInstance(modalElement);

    modalElement.addEventListener('shown.bs.modal', function () {
        initializeBoard(boardOrientation);
    }, { once: true });

    bootstrapModal.show();
}

function formatPlayer(name, rating) {
    var safeName = name && name.trim() ? name.trim() : 'Unknown';
    var safeRating = rating && rating !== '0' ? rating : '—';

    return safeName + ' (' + safeRating + ')';
}

function initializeBoard(boardOrientation) {
    var targetOrientation = normalizeBoardOrientation(boardOrientation);

    // The board lives inside a Bootstrap modal. Recreating it cleanly prevents
    // previous game state or orientation from leaking into the next opened game.
    if (board && typeof board.destroy === 'function') {
        board.destroy();
    }

    $('#myBoard').empty();

    board = Chessboard('myBoard', {
        position: 'start',
        orientation: targetOrientation,
        draggable: false,
        pieceTheme: '/img/chesspieces/{piece}.svg'
    });

    // Be explicit: apply orientation immediately.
    board.orientation(targetOrientation);
    board.start(false);

    // Bootstrap modal content gets final dimensions after it becomes visible.
    // Resizing on the next tick keeps chessboard.js layout and orientation stable.
    window.setTimeout(function () {
        if (board) {
            if (typeof board.resize === 'function') {
                board.resize();
            }
            board.orientation(targetOrientation);
        }
    }, 0);

    evaluatePosition();
}

function normalizeBoardOrientation(boardOrientation) {
    return boardOrientation === 'black' ? 'black' : 'white';
}

function renderMoves() {
    var html = '<div class="d-flex flex-wrap">';

    for (var i = 0; i < moveHistory.length; i++) {
        var move = moveHistory[i];

        if (i % 2 === 0) {
            var moveNum = (i / 2) + 1;
            html += '<div class="w-100 mt-1"><strong>' + moveNum + '.</strong> ';
        }

        html += '<span id="move-' + i + '" class="px-1 cursor-pointer js-move" data-move-index="' + i + '">' + move + '</span> ';

        if (i % 2 !== 0 || i === moveHistory.length - 1) {
            html += '</div>';
        }
    }

    html += '</div>';
    $('#pgn-moves').html(html);
}

function goToMove(index) {
    if (!chess || !board || Number.isNaN(index)) {
        return;
    }

    chess.reset();

    for (var i = 0; i <= index; i++) {
        chess.move(moveHistory[i]);
    }

    currentMoveIndex = index;
    board.position(chess.fen(), false);
    evaluatePosition();
    updateHighlight();
}

function clearMoveHighlight() {
    $('.js-move').removeClass('bg-warning fw-bold text-primary');
}

function updateHighlight() {
    clearMoveHighlight();

    if (currentMoveIndex >= 0) {
        $('#move-' + currentMoveIndex).addClass('bg-warning fw-bold text-primary');
    }
}

function moveNext() {
    if (!chess || !board || currentMoveIndex >= moveHistory.length - 1) {
        return;
    }

    currentMoveIndex++;
    chess.move(moveHistory[currentMoveIndex]);
    board.position(chess.fen(), false);

    $('#evaluation').text('...');
    evaluatePosition();
    updateHighlight();
}

function movePrev() {
    if (!chess || !board || currentMoveIndex < 0) {
        return;
    }

    chess.undo();
    currentMoveIndex--;
    board.position(chess.fen(), false);

    $('#evaluation').text('...');
    evaluatePosition();
    updateHighlight();
}

function moveStart() {
    if (!chess || !board) {
        return;
    }

    chess.reset();
    currentMoveIndex = -1;
    board.start(false);

    $('#evaluation').text('...');
    evaluatePosition();
    updateHighlight();
}

function moveEnd() {
    if (!chess || moveHistory.length === 0) {
        return;
    }

    goToMove(moveHistory.length - 1);
}

function parseScore(line) {
    if (!chess) {
        return;
    }

    var scoreMatch = line.match(/score cp (-?\d+)/);
    if (scoreMatch) {
        var cp = parseInt(scoreMatch[1], 10);

        // Positive score is shown from White's point of view.
        var score = (chess.turn() === 'b' ? -cp : cp) / 100;
        $('#evaluation').text((score > 0 ? '+' : '') + score.toFixed(1));
        return;
    }

    var mateMatch = line.match(/score mate (-?\d+)/);
    if (mateMatch) {
        var mate = parseInt(mateMatch[1], 10);
        $('#evaluation').text('M' + Math.abs(mate));
    }
}

function startEngine() {
    engine.postMessage('uci');
    engine.postMessage('ucinewgame');
    engine.postMessage('isready');
    engineStarted = true;
}

function evaluatePosition() {
    if (!chess) {
        return;
    }

    if (!engineStarted) {
        startEngine();
    }

    // Stop a previous calculation before starting a new one.
    // This keeps the UI responsive when the user clicks through moves quickly.
    engine.postMessage('stop');
    engine.postMessage('position fen ' + chess.fen());
    engine.postMessage('go depth 12');
}
