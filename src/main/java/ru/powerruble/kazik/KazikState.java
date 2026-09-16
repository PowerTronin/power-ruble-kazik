package ru.powerruble.kazik;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public final class KazikState extends PersistentState {
    private static final String STATE_KEY = PowerRubleKazikMod.MOD_ID;
    private static final String GLOBAL_KEY = "global";
    private static final String PLAYERS_KEY = "players";
    private static final String RECENT_KEY = "recent";
    private static final String PLAYER_RECENT_KEY = "playerRecent";
    private static final String CASINOS_KEY = "casinos";
    private static final String CASINO_RECENT_KEY = "casinoRecent";
    private static final String ORG_CASINOS_KEY = "orgCasinos";
    private static final String ENTRIES_KEY = "entries";
    private static final String PLAYER_ID_KEY = "playerId";
    private static final String PLAYER_NAME_KEY = "playerName";
    private static final String CASINO_ID_KEY = "casinoId";
    private static final String CASINO_NAME_KEY = "casinoName";
    private static final String ORGANIZATION_ID_KEY = "organizationId";
    private static final String ORGANIZATION_NAME_KEY = "organizationName";
    private static final String CREATED_AT_KEY = "createdAt";
    private static final String ENABLED_KEY = "enabled";
    private static final String STATS_KEY = "stats";
    private static final String GAMES_PLAYED_KEY = "gamesPlayed";
    private static final String WINS_KEY = "wins";
    private static final String LOSSES_KEY = "losses";
    private static final String DRAWS_KEY = "draws";
    private static final String TOTAL_BET_KEY = "totalBet";
    private static final String TOTAL_WON_KEY = "totalWon";
    private static final String TOTAL_LOST_KEY = "totalLost";
    private static final String BIGGEST_WIN_KEY = "biggestWin";
    private static final String BIGGEST_LOSS_KEY = "biggestLoss";
    private static final String TIME_KEY = "time";
    private static final String GAME_KEY = "game";
    private static final String OUTCOME_KEY = "outcome";
    private static final String PAYOUT_KEY = "payout";
    private static final String LOST_KEY = "lost";
    private static final String DETAIL_KEY = "detail";

    private GlobalStats globalStats = GlobalStats.empty();
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    private final List<GameEntry> recentEntries = new ArrayList<>();
    private final Map<UUID, List<GameEntry>> playerRecentEntries = new HashMap<>();
    private final Map<UUID, CasinoStats> casinoStats = new HashMap<>();
    private final Map<UUID, List<GameEntry>> casinoRecentEntries = new HashMap<>();
    private final Map<UUID, OrgCasino> orgCasinos = new HashMap<>();

    public static KazikState get(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return manager.getOrCreate(KazikState::fromNbt, KazikState::new, STATE_KEY);
    }

    public static KazikState fromNbt(NbtCompound nbt) {
        KazikState state = new KazikState();
        if (nbt.contains(GLOBAL_KEY, NbtElement.COMPOUND_TYPE)) {
            state.globalStats = GlobalStats.fromNbt(nbt.getCompound(GLOBAL_KEY));
        }

        if (nbt.contains(PLAYERS_KEY, NbtElement.LIST_TYPE)) {
            NbtList players = nbt.getList(PLAYERS_KEY, NbtElement.COMPOUND_TYPE);
            for (int index = 0; index < players.size(); index++) {
                PlayerStats stats = PlayerStats.fromNbt(players.getCompound(index));
                if (stats != null) {
                    state.playerStats.put(stats.playerId(), stats);
                }
            }
        }

        if (nbt.contains(RECENT_KEY, NbtElement.LIST_TYPE)) {
            NbtList recent = nbt.getList(RECENT_KEY, NbtElement.COMPOUND_TYPE);
            for (int index = 0; index < recent.size(); index++) {
                GameEntry entry = GameEntry.fromNbt(recent.getCompound(index));
                if (entry != null) {
                    state.recentEntries.add(entry);
                }
            }
        }

        if (nbt.contains(PLAYER_RECENT_KEY, NbtElement.LIST_TYPE)) {
            NbtList playerRecent = nbt.getList(PLAYER_RECENT_KEY, NbtElement.COMPOUND_TYPE);
            for (int index = 0; index < playerRecent.size(); index++) {
                NbtCompound playerHistory = playerRecent.getCompound(index);
                try {
                    UUID playerId = UUID.fromString(playerHistory.getString(PLAYER_ID_KEY));
                    NbtList entries = playerHistory.getList(ENTRIES_KEY, NbtElement.COMPOUND_TYPE);
                    List<GameEntry> loadedEntries = new ArrayList<>();
                    for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                        GameEntry entry = GameEntry.fromNbt(entries.getCompound(entryIndex));
                        if (entry != null) {
                            loadedEntries.add(entry);
                        }
                    }
                    state.playerRecentEntries.put(playerId, loadedEntries);
                } catch (RuntimeException exception) {
                    PowerRubleKazikMod.logger().warn("Skipping invalid Kazik player history entry", exception);
                }
            }
        }

        if (nbt.contains(CASINOS_KEY, NbtElement.LIST_TYPE)) {
            NbtList casinos = nbt.getList(CASINOS_KEY, NbtElement.COMPOUND_TYPE);
            for (int index = 0; index < casinos.size(); index++) {
                CasinoStats stats = CasinoStats.fromNbt(casinos.getCompound(index));
                if (stats != null) {
                    state.casinoStats.put(stats.casinoId(), stats);
                }
            }
        }

        if (nbt.contains(CASINO_RECENT_KEY, NbtElement.LIST_TYPE)) {
            NbtList casinoRecent = nbt.getList(CASINO_RECENT_KEY, NbtElement.COMPOUND_TYPE);
            for (int index = 0; index < casinoRecent.size(); index++) {
                NbtCompound casinoHistory = casinoRecent.getCompound(index);
                try {
                    UUID casinoId = UUID.fromString(casinoHistory.getString(CASINO_ID_KEY));
                    NbtList entries = casinoHistory.getList(ENTRIES_KEY, NbtElement.COMPOUND_TYPE);
                    List<GameEntry> loadedEntries = new ArrayList<>();
                    for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                        GameEntry entry = GameEntry.fromNbt(entries.getCompound(entryIndex));
                        if (entry != null) {
                            loadedEntries.add(entry);
                        }
                    }
                    state.casinoRecentEntries.put(casinoId, loadedEntries);
                } catch (RuntimeException exception) {
                    PowerRubleKazikMod.logger().warn("Skipping invalid Kazik casino history entry", exception);
                }
            }
        }

        if (nbt.contains(ORG_CASINOS_KEY, NbtElement.LIST_TYPE)) {
            NbtList orgCasinoData = nbt.getList(ORG_CASINOS_KEY, NbtElement.COMPOUND_TYPE);
            for (int index = 0; index < orgCasinoData.size(); index++) {
                OrgCasino casino = OrgCasino.fromNbt(orgCasinoData.getCompound(index));
                if (casino != null) {
                    state.orgCasinos.put(casino.organizationId(), casino);
                }
            }
        }

        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.put(GLOBAL_KEY, globalStats.toNbt());

        NbtList players = new NbtList();
        playerStats.values().stream()
            .sorted(Comparator.comparing(PlayerStats::playerName, String.CASE_INSENSITIVE_ORDER))
            .forEach(stats -> players.add(stats.toNbt()));
        nbt.put(PLAYERS_KEY, players);

        NbtList recent = new NbtList();
        recentEntries.forEach(entry -> recent.add(entry.toNbt()));
        nbt.put(RECENT_KEY, recent);

        NbtList playerRecent = new NbtList();
        playerRecentEntries.entrySet().stream()
            .sorted(Comparator.comparing(entry -> playerStats(entry.getKey()).playerName(), String.CASE_INSENSITIVE_ORDER))
            .forEach(entry -> {
                NbtCompound playerHistory = new NbtCompound();
                playerHistory.putString(PLAYER_ID_KEY, entry.getKey().toString());
                NbtList entries = new NbtList();
                entry.getValue().forEach(gameEntry -> entries.add(gameEntry.toNbt()));
                playerHistory.put(ENTRIES_KEY, entries);
                playerRecent.add(playerHistory);
            });
        nbt.put(PLAYER_RECENT_KEY, playerRecent);

        NbtList casinos = new NbtList();
        casinoStats.values().stream()
            .sorted(Comparator.comparing(CasinoStats::casinoName, String.CASE_INSENSITIVE_ORDER))
            .forEach(stats -> casinos.add(stats.toNbt()));
        nbt.put(CASINOS_KEY, casinos);

        NbtList casinoRecent = new NbtList();
        casinoRecentEntries.entrySet().stream()
            .sorted(Comparator.comparing(entry -> casinoStats(entry.getKey()).casinoName(), String.CASE_INSENSITIVE_ORDER))
            .forEach(entry -> {
                NbtCompound casinoHistory = new NbtCompound();
                casinoHistory.putString(CASINO_ID_KEY, entry.getKey().toString());
                NbtList entries = new NbtList();
                entry.getValue().forEach(gameEntry -> entries.add(gameEntry.toNbt()));
                casinoHistory.put(ENTRIES_KEY, entries);
                casinoRecent.add(casinoHistory);
            });
        nbt.put(CASINO_RECENT_KEY, casinoRecent);

        NbtList orgCasinoData = new NbtList();
        orgCasinos.values().stream()
            .sorted(Comparator.comparing(OrgCasino::organizationName, String.CASE_INSENSITIVE_ORDER))
            .forEach(casino -> orgCasinoData.add(casino.toNbt()));
        nbt.put(ORG_CASINOS_KEY, orgCasinoData);
        return nbt;
    }

    public GlobalStats globalStats() {
        return globalStats;
    }

    public PlayerStats playerStats(UUID playerId) {
        return playerStats.getOrDefault(playerId, PlayerStats.empty(playerId, playerId.toString()));
    }

    public CasinoStats casinoStats(UUID casinoId) {
        return casinoStats.getOrDefault(casinoId, CasinoStats.empty(casinoId, casinoId.toString()));
    }

    public List<GameEntry> casinoRecentEntries(UUID casinoId, int limit) {
        return casinoRecentEntries.getOrDefault(casinoId, List.of()).stream()
            .limit(limit)
            .toList();
    }

    public List<OrgCasino> orgCasinos() {
        return orgCasinos.values().stream()
            .filter(OrgCasino::enabled)
            .sorted(Comparator.comparing(OrgCasino::organizationName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public boolean isOrgCasinoEnabled(UUID organizationId) {
        OrgCasino casino = orgCasinos.get(organizationId);
        return casino != null && casino.enabled();
    }

    public boolean enableOrgCasino(UUID organizationId, String organizationName) {
        OrgCasino current = orgCasinos.get(organizationId);
        if (current != null && current.enabled()) {
            return false;
        }

        Instant createdAt = current == null ? Instant.now() : current.createdAt();
        orgCasinos.put(organizationId, new OrgCasino(organizationId, organizationName, createdAt, true));
        rememberCasinoName(organizationId, organizationName);
        markDirty();
        return true;
    }

    public boolean disableOrgCasino(UUID organizationId) {
        OrgCasino current = orgCasinos.get(organizationId);
        if (current == null || !current.enabled()) {
            return false;
        }

        orgCasinos.put(organizationId, current.withEnabled(false));
        markDirty();
        return true;
    }

    public void rememberPlayerName(UUID playerId, String playerName) {
        PlayerStats current = playerStats.get(playerId);
        if (current != null && playerName != null && !playerName.isBlank() && !current.playerName().equals(playerName)) {
            playerStats.put(playerId, current.withPlayerName(playerName));
            markDirty();
        }
    }

    public void rememberCasinoName(UUID casinoId, String casinoName) {
        CasinoStats currentStats = casinoStats.get(casinoId);
        if (currentStats != null && casinoName != null && !casinoName.isBlank() && !currentStats.casinoName().equals(casinoName)) {
            casinoStats.put(casinoId, currentStats.withCasinoName(casinoName));
            markDirty();
        }

        OrgCasino currentCasino = orgCasinos.get(casinoId);
        if (currentCasino != null && casinoName != null && !casinoName.isBlank() && !currentCasino.organizationName().equals(casinoName)) {
            orgCasinos.put(casinoId, currentCasino.withOrganizationName(casinoName));
            markDirty();
        }
    }

    public void recordGame(UUID playerId, String playerName, KazikGameEngine.GameResult result, int recentLimit, int perPlayerRecentLimit) {
        recordGame(playerId, playerName, null, null, result, recentLimit, perPlayerRecentLimit);
    }

    public void recordGame(
        UUID playerId,
        String playerName,
        UUID casinoId,
        String casinoName,
        KazikGameEngine.GameResult result,
        int recentLimit,
        int perPlayerRecentLimit
    ) {
        PlayerStats current = playerStats.getOrDefault(playerId, PlayerStats.empty(playerId, playerName));
        PlayerStats nextPlayerStats = current.record(playerName, result);
        playerStats.put(playerId, nextPlayerStats);
        globalStats = globalStats.record(result);

        GameEntry entry = GameEntry.fromResult(Instant.now(), playerId, playerName, result);
        recentEntries.add(0, entry);
        trimRecentEntries(recentLimit);

        List<GameEntry> playerEntries = playerRecentEntries.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        playerEntries.add(0, entry);
        trimEntries(playerEntries, perPlayerRecentLimit);

        if (casinoId != null && casinoName != null && !casinoName.isBlank()) {
            CasinoStats currentCasino = casinoStats.getOrDefault(casinoId, CasinoStats.empty(casinoId, casinoName));
            casinoStats.put(casinoId, currentCasino.record(casinoName, result));

            List<GameEntry> casinoEntries = casinoRecentEntries.computeIfAbsent(casinoId, ignored -> new ArrayList<>());
            casinoEntries.add(0, entry);
            trimEntries(casinoEntries, recentLimit);
        }
        markDirty();
    }

    public void recordGame(UUID playerId, String playerName, KazikGameEngine.GameResult result, int recentLimit) {
        recordGame(playerId, playerName, result, recentLimit, recentLimit);
    }

    public List<GameEntry> recentEntries(int limit) {
        return recentEntries.stream()
            .limit(limit)
            .toList();
    }

    public List<GameEntry> recentEntries(UUID playerId, int limit) {
        return playerRecentEntries.getOrDefault(playerId, List.of()).stream()
            .limit(limit)
            .toList();
    }

    public List<PlayerStats> topByWins(int limit) {
        return playerStats.values().stream()
            .sorted(Comparator.comparingLong(PlayerStats::totalWon).reversed())
            .limit(limit)
            .toList();
    }

    public List<PlayerStats> topByLosses(int limit) {
        return playerStats.values().stream()
            .sorted(Comparator.comparingLong(PlayerStats::totalLost).reversed())
            .limit(limit)
            .toList();
    }

    public List<PlayerStats> topByProfit(int limit) {
        return playerStats.values().stream()
            .sorted(Comparator.comparingLong(PlayerStats::netProfit).reversed())
            .limit(limit)
            .toList();
    }

    private void trimRecentEntries(int recentLimit) {
        trimEntries(recentEntries, recentLimit);
    }

    private static void trimEntries(List<?> entries, int limit) {
        int maxEntries = Math.max(1, limit);
        while (entries.size() > maxEntries) {
            entries.remove(entries.size() - 1);
        }
    }

    public record GlobalStats(
        long gamesPlayed,
        long wins,
        long losses,
        long draws,
        long totalBet,
        long totalWon,
        long totalLost,
        long biggestWin,
        long biggestLoss
    ) {
        static GlobalStats empty() {
            return new GlobalStats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        GlobalStats record(KazikGameEngine.GameResult result) {
            return new GlobalStats(
                safeAdd(gamesPlayed, 1L),
                safeAdd(wins, result.outcome() == KazikGameEngine.Outcome.WIN ? 1L : 0L),
                safeAdd(losses, result.outcome() == KazikGameEngine.Outcome.LOSS ? 1L : 0L),
                safeAdd(draws, result.outcome() == KazikGameEngine.Outcome.DRAW ? 1L : 0L),
                safeAdd(totalBet, result.bet()),
                safeAdd(totalWon, result.payout()),
                safeAdd(totalLost, result.lost()),
                Math.max(biggestWin, result.payout()),
                Math.max(biggestLoss, result.lost())
            );
        }

        public long houseProfit() {
            return safeSubtract(totalLost, totalWon);
        }

        private NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putLong(GAMES_PLAYED_KEY, gamesPlayed);
            nbt.putLong(WINS_KEY, wins);
            nbt.putLong(LOSSES_KEY, losses);
            nbt.putLong(DRAWS_KEY, draws);
            nbt.putLong(TOTAL_BET_KEY, totalBet);
            nbt.putLong(TOTAL_WON_KEY, totalWon);
            nbt.putLong(TOTAL_LOST_KEY, totalLost);
            nbt.putLong(BIGGEST_WIN_KEY, biggestWin);
            nbt.putLong(BIGGEST_LOSS_KEY, biggestLoss);
            return nbt;
        }

        private static GlobalStats fromNbt(NbtCompound nbt) {
            return new GlobalStats(
                nbt.getLong(GAMES_PLAYED_KEY),
                nbt.getLong(WINS_KEY),
                nbt.getLong(LOSSES_KEY),
                nbt.getLong(DRAWS_KEY),
                nbt.getLong(TOTAL_BET_KEY),
                nbt.getLong(TOTAL_WON_KEY),
                nbt.getLong(TOTAL_LOST_KEY),
                nbt.getLong(BIGGEST_WIN_KEY),
                nbt.getLong(BIGGEST_LOSS_KEY)
            );
        }
    }

    public record CasinoStats(
        UUID casinoId,
        String casinoName,
        GlobalStats stats
    ) {
        static CasinoStats empty(UUID casinoId, String casinoName) {
            return new CasinoStats(casinoId, casinoName, GlobalStats.empty());
        }

        CasinoStats withCasinoName(String nextCasinoName) {
            return new CasinoStats(casinoId, nextCasinoName, stats);
        }

        CasinoStats record(String nextCasinoName, KazikGameEngine.GameResult result) {
            return new CasinoStats(casinoId, nextCasinoName, stats.record(result));
        }

        private NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString(CASINO_ID_KEY, casinoId.toString());
            nbt.putString(CASINO_NAME_KEY, casinoName);
            nbt.put(STATS_KEY, stats.toNbt());
            return nbt;
        }

        private static CasinoStats fromNbt(NbtCompound nbt) {
            try {
                return new CasinoStats(
                    UUID.fromString(nbt.getString(CASINO_ID_KEY)),
                    nbt.getString(CASINO_NAME_KEY),
                    GlobalStats.fromNbt(nbt.getCompound(STATS_KEY))
                );
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    public record OrgCasino(
        UUID organizationId,
        String organizationName,
        Instant createdAt,
        boolean enabled
    ) {
        OrgCasino withOrganizationName(String nextOrganizationName) {
            return new OrgCasino(organizationId, nextOrganizationName, createdAt, enabled);
        }

        OrgCasino withEnabled(boolean nextEnabled) {
            return new OrgCasino(organizationId, organizationName, createdAt, nextEnabled);
        }

        private NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString(ORGANIZATION_ID_KEY, organizationId.toString());
            nbt.putString(ORGANIZATION_NAME_KEY, organizationName);
            nbt.putString(CREATED_AT_KEY, createdAt.toString());
            nbt.putBoolean(ENABLED_KEY, enabled);
            return nbt;
        }

        private static OrgCasino fromNbt(NbtCompound nbt) {
            try {
                return new OrgCasino(
                    UUID.fromString(nbt.getString(ORGANIZATION_ID_KEY)),
                    nbt.getString(ORGANIZATION_NAME_KEY),
                    Instant.parse(nbt.getString(CREATED_AT_KEY)),
                    !nbt.contains(ENABLED_KEY) || nbt.getBoolean(ENABLED_KEY)
                );
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    public record PlayerStats(
        UUID playerId,
        String playerName,
        long gamesPlayed,
        long wins,
        long losses,
        long draws,
        long totalBet,
        long totalWon,
        long totalLost,
        long biggestWin,
        long biggestLoss
    ) {
        static PlayerStats empty(UUID playerId, String playerName) {
            return new PlayerStats(playerId, playerName, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        PlayerStats withPlayerName(String nextPlayerName) {
            return new PlayerStats(playerId, nextPlayerName, gamesPlayed, wins, losses, draws, totalBet, totalWon, totalLost, biggestWin, biggestLoss);
        }

        PlayerStats record(String nextPlayerName, KazikGameEngine.GameResult result) {
            return new PlayerStats(
                playerId,
                nextPlayerName,
                safeAdd(gamesPlayed, 1L),
                safeAdd(wins, result.outcome() == KazikGameEngine.Outcome.WIN ? 1L : 0L),
                safeAdd(losses, result.outcome() == KazikGameEngine.Outcome.LOSS ? 1L : 0L),
                safeAdd(draws, result.outcome() == KazikGameEngine.Outcome.DRAW ? 1L : 0L),
                safeAdd(totalBet, result.bet()),
                safeAdd(totalWon, result.payout()),
                safeAdd(totalLost, result.lost()),
                Math.max(biggestWin, result.payout()),
                Math.max(biggestLoss, result.lost())
            );
        }

        public long netProfit() {
            return safeSubtract(totalWon, totalLost);
        }

        private NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString(PLAYER_ID_KEY, playerId.toString());
            nbt.putString(PLAYER_NAME_KEY, playerName);
            nbt.putLong(GAMES_PLAYED_KEY, gamesPlayed);
            nbt.putLong(WINS_KEY, wins);
            nbt.putLong(LOSSES_KEY, losses);
            nbt.putLong(DRAWS_KEY, draws);
            nbt.putLong(TOTAL_BET_KEY, totalBet);
            nbt.putLong(TOTAL_WON_KEY, totalWon);
            nbt.putLong(TOTAL_LOST_KEY, totalLost);
            nbt.putLong(BIGGEST_WIN_KEY, biggestWin);
            nbt.putLong(BIGGEST_LOSS_KEY, biggestLoss);
            return nbt;
        }

        private static PlayerStats fromNbt(NbtCompound nbt) {
            try {
                return new PlayerStats(
                    UUID.fromString(nbt.getString(PLAYER_ID_KEY)),
                    nbt.getString(PLAYER_NAME_KEY),
                    nbt.getLong(GAMES_PLAYED_KEY),
                    nbt.getLong(WINS_KEY),
                    nbt.getLong(LOSSES_KEY),
                    nbt.getLong(DRAWS_KEY),
                    nbt.getLong(TOTAL_BET_KEY),
                    nbt.getLong(TOTAL_WON_KEY),
                    nbt.getLong(TOTAL_LOST_KEY),
                    nbt.getLong(BIGGEST_WIN_KEY),
                    nbt.getLong(BIGGEST_LOSS_KEY)
                );
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    public record GameEntry(
        Instant time,
        UUID playerId,
        String playerName,
        String game,
        KazikGameEngine.Outcome outcome,
        long bet,
        long payout,
        long lost,
        String detail
    ) {
        static GameEntry fromResult(Instant time, UUID playerId, String playerName, KazikGameEngine.GameResult result) {
            return new GameEntry(time, playerId, playerName, result.game(), result.outcome(), result.bet(), result.payout(), result.lost(), result.detail());
        }

        private NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString(TIME_KEY, time.toString());
            nbt.putString(PLAYER_ID_KEY, playerId.toString());
            nbt.putString(PLAYER_NAME_KEY, playerName);
            nbt.putString(GAME_KEY, game);
            nbt.putString(OUTCOME_KEY, outcome.name());
            nbt.putLong(TOTAL_BET_KEY, bet);
            nbt.putLong(PAYOUT_KEY, payout);
            nbt.putLong(LOST_KEY, lost);
            nbt.putString(DETAIL_KEY, detail);
            return nbt;
        }

        private static GameEntry fromNbt(NbtCompound nbt) {
            try {
                return new GameEntry(
                    Instant.parse(nbt.getString(TIME_KEY)),
                    UUID.fromString(nbt.getString(PLAYER_ID_KEY)),
                    nbt.getString(PLAYER_NAME_KEY),
                    nbt.getString(GAME_KEY),
                    KazikGameEngine.Outcome.valueOf(nbt.getString(OUTCOME_KEY)),
                    nbt.getLong(TOTAL_BET_KEY),
                    nbt.getLong(PAYOUT_KEY),
                    nbt.getLong(LOST_KEY),
                    nbt.getString(DETAIL_KEY)
                );
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    private static long safeAdd(long current, long delta) {
        if (delta > 0L && current > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }

        if (delta < 0L && current < Long.MIN_VALUE - delta) {
            return Long.MIN_VALUE;
        }

        return current + delta;
    }

    private static long safeSubtract(long current, long delta) {
        if (delta > 0L && current < Long.MIN_VALUE + delta) {
            return Long.MIN_VALUE;
        }

        if (delta < 0L && current > Long.MAX_VALUE + delta) {
            return Long.MAX_VALUE;
        }

        return current - delta;
    }
}
