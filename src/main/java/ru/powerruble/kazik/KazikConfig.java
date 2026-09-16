package ru.powerruble.kazik;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.api.SyntaxError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class KazikConfig {
    private static final String CONFIG_FILE_NAME = PowerRubleKazikMod.MOD_ID + ".json5";

    private final String houseAccountName;
    private final boolean finiteBankroll;
    private final boolean publicBalance;
    private final long minBetAmount;
    private final long maxBetAmount;
    private final int betCooldownSeconds;
    private final boolean allowDebt;
    private final boolean coinEnabled;
    private final double coinPayoutMultiplier;
    private final boolean diceEnabled;
    private final int diceMinTarget;
    private final int diceMaxTarget;
    private final double diceHouseEdgePercent;
    private final boolean slotsEnabled;
    private final double slotsMaxPayoutMultiplier;
    private final boolean statsEnabled;
    private final boolean publicPlayerStats;
    private final boolean publicServerStats;
    private final boolean publicTop;
    private final int topSize;
    private final int recentEntries;
    private final int perPlayerHistoryEntries;

    private KazikConfig(
        String houseAccountName,
        boolean finiteBankroll,
        boolean publicBalance,
        long minBetAmount,
        long maxBetAmount,
        int betCooldownSeconds,
        boolean allowDebt,
        boolean coinEnabled,
        double coinPayoutMultiplier,
        boolean diceEnabled,
        int diceMinTarget,
        int diceMaxTarget,
        double diceHouseEdgePercent,
        boolean slotsEnabled,
        double slotsMaxPayoutMultiplier,
        boolean statsEnabled,
        boolean publicPlayerStats,
        boolean publicServerStats,
        boolean publicTop,
        int topSize,
        int recentEntries,
        int perPlayerHistoryEntries
    ) {
        this.houseAccountName = houseAccountName;
        this.finiteBankroll = finiteBankroll;
        this.publicBalance = publicBalance;
        this.minBetAmount = minBetAmount;
        this.maxBetAmount = maxBetAmount;
        this.betCooldownSeconds = betCooldownSeconds;
        this.allowDebt = allowDebt;
        this.coinEnabled = coinEnabled;
        this.coinPayoutMultiplier = coinPayoutMultiplier;
        this.diceEnabled = diceEnabled;
        this.diceMinTarget = diceMinTarget;
        this.diceMaxTarget = diceMaxTarget;
        this.diceHouseEdgePercent = diceHouseEdgePercent;
        this.slotsEnabled = slotsEnabled;
        this.slotsMaxPayoutMultiplier = slotsMaxPayoutMultiplier;
        this.statsEnabled = statsEnabled;
        this.publicPlayerStats = publicPlayerStats;
        this.publicServerStats = publicServerStats;
        this.publicTop = publicTop;
        this.topSize = topSize;
        this.recentEntries = recentEntries;
        this.perPlayerHistoryEntries = perPlayerHistoryEntries;
    }

    public static KazikConfig load() {
        return load(FabricLoader.getInstance().getConfigDir());
    }

    static KazikConfig load(Path configDir) {
        Path path = configDir.resolve(CONFIG_FILE_NAME);
        if (Files.exists(path)) {
            KazikConfig config = readJson5(path);
            if (config != null) {
                return config;
            }

            return defaults();
        }

        KazikConfig defaults = defaults();
        writeJson5(path, defaults);
        return defaults;
    }

    public String houseAccountName() {
        return houseAccountName;
    }

    public boolean finiteBankroll() {
        return finiteBankroll;
    }

    public boolean publicBalance() {
        return publicBalance;
    }

    public long minBetAmount() {
        return minBetAmount;
    }

    public long maxBetAmount() {
        return maxBetAmount;
    }

    public int betCooldownSeconds() {
        return betCooldownSeconds;
    }

    public boolean allowDebt() {
        return allowDebt;
    }

    public boolean coinEnabled() {
        return coinEnabled;
    }

    public double coinPayoutMultiplier() {
        return coinPayoutMultiplier;
    }

    public boolean diceEnabled() {
        return diceEnabled;
    }

    public int diceMinTarget() {
        return diceMinTarget;
    }

    public int diceMaxTarget() {
        return diceMaxTarget;
    }

    public double diceHouseEdgePercent() {
        return diceHouseEdgePercent;
    }

    public boolean slotsEnabled() {
        return slotsEnabled;
    }

    public double slotsMaxPayoutMultiplier() {
        return slotsMaxPayoutMultiplier;
    }

    public boolean statsEnabled() {
        return statsEnabled;
    }

    public boolean publicPlayerStats() {
        return publicPlayerStats;
    }

    public boolean publicServerStats() {
        return publicServerStats;
    }

    public boolean publicTop() {
        return publicTop;
    }

    public int topSize() {
        return topSize;
    }

    public int recentEntries() {
        return recentEntries;
    }

    public int perPlayerHistoryEntries() {
        return perPlayerHistoryEntries;
    }

    private static KazikConfig readJson5(Path path) {
        try {
            return fromJson(Jankson.builder().build().load(path.toFile()));
        } catch (IOException | SyntaxError exception) {
            PowerRubleKazikMod.logger().warn("Could not read {}, using defaults", path, exception);
            return null;
        }
    }

    private static KazikConfig fromJson(JsonObject root) {
        JsonObject house = section(root, "house");
        JsonObject bets = section(root, "bets");
        JsonObject games = section(root, "games");
        JsonObject coin = section(games, "coin");
        JsonObject dice = section(games, "dice");
        JsonObject slots = section(games, "slots");
        JsonObject stats = section(root, "stats");

        long minBetAmount = positiveLong(bets, "minAmount", 1L);
        long maxBetAmount = positiveLong(bets, "maxAmount", 10_000L);
        if (minBetAmount > maxBetAmount) {
            PowerRubleKazikMod.logger().warn("Config 'bets.minAmount' is greater than 'bets.maxAmount', using defaults");
            minBetAmount = 1L;
            maxBetAmount = 10_000L;
        }

        int diceMinTarget = clampInt(positiveInt(dice, "minTarget", 2), 2, 98);
        int diceMaxTarget = clampInt(positiveInt(dice, "maxTarget", 98), 2, 98);
        if (diceMinTarget > diceMaxTarget) {
            PowerRubleKazikMod.logger().warn("Config dice target range is invalid, using defaults");
            diceMinTarget = 2;
            diceMaxTarget = 98;
        }

        return new KazikConfig(
            nonBlankString(house, "accountName", "kazik"),
            booleanValue(house, "finiteBankroll", true),
            booleanValue(house, "publicBalance", true),
            minBetAmount,
            maxBetAmount,
            nonNegativeInt(bets, "cooldownSeconds", 3),
            booleanValue(bets, "allowDebt", false),
            booleanValue(coin, "enabled", true),
            nonNegativeDouble(coin, "payoutMultiplier", 0.95D),
            booleanValue(dice, "enabled", true),
            diceMinTarget,
            diceMaxTarget,
            clampDouble(nonNegativeDouble(dice, "houseEdgePercent", 3.0D), 0.0D, 99.0D),
            booleanValue(slots, "enabled", true),
            nonNegativeDouble(slots, "maxPayoutMultiplier", 25.0D),
            booleanValue(stats, "enabled", true),
            booleanValue(stats, "publicPlayerStats", false),
            booleanValue(stats, "publicServerStats", true),
            booleanValue(stats, "publicTop", true),
            positiveInt(stats, "topSize", 10),
            positiveInt(stats, "recentEntries", 50),
            positiveInt(stats, "perPlayerHistoryEntries", 20)
        );
    }

    private static KazikConfig defaults() {
        return new KazikConfig(
            "kazik",
            true,
            true,
            1L,
            10_000L,
            3,
            false,
            true,
            0.95D,
            true,
            2,
            98,
            3.0D,
            true,
            25.0D,
            true,
            false,
            true,
            true,
            10,
            50,
            20
        );
    }

    private static void writeJson5(Path path, KazikConfig config) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toJson5(config));
        } catch (IOException exception) {
            PowerRubleKazikMod.logger().warn("Could not write {}", path, exception);
        }
    }

    private static String toJson5(KazikConfig config) {
        return """
            {
              house: {
                accountName: "%s",
                finiteBankroll: %s,
                publicBalance: %s,
              },
              bets: {
                minAmount: %d,
                maxAmount: %d,
                cooldownSeconds: %d,
                allowDebt: %s,
              },
              games: {
                coin: {
                  enabled: %s,
                  // Net profit paid on win. 0.95 means +95 for a 100 bet.
                  payoutMultiplier: %s,
                },
                dice: {
                  enabled: %s,
                  minTarget: %d,
                  maxTarget: %d,
                  houseEdgePercent: %s,
                },
                slots: {
                  enabled: %s,
                  maxPayoutMultiplier: %s,
                },
              },
              stats: {
                enabled: %s,
                publicPlayerStats: %s,
                publicServerStats: %s,
                publicTop: %s,
                topSize: %d,
                recentEntries: %d,
                perPlayerHistoryEntries: %d,
              },
            }
            """.formatted(
            escape(config.houseAccountName),
            Boolean.toString(config.finiteBankroll),
            Boolean.toString(config.publicBalance),
            config.minBetAmount,
            config.maxBetAmount,
            config.betCooldownSeconds,
            Boolean.toString(config.allowDebt),
            Boolean.toString(config.coinEnabled),
            Double.toString(config.coinPayoutMultiplier),
            Boolean.toString(config.diceEnabled),
            config.diceMinTarget,
            config.diceMaxTarget,
            Double.toString(config.diceHouseEdgePercent),
            Boolean.toString(config.slotsEnabled),
            Double.toString(config.slotsMaxPayoutMultiplier),
            Boolean.toString(config.statsEnabled),
            Boolean.toString(config.publicPlayerStats),
            Boolean.toString(config.publicServerStats),
            Boolean.toString(config.publicTop),
            config.topSize,
            config.recentEntries,
            config.perPlayerHistoryEntries
        );
    }

    private static JsonObject section(JsonObject object, String key) {
        JsonObject section = object.getObject(key);
        return section == null ? new JsonObject() : section;
    }

    private static String nonBlankString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element instanceof JsonPrimitive primitive) {
            String value = primitive.asString().trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return fallback;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element instanceof JsonPrimitive primitive) {
            return primitive.asBoolean(fallback);
        }
        return fallback;
    }

    private static int positiveInt(JsonObject object, String key, int fallback) {
        long value = positiveLong(object, key, fallback);
        return value > Integer.MAX_VALUE ? fallback : (int) value;
    }

    private static int nonNegativeInt(JsonObject object, String key, int fallback) {
        long value = nonNegativeLong(object, key, fallback);
        return value > Integer.MAX_VALUE ? fallback : (int) value;
    }

    private static long positiveLong(JsonObject object, String key, long fallback) {
        long value = longValue(object, key, fallback);
        return value < 1L ? fallback : value;
    }

    private static long nonNegativeLong(JsonObject object, String key, long fallback) {
        long value = longValue(object, key, fallback);
        return value < 0L ? fallback : value;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        if (element instanceof JsonPrimitive primitive) {
            return primitive.asLong(fallback);
        }
        return fallback;
    }

    private static double nonNegativeDouble(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        double value = element instanceof JsonPrimitive primitive ? primitive.asDouble(fallback) : fallback;
        return value < 0.0D ? fallback : value;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }
}
