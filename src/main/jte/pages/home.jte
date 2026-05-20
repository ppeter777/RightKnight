@param String username

@template.layout.main(title = "RightKnight - Dashboard", activePage = "home", content = @`
    <div class="card shadow-sm">
        <div class="card-body">
            <h1 class="h3 mb-3">Welcome, ${username}!</h1>
            <p class="text-muted mb-0">This is your dashboard. Here you can view your recent activity and manage your account.</p>
        </div>
    </div>
`)
