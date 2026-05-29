@param String title = "RightKnight"
@param String activePage = ""
@param gg.jte.Content content
@param String currentUsername = null
@param String csrfParameterName = "_csrf"
@param String csrfToken = ""

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title}</title>

    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css?family=Lato:300,400,700&display=swap" rel="stylesheet">

    <link rel="stylesheet" href="/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://unpkg.com/@chrisoakman/chessboardjs@1.0.0/dist/chessboard-1.0.0.min.css">
    <link href="https://unpkg.com/gijgo@1.9.14/css/gijgo.min.css" rel="stylesheet">
    <link rel="stylesheet" href="/css/style.css">

    <script src="/js/jquery-3.3.1.min.js"></script>
    <script src="/js/bootstrap.bundle.min.js"></script>
    <script src="https://unpkg.com/gijgo@1.9.14/js/gijgo.min.js"></script>
    <script src="/js/chess.min.js"></script>
    <script src="/js/chessboard-1.0.0.min.js"></script>
</head>

<body>
<div class="d-flex flex-column min-vh-100">
    <nav class="navbar navbar-expand-md navbar-dark bg-dark">
        <div class="container-xl">
            <a class="navbar-brand d-flex align-items-center gap-2" href="/">
                <span class="brand-mark">RK</span>
                <span>RightKnight</span>
            </a>

            <button class="navbar-toggler" type="button"
                    data-bs-toggle="collapse"
                    data-bs-target="#mainNavbar"
                    aria-controls="mainNavbar"
                    aria-expanded="false"
                    aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>

            <div class="collapse navbar-collapse" id="mainNavbar">
                <ul class="navbar-nav ms-auto mb-2 mb-md-0">
                    <li class="nav-item">
                        <a class="nav-link ${activePage.equals("home") ? "active" : ""}" href="/">Dashboard</a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link ${activePage.equals("performance") ? "active" : ""}" href="/performance">Performance</a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link ${activePage.equals("games") ? "active" : ""}" href="/games">Games</a>
                    </li>
                    <li class="nav-item ms-md-3">
                        @if(currentUsername == null)
                            <a class="nav-link ${activePage.equals("login") ? "active" : ""}" href="/login">Войти</a>
                        @else
                            <span class="navbar-text text-light me-2">${currentUsername}</span>
                            <form method="post" action="/logout" class="d-inline">
                                <input type="hidden" name="${csrfParameterName}" value="${csrfToken}">
                                <button type="submit" class="btn btn-link nav-link d-inline p-0 align-baseline">
                                    Выйти
                                </button>
                            </form>
                        @endif
                    </li>
                </ul>
            </div>
        </div>
    </nav>

    <main class="flex-grow-1 py-4">
        <div class="container-xl">
            ${content}
        </div>
    </main>

    <footer class="border-top py-3 mt-auto bg-light">
        <div class="container-xl text-center text-muted small">
            Created by
            <a href="https://github.com/ppeter777" target="_blank" rel="noopener noreferrer">Peter Pesotskiy</a>
        </div>
    </footer>
</div>
</body>
</html>
