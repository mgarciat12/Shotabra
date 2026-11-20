package com.shotabra.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import com.shotabra.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class GameApiControllerTest extends IntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void createSetsHttpOnlyCookieAndStateWorksWithCookieOrHeader() throws Exception {
        MvcResult created = mvc.perform(post("/api/parties").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"emoji\":\"🍕\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andReturn();
        String token = JsonPath.read(created.getResponse().getContentAsString(), "$.playerToken");

        mvc.perform(get("/api/me/state").cookie(new Cookie(PlayerTokens.COOKIE, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.name").value("Ana"))
                .andExpect(jsonPath("$.me.host").value(true))
                .andExpect(jsonPath("$.party.status").value("LOBBY"));
        mvc.perform(get("/api/me/state").header(PlayerTokens.HEADER, token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/state"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    @Test
    void fullRoundOverHttpNeverLeaksOtherPlayersCards() throws Exception {
        List<String> tokens = new ArrayList<>();
        String createdJson = mvc.perform(post("/api/parties").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Host\",\"emoji\":\"🐶\"}"))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(createdJson, "$.code");
        tokens.add(JsonPath.read(createdJson, "$.playerToken"));
        for (String[] p : new String[][]{{"Bea", "🐱"}, {"Carlos", "🦊"}, {"Dani", "🐼"}}) {
            String json = mvc.perform(post("/api/parties/{code}/players", code.toLowerCase()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"%s\",\"emoji\":\"%s\"}".formatted(p[0], p[1])))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            tokens.add(JsonPath.read(json, "$.playerToken"));
        }

        mvc.perform(post("/api/parties/{code}/players", code).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bea\",\"emoji\":\"🐸\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_TAKEN"));
        mvc.perform(post("/api/game/start").header(PlayerTokens.HEADER, tokens.get(1)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/game/start").header(PlayerTokens.HEADER, tokens.getFirst()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.party.status").value("PLAYING"));

        for (String token : tokens) {
            String state = mvc.perform(get("/api/me/state").header(PlayerTokens.HEADER, token))
                    .andExpect(jsonPath("$.assignment.word").isNotEmpty())
                    .andExpect(jsonPath("$.result").value(nullValue()))
                    .andReturn().getResponse().getContentAsString();
            // Solo hay UNA palabra en todo el JSON: la suya.
            String word = JsonPath.read(state, "$.assignment.word");
            assertThat(state.split("\"word\"", -1)).hasSize(2);
            assertThat(state).contains(word);
        }

        // El host acusa y su objetivo confirma desde el modal.
        String accuserState = mvc.perform(post("/api/game/accusations").header(PlayerTokens.HEADER, tokens.getFirst()))
                .andExpect(jsonPath("$.myAccusation.id").isNumber())
                .andReturn().getResponse().getContentAsString();
        Integer accusationId = JsonPath.read(accuserState, "$.myAccusation.id");
        Integer targetId = JsonPath.read(accuserState, "$.assignment.target.id");
        String accusedToken = null;
        for (String token : tokens) {
            String s = mvc.perform(get("/api/me/state").header(PlayerTokens.HEADER, token)).andReturn().getResponse().getContentAsString();
            if (((Integer) JsonPath.read(s, "$.me.id")).equals(targetId)) {
                accusedToken = token;
                assertThat((Integer) JsonPath.read(s, "$.incomingAccusation.id")).isEqualTo(accusationId);
            }
        }

        mvc.perform(post("/api/game/accusations/{id}/confirm", accusationId).header(PlayerTokens.HEADER, tokens.get(tokens.indexOf(accusedToken) == 1 ? 2 : 1)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/game/accusations/{id}/confirm", accusationId).header(PlayerTokens.HEADER, accusedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.party.status").value("ROUND_OVER"))
                .andExpect(jsonPath("$.result.loser.id").value(targetId))
                .andExpect(jsonPath("$.result.assignments", hasSize(4)));

        mvc.perform(post("/api/game/restart").header(PlayerTokens.HEADER, tokens.getFirst()))
                .andExpect(jsonPath("$.party.round").value(2))
                .andExpect(jsonPath("$.party.status").value("PLAYING"));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        mvc.perform(post("/api/parties").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"emoji\":\"🍕\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/parties").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\",\"emoji\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMOJI"));
        mvc.perform(get("/api/parties/{code}", "ZZZZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void qrPointsToTheJoinUrl() throws Exception {
        mvc.perform(get("/api/parties/{code}/qr.svg", "ABCD"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(containsString("<svg")));
    }

    @Test
    void pagesRenderAndEveryLinkedAssetIsServedLocally() throws Exception {
        String html = mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Shotabra")))
                .andReturn().getResponse().getContentAsString();

        // Todos los CSS/JS se sirven desde el propio servidor (WebJars), nada de CDN.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:href|src)=\"(/[^\"]+\\.(?:css|js))\"").matcher(html);
        List<String> assets = new ArrayList<>();
        while (m.find()) {
            assets.add(m.group(1));
        }
        assertThat(assets).anyMatch(a -> a.contains("bootstrap") && a.endsWith(".css"))
                .anyMatch(a -> a.contains("stomp"))
                .anyMatch(a -> a.contains("sockjs"))
                .anyMatch(a -> a.endsWith("game.js"))
                .allMatch(a -> a.startsWith("/"));
        assertThat(html).doesNotContain("cdn.");
        for (String asset : assets) {
            mvc.perform(get(asset)).andExpect(status().isOk());
        }

        mvc.perform(get("/j/abcd"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-join-code=\"ABCD\"")));
        mvc.perform(get("/j/{code}", "<script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-join-code"))));
    }
}
