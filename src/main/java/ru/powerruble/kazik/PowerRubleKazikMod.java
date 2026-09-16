package ru.powerruble.kazik;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.powerruble.RubleState;
import ru.powerruble.RubleTransaction;
import ru.powerruble.api.PowerRuble;
import ru.powerruble.orgcontracts.OrgRole;
import ru.powerruble.orgcontracts.OrganizationState;

public final class PowerRubleKazikMod implements ModInitializer {
    public static final String MOD_ID = "power-ruble-kazik";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final UUID HOUSE_ID = UUID.nameUUIDFromBytes("PowerRubleKazik:casino".getBytes(StandardCharsets.UTF_8));
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Instant> lastGames = new HashMap<>();

    private static final SimpleCommandExceptionType GAME_DISABLED = new SimpleCommandExceptionType(Text.literal("This casino game is disabled."));
    private static final SimpleCommandExceptionType STATS_DISABLED = new SimpleCommandExceptionType(Text.literal("Casino statistics are disabled."));
    private static final SimpleCommandExceptionType NO_PERMISSION = new SimpleCommandExceptionType(Text.literal("You do not have permission for this casino command."));
    private static final SimpleCommandExceptionType BET_TOO_SMALL = new SimpleCommandExceptionType(Text.literal("Bet is below the configured minimum."));
    private static final SimpleCommandExceptionType BET_TOO_LARGE = new SimpleCommandExceptionType(Text.literal("Bet is above the configured maximum."));
    private static final SimpleCommandExceptionType NOT_ENOUGH_MONEY = new SimpleCommandExceptionType(Text.literal("You do not have enough rubles for this bet."));
    private static final SimpleCommandExceptionType HOUSE_NOT_ENOUGH_MONEY = new SimpleCommandExceptionType(Text.literal("Casino bankroll cannot cover this possible payout."));
    private static final SimpleCommandExceptionType INVALID_DICE_TARGET = new SimpleCommandExceptionType(Text.literal("Dice target is outside configured limits."));
    private static final SimpleCommandExceptionType ORGANIZATION_REQUIRED = new SimpleCommandExceptionType(Text.literal("You are not in an organization."));
    private static final SimpleCommandExceptionType ORG_CASINO_EXISTS = new SimpleCommandExceptionType(Text.literal("Your organization already has an open casino."));
    private static final SimpleCommandExceptionType ORG_CASINO_NOT_FOUND = new SimpleCommandExceptionType(Text.literal("Organization casino not found."));

    private static KazikConfig config;

    @Override
    public void onInitialize() {
        config = KazikConfig.load();
        LOGGER.info("Power Ruble Kazik loaded. Base currency: {}", PowerRuble.api().config().currencyName());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("kazik")
                    .executes(context -> showHelp(context.getSource()))
                    .then(CommandManager.literal("help")
                        .executes(context -> showHelp(context.getSource()))
                    )
                    .then(CommandManager.literal("coin")
                        .then(CommandManager.argument("bet", LongArgumentType.longArg(1))
                            .then(CommandManager.literal("heads")
                                .executes(context -> playCoin(context.getSource(), LongArgumentType.getLong(context, "bet"), "heads"))
                            )
                            .then(CommandManager.literal("tails")
                                .executes(context -> playCoin(context.getSource(), LongArgumentType.getLong(context, "bet"), "tails"))
                            )
                        )
                    )
                    .then(CommandManager.literal("dice")
                        .then(CommandManager.argument("bet", LongArgumentType.longArg(1))
                            .then(CommandManager.literal("under")
                                .then(CommandManager.argument("target", IntegerArgumentType.integer(2, 98))
                                    .executes(context -> playDice(
                                        context.getSource(),
                                        LongArgumentType.getLong(context, "bet"),
                                        KazikGameEngine.DiceDirection.UNDER,
                                        IntegerArgumentType.getInteger(context, "target")
                                    ))
                                )
                            )
                            .then(CommandManager.literal("over")
                                .then(CommandManager.argument("target", IntegerArgumentType.integer(2, 98))
                                    .executes(context -> playDice(
                                        context.getSource(),
                                        LongArgumentType.getLong(context, "bet"),
                                        KazikGameEngine.DiceDirection.OVER,
                                        IntegerArgumentType.getInteger(context, "target")
                                    ))
                                )
                            )
                        )
                    )
                    .then(CommandManager.literal("slots")
                        .then(CommandManager.argument("bet", LongArgumentType.longArg(1))
                            .executes(context -> playSlots(context.getSource(), LongArgumentType.getLong(context, "bet")))
                        )
                    )
                    .then(CommandManager.literal("org")
                        .then(CommandManager.literal("create")
                            .executes(context -> createOrgCasino(context.getSource()))
                        )
                        .then(CommandManager.literal("close")
                            .executes(context -> closeOrgCasino(context.getSource()))
                        )
                        .then(CommandManager.literal("info")
                            .executes(context -> showOwnOrgCasinoInfo(context.getSource()))
                        )
                        .then(CommandManager.literal("list")
                            .executes(context -> listOrgCasinos(context.getSource()))
                        )
                        .then(CommandManager.literal("balance")
                            .executes(context -> showOwnOrgCasinoBalance(context.getSource()))
                            .then(CommandManager.argument("organization", StringArgumentType.word())
                                .executes(context -> showOrgCasinoBalance(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "organization")
                                ))
                            )
                        )
                        .then(CommandManager.literal("stats")
                            .executes(context -> showOwnOrgCasinoStats(context.getSource()))
                            .then(CommandManager.argument("organization", StringArgumentType.word())
                                .executes(context -> showOrgCasinoStats(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "organization")
                                ))
                            )
                        )
                        .then(CommandManager.literal("recent")
                            .executes(context -> showOwnOrgCasinoRecent(context.getSource()))
                            .then(CommandManager.argument("organization", StringArgumentType.word())
                                .executes(context -> showOrgCasinoRecent(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "organization")
                                ))
                            )
                        )
                        .then(CommandManager.literal("coin")
                            .then(CommandManager.argument("organization", StringArgumentType.word())
                                .then(CommandManager.argument("bet", LongArgumentType.longArg(1))
                                    .then(CommandManager.literal("heads")
                                        .executes(context -> playOrgCoin(
                                            context.getSource(),
                                            StringArgumentType.getString(context, "organization"),
                                            LongArgumentType.getLong(context, "bet"),
                                            "heads"
                                        ))
                                    )
                                    .then(CommandManager.literal("tails")
                                        .executes(context -> playOrgCoin(
                                            context.getSource(),
                                            StringArgumentType.getString(context, "organization"),
                                            LongArgumentType.getLong(context, "bet"),
                                            "tails"
                                        ))
                                    )
                                )
                            )
                        )
                        .then(CommandManager.literal("dice")
                            .then(CommandManager.argument("organization", StringArgumentType.word())
                                .then(CommandManager.argument("bet", LongArgumentType.longArg(1))
                                    .then(CommandManager.literal("under")
                                        .then(CommandManager.argument("target", IntegerArgumentType.integer(2, 98))
                                            .executes(context -> playOrgDice(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "organization"),
                                                LongArgumentType.getLong(context, "bet"),
                                                KazikGameEngine.DiceDirection.UNDER,
                                                IntegerArgumentType.getInteger(context, "target")
                                            ))
                                        )
                                    )
                                    .then(CommandManager.literal("over")
                                        .then(CommandManager.argument("target", IntegerArgumentType.integer(2, 98))
                                            .executes(context -> playOrgDice(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "organization"),
                                                LongArgumentType.getLong(context, "bet"),
                                                KazikGameEngine.DiceDirection.OVER,
                                                IntegerArgumentType.getInteger(context, "target")
                                            ))
                                        )
                                    )
                                )
                            )
                        )
                        .then(CommandManager.literal("slots")
                            .then(CommandManager.argument("organization", StringArgumentType.word())
                                .then(CommandManager.argument("bet", LongArgumentType.longArg(1))
                                    .executes(context -> playOrgSlots(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "organization"),
                                        LongArgumentType.getLong(context, "bet")
                                    ))
                                )
                            )
                        )
                    )
                    .then(CommandManager.literal("stats")
                        .executes(context -> showOwnStats(context.getSource()))
                        .then(CommandManager.literal("server")
                            .executes(context -> showServerStats(context.getSource()))
                        )
                        .then(CommandManager.literal("player")
                            .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .executes(context -> showPlayerStats(
                                    context.getSource(),
                                    singleProfile(GameProfileArgumentType.getProfileArgument(context, "player"))
                                ))
                            )
                        )
                    )
                    .then(CommandManager.literal("top")
                        .then(CommandManager.literal("wins")
                            .executes(context -> showTop(context.getSource(), TopMode.WINS))
                        )
                        .then(CommandManager.literal("losses")
                            .executes(context -> showTop(context.getSource(), TopMode.LOSSES))
                        )
                        .then(CommandManager.literal("profit")
                            .executes(context -> showTop(context.getSource(), TopMode.PROFIT))
                        )
                    )
                    .then(CommandManager.literal("recent")
                        .executes(context -> showRecent(context.getSource()))
                        .then(CommandManager.literal("me")
                            .executes(context -> showOwnRecent(context.getSource()))
                        )
                        .then(CommandManager.literal("player")
                            .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .executes(context -> showPlayerRecent(
                                    context.getSource(),
                                    singleProfile(GameProfileArgumentType.getProfileArgument(context, "player"))
                                ))
                            )
                        )
                    )
                    .then(CommandManager.literal("house")
                        .then(CommandManager.literal("balance")
                            .executes(context -> showHouseBalance(context.getSource()))
                        )
                        .then(CommandManager.literal("give")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> giveHouse(context.getSource(), LongArgumentType.getLong(context, "amount")))
                            )
                        )
                        .then(CommandManager.literal("take")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> takeHouse(context.getSource(), LongArgumentType.getLong(context, "amount")))
                            )
                        )
                        .then(CommandManager.literal("set")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.argument("amount", LongArgumentType.longArg(0))
                                .executes(context -> setHouse(context.getSource(), LongArgumentType.getLong(context, "amount")))
                            )
                        )
                    )
                    .then(CommandManager.literal("reload")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> reloadConfig(context.getSource()))
                    )
            );

            dispatcher.register(
                CommandManager.literal("help")
                    .then(CommandManager.literal("kazik")
                        .executes(context -> showHelp(context.getSource()))
                    )
            );
        });
    }

    private static int showHelp(ServerCommandSource source) {
        send(source, "Power Ruble Kazik commands:");
        send(source, "/kazik coin <bet> heads|tails");
        send(source, "/kazik dice <bet> under|over <target>");
        send(source, "/kazik slots <bet>");
        send(source, "/kazik org create|close|info|list");
        send(source, "/kazik org coin|dice|slots <organization> <bet> ...");
        send(source, "/kazik org balance|stats|recent [organization]");
        send(source, "/kazik stats, /kazik stats server, /kazik stats player <player>");
        send(source, "/kazik top wins|losses|profit, /kazik recent, /kazik recent me");
        send(source, "/kazik house balance");
        if (source.hasPermissionLevel(2)) {
            send(source, "/kazik house give|take|set, /kazik reload");
        }
        return 1;
    }

    private static int playCoin(ServerCommandSource source, long bet, String guess) throws CommandSyntaxException {
        if (!config.coinEnabled()) {
            throw GAME_DISABLED.create();
        }

        long maxPayout = KazikGameEngine.payout(bet, config.coinPayoutMultiplier());
        return play(source, houseAccount(), bet, maxPayout, () -> KazikGameEngine.coin(RANDOM, bet, guess, config.coinPayoutMultiplier()));
    }

    private static int playDice(ServerCommandSource source, long bet, KazikGameEngine.DiceDirection direction, int target) throws CommandSyntaxException {
        if (!config.diceEnabled()) {
            throw GAME_DISABLED.create();
        }
        if (target < config.diceMinTarget() || target > config.diceMaxTarget()) {
            throw INVALID_DICE_TARGET.create();
        }

        double multiplier = KazikGameEngine.dicePayoutMultiplier(direction, target, config.diceHouseEdgePercent());
        long maxPayout = KazikGameEngine.payout(bet, multiplier);
        return play(source, houseAccount(), bet, maxPayout, () -> KazikGameEngine.dice(RANDOM, bet, direction, target, config.diceHouseEdgePercent()));
    }

    private static int playSlots(ServerCommandSource source, long bet) throws CommandSyntaxException {
        if (!config.slotsEnabled()) {
            throw GAME_DISABLED.create();
        }

        long maxPayout = KazikGameEngine.payout(bet, config.slotsMaxPayoutMultiplier());
        return play(source, houseAccount(), bet, maxPayout, () -> KazikGameEngine.slots(RANDOM, bet, config.slotsMaxPayoutMultiplier()));
    }

    private static int playOrgCoin(ServerCommandSource source, String organizationName, long bet, String guess) throws CommandSyntaxException {
        if (!config.coinEnabled()) {
            throw GAME_DISABLED.create();
        }

        OrganizationState.Organization organization = resolveOpenOrgCasino(source, organizationName);
        long maxPayout = KazikGameEngine.payout(bet, config.coinPayoutMultiplier());
        return play(source, orgAccount(organization), bet, maxPayout, () -> KazikGameEngine.coin(RANDOM, bet, guess, config.coinPayoutMultiplier()));
    }

    private static int playOrgDice(
        ServerCommandSource source,
        String organizationName,
        long bet,
        KazikGameEngine.DiceDirection direction,
        int target
    ) throws CommandSyntaxException {
        if (!config.diceEnabled()) {
            throw GAME_DISABLED.create();
        }
        if (target < config.diceMinTarget() || target > config.diceMaxTarget()) {
            throw INVALID_DICE_TARGET.create();
        }

        OrganizationState.Organization organization = resolveOpenOrgCasino(source, organizationName);
        double multiplier = KazikGameEngine.dicePayoutMultiplier(direction, target, config.diceHouseEdgePercent());
        long maxPayout = KazikGameEngine.payout(bet, multiplier);
        return play(source, orgAccount(organization), bet, maxPayout, () -> KazikGameEngine.dice(RANDOM, bet, direction, target, config.diceHouseEdgePercent()));
    }

    private static int playOrgSlots(ServerCommandSource source, String organizationName, long bet) throws CommandSyntaxException {
        if (!config.slotsEnabled()) {
            throw GAME_DISABLED.create();
        }

        OrganizationState.Organization organization = resolveOpenOrgCasino(source, organizationName);
        long maxPayout = KazikGameEngine.payout(bet, config.slotsMaxPayoutMultiplier());
        return play(source, orgAccount(organization), bet, maxPayout, () -> KazikGameEngine.slots(RANDOM, bet, config.slotsMaxPayoutMultiplier()));
    }

    private static int play(ServerCommandSource source, CasinoAccount casino, long bet, long maxPayout, GameSupplier gameSupplier) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        validateBet(bet);
        checkCooldown(player.getUuid());
        checkPlayerCanRisk(source.getServer(), player.getUuid(), bet);
        rememberCasino(source.getServer(), casino);
        PowerRuble.api().rememberAccountName(source.getServer(), player.getUuid(), playerName(player));

        if (casino.finiteBankroll() && PowerRuble.api().getBalance(source.getServer(), casino.id()) < maxPayout) {
            throw HOUSE_NOT_ENOUGH_MONEY.create();
        }

        KazikGameEngine.GameResult result = gameSupplier.get();
        applyResult(source.getServer(), player, casino, result);
        if (config.statsEnabled()) {
            KazikState.get(source.getServer()).recordGame(
                player.getUuid(),
                playerName(player),
                casino.id(),
                casino.name(),
                result,
                config.recentEntries(),
                config.perPlayerHistoryEntries()
            );
        }
        lastGames.put(player.getUuid(), Instant.now());

        String casinoSuffix = casino.house() ? "" : " at " + casino.name();
        if (result.outcome() == KazikGameEngine.Outcome.WIN) {
            send(source, "You won " + format(result.payout()) + " in " + result.game() + casinoSuffix + " (" + result.detail() + ").");
        } else if (result.outcome() == KazikGameEngine.Outcome.LOSS) {
            send(source, "You lost " + format(result.lost()) + " in " + result.game() + casinoSuffix + " (" + result.detail() + ").");
        } else {
            send(source, "Draw in " + result.game() + casinoSuffix + " (" + result.detail() + ").");
        }
        return 1;
    }

    private static int createOrgCasino(ServerCommandSource source) throws CommandSyntaxException {
        OrganizationState.Organization organization = requireManagedOrganization(source);
        KazikState state = KazikState.get(source.getServer());
        if (!state.enableOrgCasino(organization.id(), organization.name())) {
            throw ORG_CASINO_EXISTS.create();
        }

        PowerRuble.api().rememberAccountName(source.getServer(), organization.id(), organization.name());
        send(source, "Organization casino opened for " + organization.name() + ". Bankroll: " + format(PowerRuble.api().getBalance(source.getServer(), organization.id())));
        return 1;
    }

    private static int closeOrgCasino(ServerCommandSource source) throws CommandSyntaxException {
        OrganizationState.Organization organization = requireManagedOrganization(source);
        if (!KazikState.get(source.getServer()).disableOrgCasino(organization.id())) {
            throw ORG_CASINO_NOT_FOUND.create();
        }

        send(source, "Organization casino closed for " + organization.name() + ". Organization balance is unchanged.");
        return 1;
    }

    private static int showOwnOrgCasinoInfo(ServerCommandSource source) throws CommandSyntaxException {
        OrganizationState.Organization organization = requireOwnOrganization(source);
        sendOrgCasinoInfo(source, organization);
        return 1;
    }

    private static int listOrgCasinos(ServerCommandSource source) {
        KazikState state = KazikState.get(source.getServer());
        var casinos = state.orgCasinos();
        if (casinos.isEmpty()) {
            send(source, "No organization casinos are open.");
            return 1;
        }

        send(source, "Open organization casinos:");
        for (KazikState.OrgCasino casino : casinos) {
            String line = "- " + casino.organizationName();
            if (config.statsEnabled()) {
                line += ", games " + state.casinoStats(casino.organizationId()).stats().gamesPlayed();
            }
            if (config.publicBalance() || source.hasPermissionLevel(2)) {
                line += ", bankroll " + format(PowerRuble.api().getBalance(source.getServer(), casino.organizationId()));
            }
            send(source, line);
        }
        return 1;
    }

    private static int showOwnOrgCasinoBalance(ServerCommandSource source) throws CommandSyntaxException {
        OrganizationState.Organization organization = requireOwnOrganization(source);
        requireOpenOrgCasino(source, organization);
        send(source, organization.name() + " casino bankroll: " + format(PowerRuble.api().getBalance(source.getServer(), organization.id())));
        return 1;
    }

    private static int showOrgCasinoBalance(ServerCommandSource source, String organizationName) throws CommandSyntaxException {
        OrganizationState.Organization organization = resolveOpenOrgCasino(source, organizationName);
        requireCanViewOrgCasinoBalance(source, organization);
        send(source, organization.name() + " casino bankroll: " + format(PowerRuble.api().getBalance(source.getServer(), organization.id())));
        return 1;
    }

    private static int showOwnOrgCasinoStats(ServerCommandSource source) throws CommandSyntaxException {
        requireStatsEnabled();
        OrganizationState.Organization organization = requireOwnOrganization(source);
        requireOpenOrgCasino(source, organization);
        sendCasinoStats(source, organization.name(), KazikState.get(source.getServer()).casinoStats(organization.id()).stats());
        return 1;
    }

    private static int showOrgCasinoStats(ServerCommandSource source, String organizationName) throws CommandSyntaxException {
        requireStatsEnabled();
        OrganizationState.Organization organization = resolveOpenOrgCasino(source, organizationName);
        requireCanViewOrgCasinoStats(source, organization);
        sendCasinoStats(source, organization.name(), KazikState.get(source.getServer()).casinoStats(organization.id()).stats());
        return 1;
    }

    private static int showOwnOrgCasinoRecent(ServerCommandSource source) throws CommandSyntaxException {
        requireStatsEnabled();
        OrganizationState.Organization organization = requireOwnOrganization(source);
        requireOpenOrgCasino(source, organization);
        sendRecentEntries(
            source,
            "Recent casino games for " + organization.name() + ":",
            KazikState.get(source.getServer()).casinoRecentEntries(organization.id(), config.recentEntries())
        );
        return 1;
    }

    private static int showOrgCasinoRecent(ServerCommandSource source, String organizationName) throws CommandSyntaxException {
        requireStatsEnabled();
        OrganizationState.Organization organization = resolveOpenOrgCasino(source, organizationName);
        requireCanViewOrgCasinoStats(source, organization);
        sendRecentEntries(
            source,
            "Recent casino games for " + organization.name() + ":",
            KazikState.get(source.getServer()).casinoRecentEntries(organization.id(), config.recentEntries())
        );
        return 1;
    }

    private static OrganizationState.Organization requireOwnOrganization(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        return OrganizationState.get(source.getServer()).organizationFor(player.getUuid()).orElseThrow(ORGANIZATION_REQUIRED::create);
    }

    private static OrganizationState.Organization requireManagedOrganization(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        OrganizationState.Organization organization = requireOwnOrganization(source);
        OrganizationState.Member member = organization.members().get(player.getUuid());
        if (member == null || member.role() == OrgRole.MEMBER) {
            throw NO_PERMISSION.create();
        }

        return organization;
    }

    private static OrganizationState.Organization resolveOpenOrgCasino(ServerCommandSource source, String organizationName) throws CommandSyntaxException {
        OrganizationState.Organization organization = OrganizationState.get(source.getServer())
            .organizationByName(organizationName)
            .orElseThrow(ORG_CASINO_NOT_FOUND::create);
        requireOpenOrgCasino(source, organization);
        return organization;
    }

    private static void requireOpenOrgCasino(ServerCommandSource source, OrganizationState.Organization organization) throws CommandSyntaxException {
        KazikState state = KazikState.get(source.getServer());
        state.rememberCasinoName(organization.id(), organization.name());
        if (!state.isOrgCasinoEnabled(organization.id())) {
            throw ORG_CASINO_NOT_FOUND.create();
        }
    }

    private static void requireCanViewOrgCasinoBalance(ServerCommandSource source, OrganizationState.Organization organization) throws CommandSyntaxException {
        if (config.publicBalance() || source.hasPermissionLevel(2) || isOrgMember(source, organization)) {
            return;
        }

        throw NO_PERMISSION.create();
    }

    private static void requireCanViewOrgCasinoStats(ServerCommandSource source, OrganizationState.Organization organization) throws CommandSyntaxException {
        if (config.publicServerStats() || source.hasPermissionLevel(2) || isOrgMember(source, organization)) {
            return;
        }

        throw NO_PERMISSION.create();
    }

    private static boolean isOrgMember(ServerCommandSource source, OrganizationState.Organization organization) {
        ServerPlayerEntity player = source.getPlayer();
        return player != null && organization.members().containsKey(player.getUuid());
    }

    private static void sendOrgCasinoInfo(ServerCommandSource source, OrganizationState.Organization organization) {
        KazikState state = KazikState.get(source.getServer());
        state.rememberCasinoName(organization.id(), organization.name());
        boolean open = state.isOrgCasinoEnabled(organization.id());

        send(source, "Organization casino: " + organization.name());
        send(source, "Status: " + (open ? "open" : "closed"));
        send(source, "Bankroll: " + format(PowerRuble.api().getBalance(source.getServer(), organization.id())));
        if (config.statsEnabled()) {
            KazikState.GlobalStats stats = state.casinoStats(organization.id()).stats();
            send(source, "Games: " + stats.gamesPlayed() + ", house profit: " + format(stats.houseProfit()));
        }
    }

    private static void sendCasinoStats(ServerCommandSource source, String casinoName, KazikState.GlobalStats stats) {
        send(source, "Kazik casino stats for " + casinoName + ":");
        send(source, "Games: " + stats.gamesPlayed());
        send(source, "Players won: " + format(stats.totalWon()));
        send(source, "Players lost: " + format(stats.totalLost()));
        send(source, "Total bets: " + format(stats.totalBet()));
        send(source, "House profit: " + format(stats.houseProfit()));
        send(source, "Wins/Losses/Draws: " + stats.wins() + "/" + stats.losses() + "/" + stats.draws());
        send(source, "Biggest win/loss: " + format(stats.biggestWin()) + " / " + format(stats.biggestLoss()));
    }

    private static void validateBet(long bet) throws CommandSyntaxException {
        if (bet < config.minBetAmount()) {
            throw BET_TOO_SMALL.create();
        }
        if (bet > config.maxBetAmount()) {
            throw BET_TOO_LARGE.create();
        }
    }

    private static void checkCooldown(UUID playerId) throws CommandSyntaxException {
        int cooldownSeconds = config.betCooldownSeconds();
        if (cooldownSeconds <= 0) {
            return;
        }

        Instant lastGame = lastGames.get(playerId);
        if (lastGame == null) {
            return;
        }

        long elapsedSeconds = Duration.between(lastGame, Instant.now()).getSeconds();
        long remainingSeconds = cooldownSeconds - elapsedSeconds;
        if (remainingSeconds > 0L) {
            throw new SimpleCommandExceptionType(Text.literal("Wait " + remainingSeconds + " seconds before the next casino game.")).create();
        }
    }

    private static void checkPlayerCanRisk(MinecraftServer server, UUID playerId, long bet) throws CommandSyntaxException {
        long balance = PowerRuble.api().getBalance(server, playerId);
        long debtLimit = betDebtLimit();
        long balanceAfterLoss = balance - bet;
        if (balance < debtLimit || balanceAfterLoss > balance || balanceAfterLoss < debtLimit) {
            throw NOT_ENOUGH_MONEY.create();
        }
    }

    private static void applyResult(MinecraftServer server, ServerPlayerEntity player, CasinoAccount casino, KazikGameEngine.GameResult result) throws CommandSyntaxException {
        if (result.outcome() == KazikGameEngine.Outcome.WIN) {
            RubleState.TransferResult transferResult = PowerRuble.api().transfer(server, casino.id(), player.getUuid(), null, result.payout(), 0L, casinoDebtLimit(casino));
            if (transferResult != RubleState.TransferResult.OK) {
                throw HOUSE_NOT_ENOUGH_MONEY.create();
            }
            addTransferTransaction(server, casino.id(), casino.name(), player.getUuid(), playerName(player), result.payout(), reason(casino, result));
            return;
        }

        if (result.outcome() == KazikGameEngine.Outcome.LOSS) {
            RubleState.TransferResult transferResult = PowerRuble.api().transfer(server, player.getUuid(), casino.id(), null, result.lost(), 0L, betDebtLimit());
            if (transferResult == RubleState.TransferResult.NOT_ENOUGH_MONEY) {
                throw NOT_ENOUGH_MONEY.create();
            }
            if (transferResult != RubleState.TransferResult.OK) {
                throw new SimpleCommandExceptionType(Text.literal("Could not transfer casino bet.")).create();
            }
            addTransferTransaction(server, player.getUuid(), playerName(player), casino.id(), casino.name(), result.lost(), reason(casino, result));
        }
    }

    private static long betDebtLimit() {
        return config.allowDebt() ? PowerRuble.api().config().transferDebtLimit() : 0L;
    }

    private static long casinoDebtLimit(CasinoAccount casino) {
        return casino.finiteBankroll() ? 0L : Long.MIN_VALUE;
    }

    private static void addTransferTransaction(MinecraftServer server, UUID fromId, String fromName, UUID toId, String toName, long amount, String reason) {
        PowerRuble.api().addTransaction(
            server,
            RubleTransaction.transfer(Instant.now(), fromId, fromName, toId, toName, amount, 0L, Optional.empty(), "", reason)
        );
    }

    private static String reason(CasinoAccount casino, KazikGameEngine.GameResult result) {
        String prefix = casino.house() ? "kazik" : "kazik org " + casino.name();
        return prefix + " " + result.game() + " " + result.outcome().name().toLowerCase(Locale.ROOT) + ": " + result.detail();
    }

    private static int showOwnStats(ServerCommandSource source) throws CommandSyntaxException {
        requireStatsEnabled();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        KazikState state = KazikState.get(source.getServer());
        state.rememberPlayerName(player.getUuid(), playerName(player));
        sendPlayerStats(source, state.playerStats(player.getUuid()));
        return 1;
    }

    private static int showPlayerStats(ServerCommandSource source, GameProfile profile) throws CommandSyntaxException {
        requireStatsEnabled();
        if (!config.publicPlayerStats() && !source.hasPermissionLevel(2)) {
            throw NO_PERMISSION.create();
        }

        UUID playerId = profileId(profile);
        KazikState state = KazikState.get(source.getServer());
        state.rememberPlayerName(playerId, profile.getName());
        sendPlayerStats(source, state.playerStats(playerId));
        return 1;
    }

    private static int showServerStats(ServerCommandSource source) throws CommandSyntaxException {
        requireStatsEnabled();
        if (!config.publicServerStats() && !source.hasPermissionLevel(2)) {
            throw NO_PERMISSION.create();
        }

        KazikState.GlobalStats stats = KazikState.get(source.getServer()).globalStats();
        send(source, "Kazik server stats:");
        send(source, "Games: " + stats.gamesPlayed());
        send(source, "Players won: " + format(stats.totalWon()));
        send(source, "Players lost: " + format(stats.totalLost()));
        send(source, "Total bets: " + format(stats.totalBet()));
        send(source, "House profit: " + format(stats.houseProfit()));
        send(source, "Wins/Losses/Draws: " + stats.wins() + "/" + stats.losses() + "/" + stats.draws());
        send(source, "Biggest win/loss: " + format(stats.biggestWin()) + " / " + format(stats.biggestLoss()));
        return 1;
    }

    private static int showTop(ServerCommandSource source, TopMode mode) throws CommandSyntaxException {
        requireStatsEnabled();
        if (!config.publicTop() && !source.hasPermissionLevel(2)) {
            throw NO_PERMISSION.create();
        }

        KazikState state = KazikState.get(source.getServer());
        var entries = switch (mode) {
            case WINS -> state.topByWins(config.topSize());
            case LOSSES -> state.topByLosses(config.topSize());
            case PROFIT -> state.topByProfit(config.topSize());
        };
        if (entries.isEmpty()) {
            send(source, "No casino stats yet.");
            return 1;
        }

        send(source, "Kazik top " + mode.name().toLowerCase(Locale.ROOT) + ":");
        for (int index = 0; index < entries.size(); index++) {
            KazikState.PlayerStats stats = entries.get(index);
            String value = switch (mode) {
                case WINS -> format(stats.totalWon());
                case LOSSES -> format(stats.totalLost());
                case PROFIT -> format(stats.netProfit());
            };
            send(source, (index + 1) + ". " + stats.playerName() + " - " + value);
        }
        return 1;
    }

    private static int showOwnRecent(ServerCommandSource source) throws CommandSyntaxException {
        requireStatsEnabled();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        KazikState state = KazikState.get(source.getServer());
        state.rememberPlayerName(player.getUuid(), playerName(player));
        sendRecentEntries(source, "Recent casino games for " + playerName(player) + ":", state.recentEntries(player.getUuid(), config.perPlayerHistoryEntries()));
        return 1;
    }

    private static int showPlayerRecent(ServerCommandSource source, GameProfile profile) throws CommandSyntaxException {
        requireStatsEnabled();
        if (!config.publicPlayerStats() && !source.hasPermissionLevel(2)) {
            throw NO_PERMISSION.create();
        }

        UUID playerId = profileId(profile);
        KazikState state = KazikState.get(source.getServer());
        state.rememberPlayerName(playerId, profile.getName());
        sendRecentEntries(source, "Recent casino games for " + profile.getName() + ":", state.recentEntries(playerId, config.perPlayerHistoryEntries()));
        return 1;
    }

    private static int showRecent(ServerCommandSource source) throws CommandSyntaxException {
        requireStatsEnabled();
        if (!config.publicServerStats() && !source.hasPermissionLevel(2)) {
            throw NO_PERMISSION.create();
        }

        var entries = KazikState.get(source.getServer()).recentEntries(config.recentEntries());
        if (entries.isEmpty()) {
            send(source, "No recent casino games.");
            return 1;
        }

        sendRecentEntries(source, "Recent casino games:", entries);
        return 1;
    }

    private static void sendRecentEntries(ServerCommandSource source, String title, java.util.List<KazikState.GameEntry> entries) {
        if (entries.isEmpty()) {
            send(source, "No recent casino games.");
            return;
        }

        send(source, title);
        for (KazikState.GameEntry entry : entries) {
            long amount = entry.outcome() == KazikGameEngine.Outcome.WIN ? entry.payout() : entry.lost();
            send(source, entry.time() + " " + entry.playerName() + " " + entry.game() + " "
                + entry.outcome().name().toLowerCase(Locale.ROOT) + " " + format(amount) + " (" + entry.detail() + ")");
        }
    }

    private static void sendPlayerStats(ServerCommandSource source, KazikState.PlayerStats stats) {
        send(source, "Kazik stats for " + stats.playerName() + ":");
        send(source, "Games: " + stats.gamesPlayed());
        send(source, "Won: " + format(stats.totalWon()));
        send(source, "Lost: " + format(stats.totalLost()));
        send(source, "Total bets: " + format(stats.totalBet()));
        send(source, "Net: " + format(stats.netProfit()));
        send(source, "Wins/Losses/Draws: " + stats.wins() + "/" + stats.losses() + "/" + stats.draws());
        send(source, "Biggest win/loss: " + format(stats.biggestWin()) + " / " + format(stats.biggestLoss()));
    }

    private static int showHouseBalance(ServerCommandSource source) throws CommandSyntaxException {
        if (!config.publicBalance() && !source.hasPermissionLevel(2)) {
            throw NO_PERMISSION.create();
        }

        rememberHouse(source.getServer());
        send(source, "Kazik house balance: " + format(PowerRuble.api().getBalance(source.getServer(), HOUSE_ID)));
        return 1;
    }

    private static int giveHouse(ServerCommandSource source, long amount) {
        rememberHouse(source.getServer());
        if (!PowerRuble.api().add(source.getServer(), HOUSE_ID, amount)) {
            source.sendError(Text.literal("Casino balance is too large."));
            return 0;
        }

        PowerRuble.api().addTransaction(source.getServer(), RubleTransaction.admin(
            RubleTransaction.Type.ADMIN_GIVE,
            Instant.now(),
            HOUSE_ID,
            config.houseAccountName(),
            amount,
            "kazik house give"
        ));
        send(source, "Added " + format(amount) + " to Kazik house. Balance: " + format(PowerRuble.api().getBalance(source.getServer(), HOUSE_ID)));
        return 1;
    }

    private static int takeHouse(ServerCommandSource source, long amount) {
        rememberHouse(source.getServer());
        if (!PowerRuble.api().subtract(source.getServer(), HOUSE_ID, amount)) {
            source.sendError(Text.literal("Kazik house does not have enough rubles."));
            return 0;
        }

        PowerRuble.api().addTransaction(source.getServer(), RubleTransaction.admin(
            RubleTransaction.Type.ADMIN_TAKE,
            Instant.now(),
            HOUSE_ID,
            config.houseAccountName(),
            amount,
            "kazik house take"
        ));
        send(source, "Removed " + format(amount) + " from Kazik house. Balance: " + format(PowerRuble.api().getBalance(source.getServer(), HOUSE_ID)));
        return 1;
    }

    private static int setHouse(ServerCommandSource source, long amount) {
        rememberHouse(source.getServer());
        PowerRuble.api().setBalance(source.getServer(), HOUSE_ID, amount);
        PowerRuble.api().addTransaction(source.getServer(), RubleTransaction.admin(
            RubleTransaction.Type.ADMIN_SET,
            Instant.now(),
            HOUSE_ID,
            config.houseAccountName(),
            amount,
            "kazik house set"
        ));
        send(source, "Kazik house balance set to " + format(amount) + ".");
        return 1;
    }

    private static int reloadConfig(ServerCommandSource source) {
        config = KazikConfig.load();
        lastGames.clear();
        send(source, "Power Ruble Kazik config reloaded.");
        return 1;
    }

    private static void requireStatsEnabled() throws CommandSyntaxException {
        if (!config.statsEnabled()) {
            throw STATS_DISABLED.create();
        }
    }

    private static void rememberHouse(MinecraftServer server) {
        rememberCasino(server, houseAccount());
    }

    private static void rememberCasino(MinecraftServer server, CasinoAccount casino) {
        PowerRuble.api().rememberAccountName(server, casino.id(), casino.name());
    }

    private static CasinoAccount houseAccount() {
        return new CasinoAccount(HOUSE_ID, config.houseAccountName(), config.finiteBankroll(), true);
    }

    private static CasinoAccount orgAccount(OrganizationState.Organization organization) {
        return new CasinoAccount(organization.id(), organization.name(), true, false);
    }

    private static void send(ServerCommandSource source, String message) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
            return;
        }
        source.sendFeedback(() -> Text.literal(message), false);
    }

    private static String format(long amount) {
        return amount + " " + PowerRuble.api().config().currencyName();
    }

    private static String playerName(ServerPlayerEntity player) {
        return player.getName().getString();
    }

    private static GameProfile singleProfile(Collection<GameProfile> profiles) throws CommandSyntaxException {
        if (profiles.isEmpty()) {
            throw new SimpleCommandExceptionType(Text.literal("Player not found.")).create();
        }
        if (profiles.size() != 1) {
            throw new SimpleCommandExceptionType(Text.literal("Specify exactly one player.")).create();
        }
        return profiles.iterator().next();
    }

    private static UUID profileId(GameProfile profile) {
        if (profile.getId() != null) {
            return profile.getId();
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + profile.getName()).getBytes(StandardCharsets.UTF_8));
    }

    static Logger logger() {
        return LOGGER;
    }

    private enum TopMode {
        WINS,
        LOSSES,
        PROFIT
    }

    private record CasinoAccount(UUID id, String name, boolean finiteBankroll, boolean house) {
    }

    @FunctionalInterface
    private interface GameSupplier {
        KazikGameEngine.GameResult get();
    }
}
