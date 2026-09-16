package ru.powerruble.kazik;

import java.util.List;
import java.util.Locale;
import java.util.Random;

final class KazikGameEngine {
    private static final List<String> SLOT_SYMBOLS = List.of("cherry", "lemon", "bell", "diamond", "seven");

    private KazikGameEngine() {
    }

    static GameResult coin(Random random, long bet, String guess, double payoutMultiplier) {
        boolean guessedHeads = "heads".equalsIgnoreCase(guess);
        boolean landedHeads = random.nextBoolean();
        boolean win = guessedHeads == landedHeads;
        String landed = landedHeads ? "heads" : "tails";
        if (!win) {
            return GameResult.loss("coin", bet, "landed " + landed);
        }

        return GameResult.win("coin", bet, payout(bet, payoutMultiplier), "landed " + landed);
    }

    static GameResult dice(Random random, long bet, DiceDirection direction, int target, double houseEdgePercent) {
        int roll = random.nextInt(100) + 1;
        boolean win = direction == DiceDirection.UNDER ? roll < target : roll > target;
        if (!win) {
            return GameResult.loss("dice", bet, "rolled " + roll + ", target " + direction.name().toLowerCase(Locale.ROOT) + " " + target);
        }

        double multiplier = dicePayoutMultiplier(direction, target, houseEdgePercent);
        return GameResult.win(
            "dice",
            bet,
            payout(bet, multiplier),
            "rolled " + roll + ", target " + direction.name().toLowerCase(Locale.ROOT) + " " + target
        );
    }

    static GameResult slots(Random random, long bet, double maxPayoutMultiplier) {
        String first = SLOT_SYMBOLS.get(random.nextInt(SLOT_SYMBOLS.size()));
        String second = SLOT_SYMBOLS.get(random.nextInt(SLOT_SYMBOLS.size()));
        String third = SLOT_SYMBOLS.get(random.nextInt(SLOT_SYMBOLS.size()));
        double multiplier = slotsMultiplier(first, second, third, maxPayoutMultiplier);
        String detail = first + " | " + second + " | " + third;
        if (multiplier <= 0.0D) {
            return GameResult.loss("slots", bet, detail);
        }

        return GameResult.win("slots", bet, payout(bet, multiplier), detail);
    }

    static double dicePayoutMultiplier(DiceDirection direction, int target, double houseEdgePercent) {
        int winningOutcomes = direction == DiceDirection.UNDER ? target - 1 : 100 - target;
        if (winningOutcomes <= 0) {
            return 0.0D;
        }

        double grossMultiplier = 100.0D / winningOutcomes * (1.0D - houseEdgePercent / 100.0D);
        return Math.max(0.0D, grossMultiplier - 1.0D);
    }

    static long payout(long bet, double multiplier) {
        if (bet <= 0L || multiplier <= 0.0D) {
            return 0L;
        }

        double value = Math.floor(bet * multiplier);
        if (value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }

        return Math.max(0L, (long) value);
    }

    static double slotsMultiplier(String first, String second, String third, double maxPayoutMultiplier) {
        if (first.equals(second) && second.equals(third)) {
            return Math.min(maxPayoutMultiplier, tripleMultiplier(first));
        }

        if (first.equals(second) || first.equals(third) || second.equals(third)) {
            return Math.min(maxPayoutMultiplier, 1.0D);
        }

        return 0.0D;
    }

    private static double tripleMultiplier(String symbol) {
        return switch (symbol) {
            case "seven" -> 25.0D;
            case "diamond" -> 10.0D;
            case "bell" -> 5.0D;
            default -> 3.0D;
        };
    }

    enum DiceDirection {
        UNDER,
        OVER
    }

    enum Outcome {
        WIN,
        LOSS,
        DRAW
    }

    record GameResult(Outcome outcome, String game, long bet, long payout, long lost, String detail) {
        static GameResult win(String game, long bet, long payout, String detail) {
            return new GameResult(Outcome.WIN, game, bet, payout, 0L, detail);
        }

        static GameResult loss(String game, long bet, String detail) {
            return new GameResult(Outcome.LOSS, game, bet, 0L, bet, detail);
        }

        static GameResult draw(String game, long bet, String detail) {
            return new GameResult(Outcome.DRAW, game, bet, 0L, 0L, detail);
        }
    }
}
