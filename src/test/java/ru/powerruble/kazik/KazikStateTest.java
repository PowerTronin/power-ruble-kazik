package ru.powerruble.kazik;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

final class KazikStateTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void recordGameUpdatesGlobalAndPlayerStats() {
        KazikState state = new KazikState();

        state.recordGame(ALICE, "Alice", KazikGameEngine.GameResult.win("coin", 100L, 75L, "landed heads"), 10, 10);
        state.recordGame(BOB, "Bob", KazikGameEngine.GameResult.loss("slots", 40L, "cherry | lemon | bell"), 10, 10);

        assertEquals(2L, state.globalStats().gamesPlayed());
        assertEquals(1L, state.globalStats().wins());
        assertEquals(1L, state.globalStats().losses());
        assertEquals(140L, state.globalStats().totalBet());
        assertEquals(75L, state.globalStats().totalWon());
        assertEquals(40L, state.globalStats().totalLost());
        assertEquals(-35L, state.globalStats().houseProfit());

        KazikState.PlayerStats aliceStats = state.playerStats(ALICE);
        assertEquals("Alice", aliceStats.playerName());
        assertEquals(75L, aliceStats.netProfit());
        assertEquals(1L, aliceStats.wins());

        KazikState.PlayerStats bobStats = state.playerStats(BOB);
        assertEquals("Bob", bobStats.playerName());
        assertEquals(-40L, bobStats.netProfit());
        assertEquals(1L, bobStats.losses());
    }

    @Test
    void recentEntriesAreTrimmedGloballyAndPerPlayer() {
        KazikState state = new KazikState();

        state.recordGame(ALICE, "Alice", KazikGameEngine.GameResult.loss("coin", 10L, "landed tails"), 2, 1);
        state.recordGame(ALICE, "Alice", KazikGameEngine.GameResult.win("coin", 10L, 9L, "landed heads"), 2, 1);
        state.recordGame(BOB, "Bob", KazikGameEngine.GameResult.loss("slots", 20L, "cherry | lemon | bell"), 2, 1);

        assertEquals(2, state.recentEntries(10).size());
        assertEquals("Bob", state.recentEntries(10).get(0).playerName());
        assertEquals(1, state.recentEntries(ALICE, 10).size());
        assertEquals(KazikGameEngine.Outcome.WIN, state.recentEntries(ALICE, 10).get(0).outcome());
    }

    @Test
    void stateRoundTripsThroughNbt() {
        KazikState state = new KazikState();
        state.recordGame(ALICE, "Alice", KazikGameEngine.GameResult.win("dice", 50L, 60L, "rolled 1, target under 50"), 10, 10);

        KazikState loaded = KazikState.fromNbt(state.writeNbt(new NbtCompound()));

        assertEquals(1L, loaded.globalStats().gamesPlayed());
        assertEquals(60L, loaded.playerStats(ALICE).totalWon());
        assertEquals(1, loaded.recentEntries(10).size());
        assertEquals(1, loaded.recentEntries(ALICE, 10).size());
        assertEquals("dice", loaded.recentEntries(10).get(0).game());
    }
}
