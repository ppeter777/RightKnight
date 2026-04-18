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
            @RequestParam(required = false, defaultValue = "all") String mode,
            @RequestParam(required = false) Boolean rated,
            Model model) {

        if (player == null || from == null || until == null) {
            model.addAttribute("player", "zx316");
            return "pages/performance";
        }

        var zFrom = from.atStartOfDay(java.time.ZoneId.systemDefault());
        var zUntil = until.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault());

        // Передаем mode и rated в расчет
        var result = Performance.performanceCalc(player, zFrom, zUntil, mode, rated);

        // Добавляем их в модель, чтобы форма "помнила" выбор
        model.addAttribute("player", player);
        model.addAttribute("mode", mode);
        model.addAttribute("rated", rated);
        model.addAttribute("from", from);
        model.addAttribute("until", until);
        model.addAllAttributes(result);

        return "pages/performance";
    }


}
