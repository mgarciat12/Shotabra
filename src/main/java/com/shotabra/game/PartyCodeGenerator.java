package com.shotabra.game;

import java.util.random.RandomGenerator;

/** Códigos de party de 4 letras sin caracteres ambiguos (sin I, L ni O; sin dígitos). */
public final class PartyCodeGenerator {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ";
    public static final int LENGTH = 4;

    private PartyCodeGenerator() {
    }

    public static String generate(RandomGenerator random) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Normaliza lo que escribe el usuario ("abcd " → "ABCD"); devuelve null si no es válido. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.strip().toUpperCase(java.util.Locale.ROOT);
        if (code.length() != LENGTH) {
            return null;
        }
        for (char c : code.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return null;
            }
        }
        return code;
    }
}
