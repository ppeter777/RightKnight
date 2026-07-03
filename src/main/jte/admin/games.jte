@param java.util.List<dev.rightknight.service.admin.AdminGameService.GameSummary> games
@param Long filterOwnerId
@param String filterLichess
@param java.time.LocalDate filterDateFrom
@param java.time.LocalDate filterDateTo
@param String filterResult

@param String currentUsername = null
@param boolean isAdmin = false
@param String csrfParameterName = "_csrf"
@param String csrfToken = ""

@param int page = 0
@param int size = 50
@param int totalPages = 1
@param long total = 0

@template.layout.main(
title = "Admin - Games",
activePage = "admin-games",
currentUsername = currentUsername,
csrfParameterName = csrfParameterName,
csrfToken = csrfToken,
isAdmin = isAdmin,
content = @`

    <div class="container mt-4">
        <h1>Партии</h1>
        <form method="get" class="row g-3 mb-4">
            <div class="col-md-2">
                <input name="ownerId" type="number" value="${filterOwnerId != null ? filterOwnerId.toString() : ""}" class="form-control" placeholder="ID владельца">
            </div>
            <div class="col-md-2">
                <input name="lichess" type="text" value="${filterLichess != null ? filterLichess : ""}" class="form-control" placeholder="Lichess пользователя">
            </div>
            <div class="col-md-2">
                <input name="dateFrom" type="date" value="${filterDateFrom != null ? filterDateFrom.toString() : ""}" class="form-control" placeholder="Дата от">
            </div>
            <div class="col-md-2">
                <input name="dateTo" type="date" value="${filterDateTo != null ? filterDateTo.toString() : ""}" class="form-control" placeholder="Дата до">
            </div>
            <div class="col-md-2">
                <input name="result" type="text" value="${filterResult != null ? filterResult : ""}" class="form-control" placeholder="Результат">
            </div>
            <div class="col-md-2">
                <button type="submit" class="btn btn-primary w-100">Фильтр</button>
            </div>
        </form>
        <div class="table-responsive">
            <table class="table table-sm table-striped">
                <thead>
                <tr>
                    <th>ID</th><th>Owner ID</th><th>Owner Username</th><th>Owner Email</th><th>Lichess</th><th>White</th><th>Black</th><th>Result</th><th>Game Date</th><th>Imported</th><th>Moves</th>
                </tr>
                </thead>
                <tbody>
                @for(var g : games)
                    <tr>
                        <td>${g.id}</td>
                        <td>${g.ownerId}</td>
                        <td>${g.ownerUsername}</td>
                        <td>${g.ownerEmail}</td>
                        <td>${g.ownerLichessUsername}</td>
                        <td>${g.white}</td>
                        <td>${g.black}</td>
                        <td>${g.result}</td>
                        <td>${g.gameDate != null ? g.gameDate.toString() : ""}</td>
                        <td>${g.importedDate != null ? g.importedDate.toString() : ""}</td>
                        <td>${g.movesCount}</td>
                    </tr>
                @endfor
                </tbody>
            </table>
            <nav class="mt-3">
                <ul class="pagination">
                    <li class="page-item ${page <= 0 ? "disabled" : ""}">
                        <a class="page-link" href="/admin/games?page=${page - 1}&size=${size}">
                            Назад
                        </a>
                    </li>

                    <li class="page-item disabled">
            <span class="page-link">
                Страница ${page + 1} из ${totalPages}
            </span>
                    </li>

                    <li class="page-item ${page + 1 >= totalPages ? "disabled" : ""}">
                        <a class="page-link" href="/admin/games?page=${page + 1}&size=${size}">
                            Вперёд
                        </a>
                    </li>
                </ul>
            </nav>
        </div>
    </div>
`
)