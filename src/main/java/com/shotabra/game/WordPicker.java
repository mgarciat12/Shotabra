package com.shotabra.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.shotabra.domain.Word;

/** Elige {@code n} palabras distintas, priorizando las que aún no se han usado en la party. */
public final class WordPicker {

    private WordPicker() {
    }

    public static List<Word> pick(List<Word> active, Set<Long> alreadyUsed, int n, RandomGenerator random) {
        if (active.size() < n) {
            throw new GameException(GameError.NOT_ENOUGH_WORDS);
        }
        List<Word> fresh = new ArrayList<>();
        List<Word> used = new ArrayList<>();
        for (Word w : active) {
            (alreadyUsed.contains(w.getId()) ? used : fresh).add(w);
        }
        Collections.shuffle(fresh, random);
        Collections.shuffle(used, random);
        List<Word> result = new ArrayList<>(n);
        for (Word w : fresh) {
            if (result.size() == n) break;
            result.add(w);
        }
        for (Word w : used) {
            if (result.size() == n) break;
            result.add(w);
        }
        return result;
    }
}
