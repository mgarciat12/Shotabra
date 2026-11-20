package com.shotabra.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.LongStream;

import com.shotabra.domain.Word;
import org.junit.jupiter.api.Test;

class SmallRulesTest {

    private final Random random = new Random(7);

    @Test
    void partyCodesHaveNoAmbiguousCharacters() {
        for (int i = 0; i < 2000; i++) {
            String code = PartyCodeGenerator.generate(random);
            assertThat(code).hasSize(4).matches("[A-Z]{4}").doesNotContain("O", "I", "L", "0", "1");
        }
    }

    @Test
    void normalizesTypedCodes() {
        assertThat(PartyCodeGenerator.normalize(" abcd ")).isEqualTo("ABCD");
        assertThat(PartyCodeGenerator.normalize("AB0D")).isNull();
        assertThat(PartyCodeGenerator.normalize("ABC")).isNull();
        assertThat(PartyCodeGenerator.normalize(null)).isNull();
    }

    @Test
    void thereAreAbout30DistinctEmojis() {
        assertThat(Emojis.ALL).hasSize(30).doesNotHaveDuplicates();
    }

    @Test
    void namesAreTrimmedAndLimitedTo16Characters() {
        assertThat(PlayerNames.normalize("  Ana   María ")).isEqualTo("Ana María");
        assertThat(PlayerNames.normalize("Ñandú José 1234")).isEqualTo("Ñandú José 1234");
        assertThat(PlayerNames.normalize("x".repeat(16))).hasSize(16);
        assertThatThrownBy(() -> PlayerNames.normalize("x".repeat(17))).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> PlayerNames.normalize("   ")).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> PlayerNames.normalize("<script>")).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> PlayerNames.normalize("a\u0000b")).isInstanceOf(GameException.class);
    }

    @Test
    void wordPickerReturnsDistinctWordsPreferringUnused() {
        List<Word> all = LongStream.rangeClosed(1, 20).mapToObj(id -> word(id, "w" + id)).toList();
        Set<Long> used = new HashSet<>(LongStream.rangeClosed(1, 15).boxed().toList());

        List<Word> picked = WordPicker.pick(all, used, 8, random);

        assertThat(picked).hasSize(8).doesNotHaveDuplicates();
        // Las 5 sin usar (16..20) entran todas; el resto se completa con usadas.
        assertThat(picked).extracting(Word::getId).contains(16L, 17L, 18L, 19L, 20L);
    }

    @Test
    void wordPickerFailsWhenThereAreNotEnoughWords() {
        List<Word> two = List.of(word(1, "a"), word(2, "b"));
        assertThatThrownBy(() -> WordPicker.pick(two, Set.of(), 3, random)).isInstanceOf(GameException.class);
    }

    private static Word word(long id, String text) {
        Word w = new Word(text, "test");
        try {
            Field f = Word.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(w, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return w;
    }
}
