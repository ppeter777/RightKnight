@param String currentUsername = null
@param String csrfParameterName = "_csrf"
@param String csrfToken = ""
@param String lichessUsername = null
@param String lichessError = null
@param String lichessSuccess = null
@param boolean isAdmin = false
@param String requestUri = ""

@template.layout.main(
    isAdmin = isAdmin,
    title = "RightKnight - Dashboard",
    activePage = "home",
    currentUsername = currentUsername,
    csrfParameterName = csrfParameterName,
    csrfToken = csrfToken,
    content = @`
    <div class="card shadow-sm">
        <div class="card-body">
            @if(currentUsername == null)
                <h1 class="h3 mb-3">Welcome to RightKnight!</h1>
                <p class="text-muted mb-0">Sign in to manage your games and analysis.</p>
            @else
                <h1 class="h3 mb-3">Welcome, ${currentUsername}!</h1>

                @if(lichessError != null)
                    <div class="alert alert-danger">${lichessError}</div>
                @endif

                @if(lichessSuccess != null)
                    <div class="alert alert-success">${lichessSuccess}</div>
                @endif

                @if(lichessUsername == null)
                    <p class="text-muted">
                        Укажите ваш ник на Lichess, чтобы RightKnight мог загружать и анализировать ваши партии.
                    </p>
                @else
                    <p class="text-muted mb-2">
                        Подключённый аккаунт Lichess:
                        <strong>${lichessUsername}</strong>
                    </p>
                @endif

                <form method="post" action="/profile/lichess" class="row g-2 align-items-end mt-3">
                    <input type="hidden" name="${csrfParameterName}" value="${csrfToken}">

                    <div class="col-md-6 col-lg-4">
                        <label for="lichessUsername" class="form-label">Lichess username</label>
                        <input id="lichessUsername"
                               name="lichessUsername"
                               type="text"
                               class="form-control"
                               autocomplete="off"
                               autocapitalize="none"
                               spellcheck="false"
                               value="${lichessUsername == null ? "" : lichessUsername}"
                               required>
                    </div>

                    <div class="col-md-auto">
                        <button type="submit" class="btn btn-primary">
                            @if(lichessUsername == null)
                                Сохранить
                            @else
                                Изменить
                            @endif
                        </button>
                    </div>
                </form>

                @if(lichessUsername != null)
                    <div class="mt-3">
                        <a href="/performance?player=${lichessUsername}" class="btn btn-outline-secondary">
                            Открыть Performance
                        </a>
                    </div>
                @endif
            @endif
        </div>
    </div>
    <script src="/js/home.js"></script>
    `
)
