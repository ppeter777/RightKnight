@param java.util.List<dev.rightknight.model.UserActivityLogEntity> activities
@param int limit
@param String currentUsername = null
@param boolean isAdmin = false
@param String csrfParameterName = "_csrf"
@param String csrfToken = ""

@template.layout.main(
title = "Admin - Activity",
activePage = "admin-activity",
currentUsername = currentUsername,
csrfParameterName = csrfParameterName,
csrfToken = csrfToken,
isAdmin = isAdmin,
content = @`

    <div class="container mt-4">
        <h1>Журнал действий</h1>
        <form method="get" class="row g-3 mb-3">
            <div class="col-md-4">
                <input name="limit" type="number" value="${limit}" class="form-control" placeholder="Количество записей">
            </div>
            <div class="col-md-2">
                <button type="submit" class="btn btn-primary w-100">Показать</button>
            </div>
        </form>
        <div class="table-responsive">
            <table class="table table-sm table-striped">
                <thead>
                <tr>
                    <th>ID</th><th>Пользователь</th><th>Событие</th><th>Метод</th><th>Путь</th><th>Статус</th><th>IP</th><th>UA</th><th>Дата</th>
                </tr>
                </thead>
                <tbody>
                @for(var a : activities)
                    <tr>
                        <td>${a.getId()}</td>
                        <td>${a.getUser() != null ? a.getUser().getUsername() : "-"}</td>
                        <td>${a.getEventType() != null ? a.getEventType().name() : ""}</td>
                        <td>${a.getMethod() != null ? a.getMethod() : ""}</td>
                        <td>${a.getPath() != null ? a.getPath() : ""}</td>
                        <td>${a.getStatusCode() != null ? a.getStatusCode().toString() : ""}</td>
                        <td>${a.getIpAddress() != null ? a.getIpAddress() : ""}</td>
                        <td>${a.getUserAgent() != null ? a.getUserAgent() : ""}</td>
                        <td>${a.getCreatedAt() != null ? a.getCreatedAt().toString() : ""}</td>
                    </tr>
                @endfor
                </tbody>
            </table>
        </div>
    </div>
`
)