@param String currentUsername = null
@param String csrfParameterName = "_csrf"
@param String csrfToken = ""

@template.layout.main(
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
                    <p class="text-muted mb-0">This is your dashboard. Here you can view your recent activity and manage your account.</p>
                @endif
            </div>
        </div>
    `
)
