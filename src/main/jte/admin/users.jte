@param java.util.List<dev.rightknight.service.admin.AdminUserService.UserSummary> users
@param String filterQ
@param String filterLichess
@param dev.rightknight.model.UserStatus filterStatus
@param dev.rightknight.model.UserRole filterRole
@param dev.rightknight.model.UserStatus[] statuses
@param dev.rightknight.model.UserRole[] roles
@param String currentUsername = null
@param boolean isAdmin = false
@param String csrfParameterName = "_csrf"
@param String csrfToken = ""

@template.layout.main(
title = "Admin - Users",
activePage = "admin-users",
currentUsername = currentUsername,
csrfParameterName = csrfParameterName,
csrfToken = csrfToken,
isAdmin = isAdmin,
content = @`

    <div class="container mt-4">
        <h1>Пользователи</h1>
        <form method="get" class="row g-3 mb-4">
            <div class="col-md-3">
                <input name="q" type="text" value="${filterQ != null ? filterQ : ""}" class="form-control" placeholder="Username/Email">
            </div>
            <div class="col-md-3">
                <input name="lichess" type="text" value="${filterLichess != null ? filterLichess : ""}" class="form-control" placeholder="Lichess username">
            </div>
            <div class="col-md-2">
                <select name="status" class="form-select">
                    <option value="">Статус (все)</option>
                    @for(var s : statuses)
                        <option value="${s.name()}" selected="${s == filterStatus}">${s}</option>
                    @endfor
                </select>
            </div>
            <div class="col-md-2">
                <select name="role" class="form-select">
                    <option value="">Роль (все)</option>
                    @for(var r : roles)
                        <option value="${r.name()}" selected="${r == filterRole}">${r}</option>
                    @endfor
                </select>
            </div>
            <div class="col-md-2">
                <button type="submit" class="btn btn-primary w-100">Фильтр</button>
            </div>
        </form>
        <div class="table-responsive">
            <table class="table table-sm table-striped">
                <thead>
                <tr>
                    <th>ID</th><th>Username</th><th>Email</th><th>Lichess</th><th>Role</th><th>Status</th><th>Создан</th><th>Последняя активность</th><th>Кол-во партий</th><th>Первая партия</th><th>Последняя партия</th><th></th>
                </tr>
                </thead>
                <tbody>
                @for(var u : users)
                    <tr>
                        <td>${u.id}</td>
                        <td>${u.username}</td>
                        <td>${u.email}</td>
                        <td>${u.lichessUsername}</td>
                        <td>${u.role != null ? u.role : ""}</td>
                        <td>${u.status != null ? u.status : ""}</td>
                        <td>${u.createdAt != null ? u.createdAt.toString() : ""}</td>
                        <td>${u.lastActivityAt != null ? u.lastActivityAt.toString() : ""}</td>
                        <td>${u.gamesCount}</td>
                        <td>${u.firstGameAt != null ? u.firstGameAt.toString() : ""}</td>
                        <td>${u.lastGameAt != null ? u.lastGameAt.toString() : ""}</td>
                        <td><a href="/admin/users/${u.id}" class="btn btn-sm btn-outline-primary">Открыть</a></td>
                    </tr>
                @endfor
                </tbody>
            </table>
        </div>
    </div>
`
)