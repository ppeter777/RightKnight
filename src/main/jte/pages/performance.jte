@param String player = ""
@param Integer performanceBoth = null
@param Integer performanceBlack = null
@param Integer performanceWhite = null
@param Integer gamesPlayed = null
@param Integer gamesWhite = null
@param Integer gamesBlack = null
@param String mode = "all"
@param Boolean rated = null
@param java.time.LocalDate from = null
@param java.time.LocalDate until = null

@template.layout.main(content = @`
    <div class="bg-white shadow rounded-lg p-6 mb-4">
        <h1 class="text-2xl font-bold mb-4">Performance Analyzer</h1>

        <form action="/performance" method="get">
            <!-- Первая строка: Ник и Режимы -->
            <div class="row g-3 mb-3">
                <div class="col-md-3">
                    <label class="form-label">Player</label>
                    <input type="text" name="player" class="form-control" value="${player}" required>
                </div>
                <div class="col-md-3">
                    <label class="form-label">Mode</label>
                    <select name="mode" class="form-select">
                        <option value="all" selected="${mode.equals("all")}">All</option>
                        <option value="bullet" selected="${mode.equals("bullet")}">Bullet</option>
                        <option value="blitz" selected="${mode.equals("blitz")}">Blitz</option>
                        <option value="rapid" selected="${mode.equals("rapid")}">Rapid</option>
                        <option value="classical" selected="${mode.equals("classical")}">Classical</option>
                    </select>
                </div>
                <div class="col-md-3">
                    <label class="form-label">Type</label>
                    <select name="rated" class="form-select">
                        <option value="" selected="${rated == null}">All Games</option>
                        <option value="true" selected="${rated != null && rated}">Rated Only</option>
                        <option value="false" selected="${rated != null && !rated}">Casual Only</option>
                    </select>
                </div>
            </div>

            <!-- Вторая строка: Даты и Кнопка -->
            <!-- align-items-end выравнивает инпуты и кнопку по одной линии по низу -->
            <div class="row g-3 align-items-end">
                <div class="col-md-3">
                    <label class="form-label">From</label>
                    <input id="startDate" name="from" class="form-control"
                           value="${from != null ? from.toString() : ""}" required/>
                </div>
                <div class="col-md-3">
                    <label class="form-label">Until</label>
                    <input id="endDate" name="until" class="form-control"
                           value="${until != null ? until.toString() : ""}" required/>
                </div>
                <div class="col-md-2">
                    <button type="submit" id="calcBtn" class="btn btn-primary w-100">
                        <span class="custom-spinner" id="spinner"></span>
                        <span id="btnText">Calculate</span>
                    </button>
                </div>
            </div>
        </form>

    </div>


    @if(performanceBoth != null)
        <div class="bg-white shadow rounded-lg p-6">
            <h3 class="mb-3">Results for ${player}</h3>
            <table class="table table-bordered">
                <tr class="table-primary">
                    <td><strong>Overall Performance</strong></td>
                    <td><strong>${performanceBoth}</strong></td>
                </tr>
                <tr>
                    <td>Performance White</td>
                    <td>${performanceWhite}</td>
                </tr>
                <tr>
                    <td>Performance Black</td>
                    <td>${performanceBlack}</td>
                </tr>
                <tr>
                    <td>Games Played (Total / W / B)</td>
                    <td>${gamesPlayed} (${gamesWhite}w / ${gamesBlack}b)</td>
                </tr>
            </table>
        </div>
    @endif

    <script>
        $('#startDate').datepicker({
            uiLibrary: 'bootstrap5',
            format: 'yyyy-mm-dd'
        });
        $('#endDate').datepicker({
            uiLibrary: 'bootstrap5',
            format: 'yyyy-mm-dd'
        });
    </script>
    <script>
        $('form').on('submit', function() {
            const btn = $('#calcBtn');
            const spinner = $('#spinner');
            const text = $('#btnText');

            btn.addClass('loading');
            spinner.css('display', 'inline-block'); // Показываем крутилку
            text.text('Calculating...');
        });
    </script>
`)
