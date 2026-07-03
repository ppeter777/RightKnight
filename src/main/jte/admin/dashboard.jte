@param long totalUsers
@param long activeUsers
@param long totalGames
@param long gamesLast24h
@param long gamesLast7d
@param long gamesLast30d
@param java.util.List<dev.rightknight.model.UserActivityLogEntity> recentActivities
@param java.util.List<dev.rightknight.model.UserActivityLogEntity> recentErrors
@param String currentUsername = null
@param boolean isAdmin = false
@param String csrfParameterName = "_csrf"
@param String csrfToken = ""

@template.layout.main(
title = "Admin - Dashboard",
activePage = "admin-dashboard",
currentUsername = currentUsername,
csrfParameterName = csrfParameterName,
csrfToken = csrfToken,
isAdmin = isAdmin,
content = @`

    <div class="container mt-4">
        <h1>Админский Dashboard</h1>
        <div class="row g-3 mb-4">
            <div class="col-md-4">
                <a href="/admin/users" class="btn btn-outline-primary w-100">
                    Пользователи
                </a>
            </div>
            <div class="col-md-4">
                <a href="/admin/games" class="btn btn-outline-primary w-100">
                    Партии
                </a>
            </div>
            <div class="col-md-4">
                <a href="/admin/activity" class="btn btn-outline-primary w-100">
                    Журнал действий
                </a>
            </div>
        </div>
        <div class="row mt-3">
            <div class="col-md-4">
                <div class="card mb-3">
                    <div class="card-body">
                        <h5 class="card-title">Пользователи</h5>
                        <p class="card-text">Всего: <strong>${totalUsers}</strong></p>
                        <p class="card-text">Активных: <strong>${activeUsers}</strong></p>
                    </div>
                </div>
            </div>
            <div class="col-md-8">
                <div class="card mb-3">
                    <div class="card-body">
                        <h5 class="card-title">Партии</h5>
                        <p class="card-text">Всего: <strong>${totalGames}</strong></p>
                        <p class="card-text">За 24 часа: <strong>${gamesLast24h}</strong></p>
                        <p class="card-text">За 7 дней: <strong>${gamesLast7d}</strong></p>
                        <p class="card-text">За 30 дней: <strong>${gamesLast30d}</strong></p>
                    </div>
                </div>
            </div>
        </div>
        <div class="row">
            <div class="col-md-6">
                <h3>Последние действия</h3>
                <table class="table table-sm table-striped">
                    <thead><tr><th>ID</th><th>Пользователь</th><th>Событие</th><th>Дата</th></tr></thead>
                    <tbody>
                    @for(var entry : recentActivities)
                        <tr>
                            <td>${entry.getId()}</td>
                            <td>${entry.getUser() != null ? entry.getUser().getUsername() : "-"}</td>
                            <td>${entry.getEventType()}</td>
                            <td>${entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : ""}</td>
                        </tr>
                    @endfor
                    </tbody>
                </table>
            </div>
            <div class="col-md-6">
                <h3>Ошибки/админские действия</h3>
                <table class="table table-sm table-striped">
                    <thead><tr><th>ID</th><th>Пользователь</th><th>Событие</th><th>Дата</th></tr></thead>
                    <tbody>
                    @for(var entry : recentErrors)
                        <tr>
                            <td>${entry.getId()}</td>
                            <td>${entry.getUser() != null ? entry.getUser().getUsername() : "-"}</td>
                            <td>${entry.getEventType()}</td>
                            <td>${entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : ""}</td>
                        </tr>
                    @endfor
                    </tbody>
                </table>
            </div>
        </div>
    </div>
`
)