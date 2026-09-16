package ru.powerruble.kazik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class KazikConfigTest {
    @TempDir
    private Path tempDir;

    @Test
    void loadCreatesJson5DefaultsWhenNoConfigExists() throws IOException {
        KazikConfig config = KazikConfig.load(tempDir);

        Path path = tempDir.resolve("power-ruble-kazik.json5");
        assertTrue(Files.exists(path));
        assertEquals("kazik", config.houseAccountName());
        assertEquals(1L, config.minBetAmount());
        assertEquals(10_000L, config.maxBetAmount());
        assertTrue(Files.readString(path).contains("payoutMultiplier"));
    }

    @Test
    void loadReadsJson5WithComments() throws IOException {
        Files.writeString(tempDir.resolve("power-ruble-kazik.json5"), """
            {
              house: {
                accountName: "casino",
                finiteBankroll: false,
                publicBalance: false,
              },
              bets: {
                minAmount: 5,
                maxAmount: 500,
                cooldownSeconds: 7,
                allowDebt: true,
              },
              games: {
                coin: {
                  // Net profit multiplier.
                  enabled: false,
                  payoutMultiplier: 0.9,
                },
                dice: {
                  enabled: true,
                  minTarget: 10,
                  maxTarget: 90,
                  houseEdgePercent: 5.5,
                },
                slots: {
                  enabled: false,
                  maxPayoutMultiplier: 12.5,
                },
              },
              stats: {
                enabled: false,
                publicPlayerStats: true,
                publicServerStats: false,
                publicTop: false,
                topSize: 3,
                recentEntries: 9,
                perPlayerHistoryEntries: 4,
              },
            }
            """);

        KazikConfig config = KazikConfig.load(tempDir);

        assertEquals("casino", config.houseAccountName());
        assertFalse(config.finiteBankroll());
        assertFalse(config.publicBalance());
        assertEquals(5L, config.minBetAmount());
        assertEquals(500L, config.maxBetAmount());
        assertEquals(7, config.betCooldownSeconds());
        assertTrue(config.allowDebt());
        assertFalse(config.coinEnabled());
        assertEquals(0.9D, config.coinPayoutMultiplier());
        assertEquals(10, config.diceMinTarget());
        assertEquals(90, config.diceMaxTarget());
        assertEquals(5.5D, config.diceHouseEdgePercent());
        assertFalse(config.slotsEnabled());
        assertEquals(12.5D, config.slotsMaxPayoutMultiplier());
        assertFalse(config.statsEnabled());
        assertTrue(config.publicPlayerStats());
        assertFalse(config.publicServerStats());
        assertFalse(config.publicTop());
        assertEquals(3, config.topSize());
        assertEquals(9, config.recentEntries());
        assertEquals(4, config.perPlayerHistoryEntries());
    }

    @Test
    void invalidExistingJson5DoesNotGetOverwritten() throws IOException {
        Path path = tempDir.resolve("power-ruble-kazik.json5");
        Files.writeString(path, "{ invalid");

        KazikConfig config = KazikConfig.load(tempDir);

        assertEquals("kazik", config.houseAccountName());
        assertEquals("{ invalid", Files.readString(path));
    }
}
