# Power Ruble Kazik

Optional casino addon for [Power Ruble](https://github.com/PowerTronin/power-ruble).

This server-side Fabric 1.20.1 mod adds casino games that use the base Power Ruble balance API.

## Install

Put both jars into the server `mods` folder:

- `power-ruble-1.1.0.jar`
- `power-ruble-kazik-1.1.0.jar`

## Commands

- `/kazik help` - show commands.
- `/kazik coin <bet> heads|tails` - coin flip.
- `/kazik dice <bet> under|over <target>` - roll 1-100 and bet on the result.
- `/kazik slots <bet>` - simple three-reel slots.
- `/kazik stats` - show your casino statistics.
- `/kazik stats server` - show server casino totals.
- `/kazik stats player <player>` - show another player's stats, operator-only by default.
- `/kazik top wins|losses|profit` - show leaderboards.
- `/kazik recent` - show recent games.
- `/kazik recent me` - show your recent games.
- `/kazik recent player <player>` - show another player's recent games, operator-only by default.
- `/kazik reload` - reload config, operator-only.
- `/kazik house balance` - show the casino account balance, public by default.
- `/kazik house give|take|set` - manage the casino account, operator-only.

## Accounting

Winning games pay net profit from the casino account to the player. Losing games transfer the bet from the player to the casino account.

Statistics store total player winnings, losses, bet volume, net result, and recent game history. Casino profit is `player losses - player winnings`.

## Config

The addon creates `config/power-ruble-kazik.json5`:

```json5
{
  house: {
    accountName: "kazik",
    finiteBankroll: true,
    publicBalance: true,
  },
  bets: {
    minAmount: 1,
    maxAmount: 10000,
    cooldownSeconds: 3,
    allowDebt: false,
  },
  games: {
    coin: {
      enabled: true,
      payoutMultiplier: 0.95,
    },
    dice: {
      enabled: true,
      minTarget: 2,
      maxTarget: 98,
      houseEdgePercent: 3.0,
    },
    slots: {
      enabled: true,
      maxPayoutMultiplier: 25.0,
    },
  },
  stats: {
    enabled: true,
    publicPlayerStats: false,
    publicServerStats: true,
    publicTop: true,
    topSize: 10,
    recentEntries: 50,
    perPlayerHistoryEntries: 20,
  },
}
```

After editing config, restart the server or run `/kazik reload`.

## Development note

This repository downloads the matching base development jar from the `power-ruble` release assets on demand. The version is controlled by `base_mod_version` in `gradle.properties`.

## License

Power Ruble Kazik is licensed under GPL-3.0-only. See `LICENSE`.

## Build

```sh
./gradlew build
```

Jar output:

```text
build/libs/power-ruble-kazik-1.1.0.jar
```
