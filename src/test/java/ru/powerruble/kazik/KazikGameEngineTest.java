package ru.powerruble.kazik;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

final class KazikGameEngineTest {
    @Test
    void coinPaysConfiguredNetProfitOnWin() {
        KazikGameEngine.GameResult result = KazikGameEngine.coin(new FixedRandom(true), 100L, "heads", 0.95D);

        assertEquals(KazikGameEngine.Outcome.WIN, result.outcome());
        assertEquals(95L, result.payout());
        assertEquals(0L, result.lost());
    }

    @Test
    void coinLosesFullBetOnWrongGuess() {
        KazikGameEngine.GameResult result = KazikGameEngine.coin(new FixedRandom(false), 100L, "heads", 0.95D);

        assertEquals(KazikGameEngine.Outcome.LOSS, result.outcome());
        assertEquals(0L, result.payout());
        assertEquals(100L, result.lost());
    }

    @Test
    void diceMultiplierUsesWinChanceAndHouseEdge() {
        double multiplier = KazikGameEngine.dicePayoutMultiplier(KazikGameEngine.DiceDirection.UNDER, 50, 2.0D);

        assertEquals(1.0D, multiplier, 0.001D);
    }

    @Test
    void slotsMultiplierPaysPairsAndCapsTriples() {
        assertEquals(1.0D, KazikGameEngine.slotsMultiplier("cherry", "cherry", "lemon", 25.0D));
        assertEquals(10.0D, KazikGameEngine.slotsMultiplier("diamond", "diamond", "diamond", 25.0D));
        assertEquals(4.0D, KazikGameEngine.slotsMultiplier("seven", "seven", "seven", 4.0D));
        assertEquals(0.0D, KazikGameEngine.slotsMultiplier("cherry", "lemon", "bell", 25.0D));
    }

    private static final class FixedRandom extends Random {
        private final boolean booleanValue;

        private FixedRandom(boolean booleanValue) {
            this.booleanValue = booleanValue;
        }

        @Override
        public boolean nextBoolean() {
            return booleanValue;
        }
    }
}
