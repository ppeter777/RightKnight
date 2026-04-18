@param String player = ""
@param Integer performanceBoth = null
@param Integer performanceBlack = null
@param Integer performanceWhite = null
@param Integer gamesPlayed = null
@param Integer gamesWhite = null
@param Integer gamesBlack = null

@template.layout.main(content = @`
    <div class="bg-white shadow rounded-lg p-6 mb-4">
        <h1 class="text-2xl font-bold mb-4">Performance Analyzer</h1>

        <!-- ОДНА форма для всех данных -->
        <form action="/performance" method="get">
            <div class="row g-3">
                <div class="col-md-4">
                    <label class="form-label">Lichess Nickname</label>
                    <input type="text" name="player" class="form-control" value="${player}" required>
                </div>
                <div class="col-md-3">
                    <label class="form-label">From</label>
                    <input id="startDate" name="from" class="form-control" required/>
                </div>
                <div class="col-md-3">
                    <label class="form-label">Until</label>
                    <input id="endDate" name="until" class="form-control" required/>
                </div>
                <div class="col-md-2 d-flex align-items-end">
                    <button type="submit" class="btn btn-primary w-100">Calculate</button>
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
`)
