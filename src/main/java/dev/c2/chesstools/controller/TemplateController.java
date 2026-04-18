package dev.c2.chesstools.controller;

import dev.c2.chesstools.calc.Performance;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TemplateController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("username", "Peter_P");
        return "pages/home";
    }
    @GetMapping("/performance")
    public String showPerformance(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate until,
            Model model) {

        // Если параметров нет (первый заход), просто показываем форму
        if (player == null || from == null || until == null) {
            model.addAttribute("player", "zx316"); // дефолтный ник
            return "pages/performance";
        }

        // Если параметры есть — считаем
        var zFrom = from.atStartOfDay(java.time.ZoneId.systemDefault());
        var zUntil = until.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault());

        var result = Performance.performanceCalc(player, zFrom, zUntil);

        model.addAttribute("player", player);
        model.addAttribute("performanceBoth", result.get("performanceBoth"));
        model.addAttribute("performanceWhite", result.get("performanceWhite"));
        model.addAttribute("performanceBlack", result.get("performanceBlack"));
        model.addAttribute("gamesPlayed", result.get("gamesPlayed"));
        model.addAttribute("gamesWhite", result.get("gamesWhite"));
        model.addAttribute("gamesBlack", result.get("gamesBlack"));


        return "pages/performance";
    }


}
