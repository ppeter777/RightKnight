@param dev.rightknight.auth.RegisterRequest form
@param String errorMessage
@param String currentUsername = null
@param String csrfParameterName = "_csrf"
@param String csrfToken = ""
@param boolean isAdmin = false
@param String requestUri = ""

@template.layout.main(
    isAdmin = isAdmin,
    title = "RightKnight - Register",
    activePage = "register",
    currentUsername = currentUsername,
    csrfParameterName = csrfParameterName,
    csrfToken = csrfToken,
    content = @`
    <div class="row justify-content-center">
        <div class="col-md-6 col-lg-5">
            <div class="card shadow-sm">
                <div class="card-body">
                    <h1 class="h4 mb-3">Регистрация</h1>

                    @if(errorMessage != null)
                        <div class="alert alert-danger">
                            ${errorMessage}
                        </div>
                    @endif

                    <form method="post" action="/register">
                        <input type="hidden" name="${csrfParameterName}" value="${csrfToken}">

                        <div class="mb-3">
                            <label for="username" class="form-label">Имя пользователя</label>
                            <input id="username"
                                   name="username"
                                   type="text"
                                   class="form-control"
                                   value="${form.getUsername() == null ? "" : form.getUsername()}"
                                   required>
                        </div>

                        <div class="mb-3">
                            <label for="email" class="form-label">Email, необязательно</label>
                            <input id="email"
                                   name="email"
                                   type="email"
                                   class="form-control"
                                   value="${form.getEmail() == null ? "" : form.getEmail()}">
                        </div>

                        <div class="mb-3">
                            <label for="password" class="form-label">Пароль</label>
                            <input id="password" name="password" type="password" class="form-control" required>
                        </div>

                        <div class="mb-3">
                            <label for="passwordConfirm" class="form-label">Повторите пароль</label>
                            <input id="passwordConfirm" name="passwordConfirm" type="password" class="form-control" required>
                        </div>

                        <button type="submit" class="btn btn-primary">Зарегистрироваться</button>
                        <a href="/login" class="btn btn-link">Уже есть аккаунт</a>
                    </form>
                </div>
            </div>
        </div>
    </div>
`)
