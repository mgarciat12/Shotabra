package com.shotabra.web;

import com.shotabra.config.AppProperties;
import com.shotabra.game.Emojis;
import com.shotabra.game.PartyCodeGenerator;
import com.shotabra.game.PlayerNames;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Una única página: game.js cambia de pantalla según el estado que devuelve la API. */
@Controller
public class PageController {

    private final AppProperties props;

    public PageController(AppProperties props) {
        this.props = props;
    }

    @GetMapping("/")
    public String index(Model model) {
        return page(model, null);
    }

    /** Destino del QR: abre directamente la pantalla de unirse con el código relleno. */
    @GetMapping("/j/{code}")
    public String join(@PathVariable String code, Model model) {
        return page(model, PartyCodeGenerator.normalize(code));
    }

    private String page(Model model, String joinCode) {
        model.addAttribute("joinCode", joinCode);
        model.addAttribute("emojis", Emojis.ALL);
        model.addAttribute("minPlayers", props.minPlayers());
        model.addAttribute("maxNameLength", PlayerNames.MAX_LENGTH);
        return "index";
    }
}
