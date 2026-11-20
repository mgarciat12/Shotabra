package com.shotabra.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Desarreglos aleatorios mediante el algoritmo de Sattolo.
 *
 * <p>Sattolo genera de forma uniforme una permutación cíclica (un único ciclo de longitud n).
 * Todo ciclo de longitud n &ge; 2 es un desarreglo: nadie se queda consigo mismo y cada elemento
 * es imagen de exactamente otro. Además, con n &ge; 3 nunca hay parejas recíprocas (A→B y B→A),
 * lo que en el juego evita que dos personas se "cacen" mutuamente.
 */
public final class Derangements {

    private Derangements() {
    }

    /**
     * @return {@code perm} tal que {@code perm[i]} es el índice del objetivo de {@code i}
     */
    public static int[] cyclic(int n, RandomGenerator random) {
        if (n < 2) {
            throw new IllegalArgumentException("Se necesitan al menos 2 elementos, hay " + n);
        }
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) {
            perm[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i); // j en [0, i): la diferencia con Fisher–Yates (que usa [0, i])
            int tmp = perm[i];
            perm[i] = perm[j];
            perm[j] = tmp;
        }
        return perm;
    }

    /** Asigna a cada elemento un objetivo distinto de sí mismo; el resultado es biyectivo. */
    public static <T> Map<T, T> assign(List<T> items, RandomGenerator random) {
        List<T> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, random);
        int[] perm = cyclic(shuffled.size(), random);
        Map<T, T> result = new LinkedHashMap<>();
        for (int i = 0; i < perm.length; i++) {
            result.put(shuffled.get(i), shuffled.get(perm[i]));
        }
        return result;
    }
}
