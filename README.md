# Roam

Random teleport for Paper 1.21.11 with settings that are easy to read and hard to get wrong.

Roam started as a fork of [BetterRTP](https://github.com/RonanPlugins/BetterRTP) (MIT) and was rewritten
down to what a normal survival server needs. It also takes ideas from
[JakesRTP](https://github.com/donvi-bz/JakesRTP): circle and square areas, a gaussian spread, caves for
the nether, a location cache, `rtp-on-death` and sending players to a random place from portals or
commands. No JakesRTP code is used, it has no license.

## What it does

- `/roamrtp` (or `/roam`) teleports you somewhere safe in the world you are in, `/roamrtp <world>` in another one. Roam only has these two commands, so it does not collide with `/rtp` or `/wild` from a DeluxeMenus menu or another plugin. Use `[player] roamrtp <world>` in menu items.
- Square or circle areas with a minimum and maximum radius, centred on the spawn, on the player, or on x and z.
- Even spread, or a gaussian spread that makes a chosen distance more likely.
- Safe spots are found in the background and kept ready, so `/roamrtp` is instant. Chunks are loaded without lag.
- Surface search for normal worlds, cave search for the nether.
- Per world, per group and default settings for radius, height, cooldown, delay, cost, biomes and more.
- **Free fall**: teleport players a number of blocks above the safe block. They fall without taking damage.
- Cooldowns that survive restarts, a delay that cancels when you move or get hurt, a cost through Vault.
- Short invulnerability after landing, a sound and a title.
- Never lands inside a WorldGuard region, or a GriefPrevention, HuskClaims or HuskTowns claim.
- Random teleport for first joins and respawns, commands to run afterwards, PlaceholderAPI values.
- `/roamrtp info` shows exactly what the settings work out to and tries a search.

## Speed

- Chunks are loaded on Paper's worker threads, never by freezing the main thread.
- Ready spots are found in the background. When a player teleports, the spot's chunk is loaded from disk (it was generated already) and checked again, so `/roamrtp` takes a fraction of a second.
- Measured on a real 1.21.11 server with a real client: a teleport to a ready spot took about 100-200 ms, and the player's ping stayed at 1-3 ms during every teleport. When the destination has never been visited, the server still has to generate the chunks around the player, which is the same for every plugin.

## Commands and permissions

| Command | What it does | Permission |
| --- | --- | --- |
| `/roamrtp` | Teleport in your world | `roam.use` (default: everyone) |
| `/roamrtp <world>` | Teleport to another world | `roam.use`, plus `roam.world.<world>` only if `per-world-permission` is on |
| `/roamrtp player <name> [world]` | Send a player, with no cooldown, cost or delay. Good for portals and command blocks | `roam.admin` |
| `/roamrtp info [world]` | Show the settings and try a search | `roam.admin` |
| `/roamrtp reset <name>` | Clear a cooldown | `roam.admin` |
| `/roamrtp reload` | Reload config.yml and messages.yml | `roam.admin` |

Other permissions: `roam.bypass.cooldown`, `roam.bypass.delay`, `roam.bypass.cost` and `roam.group.<name>`
for the groups in config.yml.

That is all. There is one permission check for worlds and it is off by default.

## Settings

Everything is in `config.yml`. `defaults` apply everywhere, `worlds.<name>` changes them for one world and
`groups.<name>` changes them for players with `roam.group.<name>`. Only write what you change:

```yaml
defaults:
  radius: { min: 100, max: 3000 }
  cooldown: 60
  delay: 3

worlds:
  world_nether:
    search: cave
    radius: { min: 50, max: 1500 }
  spawn:
    redirect: world        # /roamrtp in the spawn world sends players to "world"
  creative:
    enabled: false

groups:
  vip:                     # permission roam.group.vip
    cooldown: 10
    delay: 0
```

Numbers can be written any way (`50`, `50.0`, `"50"`). Mistakes such as a radius that is too small, or a world
name that is not loaded, are reported in the console when the file loads.

Free fall, for example 30 blocks above the ground: `free-fall: 30`.

## PlaceholderAPI

`%roam_cooldown%` (seconds left), `%roam_cooldown_formatted%`, `%roam_ready%` and `%roam_cost%`.

## For developers

`RoamPreTeleportEvent` (cancel it or change the destination) and `RoamTeleportEvent`.

## Building

```
./gradlew build
```

The jar is in `build/libs`.

## License

MIT, see `LICENSE`.
