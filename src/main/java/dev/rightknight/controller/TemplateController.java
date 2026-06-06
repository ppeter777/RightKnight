package dev.rightknight.controller;

import dev.rightknight.calc.Performance;
import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameEntity;
import dev.rightknight.repository.GameRepository;
import dev.rightknight.service.GameSyncService;
import dev.rightknight.service.GameSyncWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import dev.rightknight.security.CurrentUserService;
import org.springframework.security.core.Authentication;

import java.util.List;

@Controller
public class TemplateController {

    @Autowired
    Performance performance;

    @Autowired
    GameRepository gameRepository;

    @Autowired
    CurrentUserService currentUserService;

    @Autowired
    GameSyncService gameSyncService;

    @Autowired
    GameSyncWorker gameSyncWorker;

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        currentUserService.getCurrentUser(authentication)
                .ifPresent(user -> model.addAttribute("lichessUsername", user.getLichessUsername()));

        return "pages/home";
    }

    @GetMapping("/performance")
    public String showPerformance(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate until,
            @RequestParam(required = false, defaultValue = "all") String mode,
            @RequestParam(required = false) Boolean rated,
            Authentication authentication,
            Model model) {

        if (player == null || from == null || until == null) {
            currentUserService.getCurrentUser(authentication)
                    .map(user -> user.getLichessUsername())
                    .ifPresentOrElse(
                            lichessUsername -> model.addAttribute("player", lichessUsername),
                            () -> model.addAttribute("player", "")
                    );

            return "pages/performance";
        }

        var zFrom = from.atStartOfDay(java.time.ZoneId.systemDefault());
        var zUntil = until.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault());

        currentUserService.getCurrentUser(authentication)
                .filter(user -> user.getLichessUsername() != null)
                .filter(user -> user.getLichessUsername().equalsIgnoreCase(player))
                .ifPresent(user -> {
                    if (gameSyncService.shouldSync(user)) {
                        gameSyncWorker.syncUserGames(user.getId());
                    }
                });

        // Передаем mode и rated в расчет
        var result = performance.performanceCalc(player, zFrom, zUntil, mode, rated);

        // Добавляем их в модель, чтобы форма "помнила" выбор
        model.addAttribute("player", player);
        model.addAttribute("mode", mode);
        model.addAttribute("rated", rated);
        model.addAttribute("from", from);
        model.addAttribute("until", until);
        model.addAllAttributes(result);

        return "pages/performance";
    }

    @GetMapping("/games")
    public String showGames(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "all") String side,
            Authentication authentication,
            Model model) {

        var currentUser = currentUserService.getCurrentUser(authentication);

        if (currentUser.isEmpty() ||
                currentUser.get().getLichessUsername() == null ||
                currentUser.get().getLichessUsername().isBlank()) {
            model.addAttribute("games", List.of());
            return "pages/games";
        }

        AppUserEntity appUser = currentUser.get();
        if (gameSyncService.shouldSync(appUser)) {
            gameSyncWorker.syncUserGames(appUser.getId());
        }

        var lichessUsername = appUser.getLichessUsername();


//    var lichessUsername = currentUserService
//            .getCurrentUser(authentication)
//            .map(user -> user.getLichessUsername())
//            .orElse(null);

        if (lichessUsername == null) {
            model.addAttribute("games", List.of());
            return "pages/games";
        }

        List<GameEntity> games;

        if (search != null && !search.isBlank()) {
            games = gameRepository
                    .findByUserIdAndOpeningNameContainingIgnoreCase(
                            lichessUsername,
                            search
                    );
        } else {
            games = gameRepository
                    .findTop50ByUserIdOrderByCreatedAtDesc(
                            lichessUsername
                    );
        }

        if (!"all".equals(side)) {
            boolean lookForWhite = "white".equals(side);

            games = games.stream()
                    .filter(g -> g.isWhite() == lookForWhite)
                    .toList();
        }

        model.addAttribute("games", games);
        model.addAttribute("search", search);
        model.addAttribute("side", side);

        return "pages/games";
    }

}
