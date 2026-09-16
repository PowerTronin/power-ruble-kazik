# Power Ruble Kazik

Optional casino addon for [Power Ruble](https://github.com/PowerTronin/power-ruble).

This server-side Fabric 1.20.1 mod adds casino games that use the base Power Ruble balance API.

## Install

Put these jars into the server `mods` folder:

- `power-ruble-1.1.0.jar`
- `power-ruble-orgcontracts-1.1.0.jar`
- `power-ruble-kazik-1.2.0.jar`

## Commands

- `/kazik help` - show commands.
- `/kazik coin <bet> heads|tails` - coin flip.
- `/kazik dice <bet> under|over <target>` - roll 1-100 and bet on the result.
- `/kazik slots <bet>` - simple three-reel slots.
- `/kazik org create` - open a casino for your organization, owner/manager only.
- `/kazik org close` - close your organization's casino, owner/manager only.
- `/kazik org info` - show your organization's casino status.
- `/kazik org list` - list open organization casinos.
- `/kazik org coin <organization> <bet> heads|tails` - play coin against an organization casino.
- `/kazik org dice <organization> <bet> under|over <target>` - play dice against an organization casino.
- `/kazik org slots <organization> <bet>` - play slots against an organization casino.
- `/kazik org balance [organization]` - show an organization casino bankroll.
- `/kazik org stats [organization]` - show organization casino statistics.
- `/kazik org recent [organization]` - show recent organization casino games.
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

Organization casinos use the Power Ruble Org Contracts organization account as the casino bankroll. Owners and managers can open or close their organization's casino. Organization casino payouts always require enough organization balance, even if the main house casino is configured with infinite bankroll.

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

This repository downloads the matching base development jar from the `power-ruble` release assets and the matching `power-ruble-orgcontracts` jar on demand. The base/addon compatibility version is controlled by `base_mod_version` in `gradle.properties`.

## License

Power Ruble Kazik is licensed under GPL-3.0-only. See `LICENSE`.

## Build

```sh
./gradlew build
```

Jar output:

```text
build/libs/power-ruble-kazik-1.2.0.jar
```
