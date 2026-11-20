package com.shotabra.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class DerangementsTest {

    private final Random random = new Random(42);

    @ParameterizedTest(name = "N = {0}")
    @ValueSource(ints = {3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void neverAssignsSelfAndIsBijective_1000Times(int n) {
        List<String> players = IntStream.range(0, n).mapToObj(i -> "p" + i).toList();
        for (int rep = 0; rep < 1000; rep++) {
            Map<String, String> targets = Derangements.assign(players, random);

            assertThat(targets.keySet()).containsExactlyInAnyOrderElementsOf(players);           // todos cazan
            assertThat(new HashSet<>(targets.values())).containsExactlyInAnyOrderElementsOf(players); // todos son cazados una vez
            targets.forEach((hunter, target) -> assertThat(target).isNotEqualTo(hunter));        // nadie a sí mismo
        }
    }

    @ParameterizedTest(name = "N = {0}")
    @ValueSource(ints = {3, 5, 8, 12})
    void producesASingleCycleSoThereAreNoMutualPairs(int n) {
        for (int rep = 0; rep < 1000; rep++) {
            int[] perm = Derangements.cyclic(n, random);
            Set<Integer> visited = new HashSet<>();
            int current = 0;
            do {
                visited.add(current);
                current = perm[current];
            } while (current != 0);
            assertThat(visited).hasSize(n);
            for (int i = 0; i < n; i++) {
                assertThat(perm[perm[i]]).as("sin parejas recíprocas").isNotEqualTo(i);
            }
        }
    }

    @Test
    void everyValidTargetEventuallyAppears() {
        // Para N = 4, el jugador 0 debe poder tener como objetivo a 1, 2 y 3 (aleatoriedad real).
        Set<Integer> seen = new HashSet<>();
        for (int rep = 0; rep < 500; rep++) {
            seen.add(Derangements.cyclic(4, random)[0]);
        }
        assertThat(seen).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void rejectsTooFewElements() {
        assertThatThrownBy(() -> Derangements.cyclic(1, random)).isInstanceOf(IllegalArgumentException.class);
    }
}
