package com.shotabra.game;

import java.util.List;

/** Emojis que pueden elegir los jugadores (no se repiten dentro de una party). */
public final class Emojis {

    public static final List<String> ALL = List.of(
            "🍕", "🍺", "🥃", "🍷", "🍹", "🌮", "🍔", "🍩",
            "🐶", "🐱", "🦊", "🐼", "🐸", "🐵", "🦄", "🐙",
            "🦁", "🐯", "🐧", "🦖", "👽", "🤖", "👻", "💀",
            "🤠", "😎", "🥳", "🔥", "⭐", "🎸");

    private Emojis() {
    }

    public static boolean isValid(String emoji) {
        return emoji != null && ALL.contains(emoji);
    }
}
