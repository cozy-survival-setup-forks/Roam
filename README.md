# Roam

Random teleport for Paper 1.21.11 with settings that are easy to read and hard to get wrong.

Roam started as a fork of [BetterRTP](https://github.com/RonanPlugins/BetterRTP) (MIT) and was rewritten
down to what a normal survival server needs. It also takes ideas from
[JakesRTP](https://github.com/donvi-bz/JakesRTP): circle and square areas, a gaussian spread, caves for
the nether, a location cache, `rtp-on-death` and sending players to a random place from portals or
commands. No JakesRTP code is used, it has no license.

## What it does

- `/rtp` (or `/wild`) teleports you somewhere safe in the world you are in, `/rtp <world>` in another one.
- Square or circle areas with a minimum and maximum radius, centred on the spawn, on the player, or on x and z.
- Even spread, or a gaussian spread that makes a chosen distance more likely.
- Safe spots are found in the background and kept ready, so `/rtp` is instant. Chunks are loaded without lag.
- Surface search for normal worlds, cave search for the nether.
- Per world, per group and default settings for radius, height, cooldown, delay, cost, biomes and more.
- **Free fall**: teleport players a number of blocks above the safe block. They fall without taking damage.
- Cooldowns that survive restarts, a delay that cancels when you move or get hurt, a cost through Vault.
- Short invulnerability after landing, a sound and a title.
- Never lands inside a WorldGuard region, or a GriefPrevention, HuskClaims or HuskTowns claim.
- Random teleport for first joins and respawns, commands to run afterwards, PlaceholderAPI values.
- `/rtp info` shows exactly what the settings work out to and tries a search.

## Commands and permissions

| Command | What it does | Permission |
| --- | --- | --- |
| `/rtp` | Teleport in your world | `roam.use` (default: everyone) |
| `/rtp <world>` | Teleport to another world | `roam.use`, plus `roam.world.<world>` only if `per-world-permission` is on |
| `/rtp player <name> [world]` | Send a player, with no cooldown, cost or delay. Good for portals and command blocks | `roam.admin` |
| `/rtp info [world]` | Show the settings and try a search | `roam.admin` |
| `/rtp reset <name>` | Clear a cooldown | `roam.admin` |
| `/rtp reload` | Reload config.yml and messages.yml | `roam.admin` |

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
    redirect: world        # /rtp in the spawn world sends players to "world"
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
