@param Boolean hasError
@param Boolean registered
@param String currentUsername = null
@param String csrfParameterName = "_csrf"
@param String csrfToken = ""
@param boolean isAdmin = false
@param String requestUri = ""

@template.layout.main(
    isAdmin = isAdmin,
    title = "RightKnight - Login",
    activePage = "login",
    currentUsername = currentUsername,
    csrfParameterName = csrfParameterName,
    csrfToken = csrfToken,
    content = @`
    <div class="row justify-content-center">
        <div class="col-md-6 col-lg-4">
            <div class="card shadow-sm">
                <div class="card-body">
                    <h1 class="h4 mb-3">Вход</h1>

                    @if(Boolean.TRUE.equals(hasError))
                        <div class="alert alert-danger">
                            Неверное имя пользователя или пароль.
                        </div>
                    @endif

                    @if(Boolean.TRUE.equals(registered))
                        <div class="alert alert-success">
                            Регистрация завершена. Теперь можно войти.
                        </div>
                    @endif

                    <form method="post" action="/login">
                        <input type="hidden" name="${csrfParameterName}" value="${csrfToken}">

                        <div class="mb-3">
                            <label for="username" class="form-label">Имя пользователя</label>
                            <input id="username" name="username" type="text" class="form-control" required autofocus>
                        </div>

                        <div class="mb-3">
                            <label for="password" class="form-label">Пароль</label>
                            <input id="password" name="password" type="password" class="form-control" required>
                        </div>

                        <button type="submit" class="btn btn-primary">Войти</button>
                        <a href="/register" class="btn btn-link">Регистрация</a>
                    </form>
                </div>
            </div>
        </div>
    </div>
`)
