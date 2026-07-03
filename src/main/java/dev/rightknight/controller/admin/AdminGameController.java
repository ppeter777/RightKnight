package dev.rightknight.controller.admin;

import dev.rightknight.service.admin.AdminGameService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Controller for administering games. Allows viewing all games with
 * optional filters.
 */
@Controller
@RequestMapping("/admin/games")
public class AdminGameController {
    private final AdminGameService adminGameService;

    public AdminGameController(AdminGameService adminGameService) {
        this.adminGameService = adminGameService;
    }

    @GetMapping
    public String listGames(@RequestParam(name = "ownerId", required = false) Long ownerId,
                            @RequestParam(name = "lichess", required = false) String lichess,
                            @RequestParam(name = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                            @RequestParam(name = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                            @RequestParam(name = "result", required = false) String result,
                            @RequestParam(name = "page", defaultValue = "0") int page,
                            @RequestParam(name = "size", defaultValue = "50") int size,
                            Model model) {
//        java.time.Instant fromInstant = dateFrom != null ? dateFrom.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
//        java.time.Instant toInstant = null;
//        if (dateTo != null) {
//            // include the entire day by moving to the end of day
//            toInstant = dateTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);
//        }

        ZonedDateTime from = dateFrom != null
                ? dateFrom.atStartOfDay(ZoneId.systemDefault())
                : null;

        ZonedDateTime to = dateTo != null
                ? dateTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1)
                : null;

        List<AdminGameService.GameSummary> games =
                adminGameService.findGames(ownerId, lichess, from, to, result);
        model.addAttribute("games", games);
        model.addAttribute("filterOwnerId", ownerId);
        model.addAttribute("filterLichess", lichess);
        model.addAttribute("filterDateFrom", dateFrom);
        model.addAttribute("filterDateTo", dateTo);
        model.addAttribute("filterResult", result);
        model.addAttribute("activePage", "admin-games");


        AdminGameService.GamePage gamePage = adminGameService.findGamesPage(page, size);

        model.addAttribute("games", gamePage.games);
        model.addAttribute("page", gamePage.page);
        model.addAttribute("size", gamePage.size);
        model.addAttribute("totalPages", gamePage.totalPages);
        model.addAttribute("total", gamePage.total);
        return "admin/games";
    }
}