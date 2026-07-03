@param dev.rightknight.model.AppUserEntity user
@param dev.rightknight.service.admin.AdminUserService.UserSummary summary
@param String currentUsername = null
@param boolean isAdmin = false
@param String csrfParameterName = "_csrf"
@param String csrfToken = ""

@template.layout.main(
title = "Admin - User",
activePage = "admin-users",
currentUsername = currentUsername,
csrfParameterName = csrfParameterName,
csrfToken = csrfToken,
isAdmin = isAdmin,
content = @`

    <div class="container mt-4">
        <h1>Пользователь #${user.getId()}</h1>
        <div class="row">
            <div class="col-md-6">
                <table class="table table-bordered">
                    <tbody>
                        <tr><th>Username</th><td>${user.getUsername()}</td></tr>
                        <tr><th>Email</th><td>${user.getEmail()}</td></tr>
                        <tr><th>Lichess</th><td>${user.getLichessUsername()}</td></tr>
                        <tr><th>Role</th><td>${user.getRole()}</td></tr>
                        <tr><th>Status</th><td>${user.getStatus()}</td></tr>
                        <tr><th>Создан</th><td>${summary.createdAt != null ? summary.createdAt.toString() : ""}</td></tr>
                        <tr><th>Последняя активность</th><td>${summary.lastActivityAt != null ? summary.lastActivityAt.toString() : ""}</td></tr>
                        <tr><th>Кол-во партий</th><td>${summary.gamesCount}</td></tr>
                        <tr><th>Первая партия</th><td>${summary.firstGameAt != null ? summary.firstGameAt.toString() : ""}</td></tr>
                        <tr><th>Последняя партия</th><td>${summary.lastGameAt != null ? summary.lastGameAt.toString() : ""}</td></tr>
                    </tbody>
                </table>
            </div>
            <div class="col-md-6">
                <h3>Действия</h3>
                <form action="/admin/users/${user.getId()}/disable" method="post" onsubmit="return confirm('Отключить пользователя?');" class="mb-2">
                    <input type="hidden" name="${csrfParameterName}" value="${csrfToken}">
                    <button type="submit" class="btn btn-warning">Отключить</button>
                </form>
                <form action="/admin/users/${user.getId()}/enable" method="post" onsubmit="return confirm('Включить пользователя?');" class="mb-2">
                    <input type="hidden" name="${csrfParameterName}" value="${csrfToken}">
                    <button type="submit" class="btn btn-success">Включить</button>
                </form>
                <form action="/admin/users/${user.getId()}/clear-games" method="post" onsubmit="return confirm('Очистить все партии пользователя?');" class="mb-2">
                    <input type="hidden" name="${csrfParameterName}" value="${csrfToken}">
                    <button type="submit" class="btn btn-danger">Очистить партии</button>
                </form>
                <form action="/admin/users/${user.getId()}/grant-admin" method="post" onsubmit="return confirm('Сделать пользователя админом?');" class="mb-2">
                    <input type="hidden" name="${csrfParameterName}" value="${csrfToken}">
                    <button type="submit" class="btn btn-primary">Сделать админом</button>
                </form>
                <form action="/admin/users/${user.getId()}/revoke-admin" method="post" onsubmit="return confirm('Снять права админа?');" class="mb-2">
                    <input type="hidden" name="${csrfParameterName}" value="${csrfToken}">
                    <button type="submit" class="btn btn-secondary">Снять админа</button>
                </form>
                <form action="/admin/users/${user.getId()}/delete" method="post" onsubmit="return confirm('Удалить пользователя? Это мягкое удаление.');" class="mb-2">
                    <input type="hidden" name="${csrfParameterName}" value="${csrfToken}">
                    <button type="submit" class="btn btn-danger">Удалить (soft)</button>
                </form>
            </div>
        </div>
    </div>

`
)