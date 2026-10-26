# ALOO Piñata Party 2.0

Automatic, server-wide Piñata boss event for **ALOO SMP** (Paper 1.21 – 1.21.10, Java 21 / Java 25).

## 1. Get your .jar (easy way: GitHub Codespaces)

1. Create a new GitHub repository, then **Add file → Upload files** and upload the *contents* of this folder
   (the zip, unzipped; keep the folder structure, including `.github` and `.devcontainer`).
2. **Option A – GitHub Actions (zero setup):** the push triggers *Actions → Build plugin jar*. When it is green,
   open the run and download the artifacts `AlooPinataParty-java21` and `AlooPinataParty-java25`.
3. **Option B – Codespaces:** *Code → Codespaces → Create codespace on main*, then in the terminal:
   ```bash
   ./build.sh            # target/AlooPinataParty-2.0.0.jar        (runs on Java 21 AND Java 25 servers)
   ./build.sh java25     # target/AlooPinataParty-2.0.0-java25.jar (only if the Codespace has JDK 25)
   ```
   Right-click the jar in `target/` → **Download**.
4. Put the jar in your server's `plugins/` folder and restart.

If the build prints compile errors, copy them to Claude and they will be fixed (see "Honest status" below).

### Which jar do I need?
| Your server Java | Use |
|---|---|
| 21 | `AlooPinataParty-2.0.0.jar` |
| 25 | `AlooPinataParty-2.0.0.jar` (works) or `...-java25.jar` |

The normal jar contains Java 21 bytecode, which runs unchanged on Java 25, so **one jar covers both**.
The `-java25` build exists only for people who want bytecode targeting 25.

### Minecraft versions
Compiled against Paper API 1.21.1 using only API calls that behave the same on 1.21.x and 1.21.10 (registries instead of
renamed enum constants, string sound ids, Adventure for text/bossbars). Override with
`mvn package -Dpaper.api.version=1.21.10-R0.1-SNAPSHOT` if you want to compile against 1.21.10 explicitly.
Needs **Paper** (or a fork: Purpur, Pufferfish…). Plain Spigot is not supported (Adventure/Paper API required).

## 2. Arena setup (5 steps)
1. `/pinata wand` – you get the Arena Wand (a stick with hidden NBT; the name alone does not count).
2. **Right-click** a block = Position 1, **left-click** a block = Position 2.
3. `/pinata setarena` – saves the region (overwrite later with `/pinata setarena confirm`).
4. Stand where the Piñata should appear, look the way you want: `/pinata setspawn`
   (must be inside the arena; overwrite with `confirm`).
5. `/pinata arena` to review, `/pinata forcestart ZOMBIE` to test.

## 3. Commands & permissions
| Command | Permission | What it does |
|---|---|---|
| `/pinata help` | – | help |
| `/pinata join` | `aloopinata.participate` (default: everyone) | delayed teleport to the event (the clickable chat button runs this) |
| `/pinata status` | `aloopinata.status` (everyone) | state, mob, HP, time left, next event |
| `/pinata schedule` | `aloopinata.status` | show schedule |
| `/pinata schedule interval <hours>` | `aloopinata.admin` | change interval (writes config.yml) |
| `/pinata schedule next <minutes>` / `enable` / `disable` / `reset` | `aloopinata.admin` | adjust schedule |
| `/pinata wand`, `setarena`, `setspawn`, `arena` | `aloopinata.wand` | arena tools |
| `/pinata start [seconds]` | `aloopinata.start` | countdown then event (0 = now) |
| `/pinata forcestart <mob>` | `aloopinata.start` | start immediately with that mob |
| `/pinata stop` | `aloopinata.stop` | cancel countdown / running event |
| `/pinata reload` | `aloopinata.reload` | reload all 5 files (no duplicate tasks/listeners/bossbars) |
| `/pinata sethealth <mob> <hp>` | `aloopinata.admin` | edit mobs.yml health (1–1024) |
| `/pinata testrewards [player] [rank] [execute]` | `aloopinata.admin` | preview, or really run, reward commands |

`aloopinata.admin` (op) includes everything. `aloopinata.teleport.bypass` skips the join delay.
Aliases: `/pinataparty`, `/aloopinata`.

## 4. Configuration files (all created on first start, never overwritten)
`config.yml` schedule, bossbars, teleport, effects, protection · `arena.yml` (managed by commands) · `mobs.yml` five mobs ·
`messages.yml` every message · `rewards.yml`. Every file is commented. Keys missing from an old file fall back to built-in
defaults. A broken YAML file is copied to `<name>.broken` and defaults are used (nothing crashes).

### Reward placeholders
`%player%` `%uuid%` `%damage%` `%rank%` `%mob%` `%event_id%` `%multiplier%`, plus `%mult:<n>%` = n × the mob's
`reward-multiplier` (e.g. `eco give %player% %mult:100%`). Commands run from the console, 10 per tick by default.
Defaults only send a chat message, so they work without any economy plugin; swap in your own commands.

## 5. How the important parts work
* **State machine** `IDLE → COUNTDOWN → ACTIVE → COMPLETING → IDLE`, driven by one 1-second ticker that only compares
  timestamps and checks one entity. No world scans, no per-tick tasks, no disk write per hit.
* **Schedule** persisted in `data.yml`. If a slot is missed while offline: `RESCHEDULE` (default, one interval from now) or
  `CATCH_UP` (exactly one event after a delay) – never an event storm.
* **Piñata identity**: stored UUID *and* PersistentDataContainer tag; non-persistent, never despawns, can't burn/transform,
  is pulled back to spawn if it leaves the arena or falls into the void, only direct player hits/projectiles can damage it.
  Llama/others have the AI "panic" goal removed through Paper's public MobGoals API so they stand and fight.
  Warden damage to players is scaled (`player-damage-multiplier`) and the sonic boom can be disabled.
* **Damage credit** = real health reduction (final damage after armor/absorption, capped at remaining HP), by UUID, only
  from players or their projectiles, creative/spectator/NPC players excluded (configurable).
* **PvP protection** (only while the event runs): melee, sweep, arrows, tridents, other projectiles, pets, primed TNT,
  clouds, splash potions and flame are blocked when the victim is in the arena (or the attacker is, with `block-outgoing`).
  PvP elsewhere and in other worlds is untouched. Not covered: explosions not caused directly by a player (end crystals,
  beds), fishing-rod pulls.
* **Rewards** are idempotent per event id and spread over ticks.

## 6. Honest status – what was and was not tested
The build environment used to write this code **could not reach Maven Central / the PaperMC repository**, so the plugin
could **not be compiled or run against the real Paper API there**. That means:

**Done and verified here**
* Every Java file passes the Java 21 parser (no syntax errors); cross-class method calls in my own code resolve.
* All 5 YAML files and `plugin.yml` parse; every message key used in code exists in `messages.yml`; every config key read in
  `Settings` exists in `config.yml`.
* Pure logic ran and passed: time formatting, damage ranking and tie-break, rejection of NaN/negative damage, reward
  placeholder/multiplier expansion, name sanitising.

**NOT done (you must do this)**
* `mvn package` against real Paper: if any Paper API call differs from my expectation there will be a compile error – send
  it to me and I'll fix it. The unit tests (`LogicTest`) run during the build; a failing test does **not** stop the jar.
* No live-server test of: first start, wand/arena persistence, scheduling & restart recovery, bossbars, clickable teleport
  and cancellation, each of the five mobs (llama AI, warden, hoglin conversion), buffs/debuffs, multi-player damage,
  rewards, PvP protection across the boundary (direct + projectile), timeout, shutdown cleanup, reload, 1.21 vs 1.21.10.
* No profiling was done; no benchmark numbers are claimed.

**Suggested first test run**: `/pinata wand` → set arena + spawn → `/pinata forcestart WARDEN` → hit it with two accounts
→ check bossbar, leaderboard, rewards chat messages → `/pinata start 20` and click the chat button → `/pinata reload` →
restart the server mid-event and confirm no mob/bossbar is left behind.
