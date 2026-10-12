# AGENTS.md

## Project
- Minecraft Forge mod for **MC 1.20.1 / Forge 47.2.0 / Java 17**. Mod id `elsilente` ("El Silente de las Sombras"), base package `com.elsilente`. Windows + PowerShell environment.
- Version and metadata live in `gradle.properties` (source of truth). `META-INF/mods.toml` and `pack.mcmeta` use `${...}` placeholders expanded by `processResources` — never hardcode values there; edit `gradle.properties`.
- Single Gradle project, no CI, no unit tests. `README*`, `RITUAL.md`, code comments and logs are **Spanish**.

## Commands (run from repo root)
- Build: `.\gradlew build` → `build/libs/elsilente-<version>.jar` (e.g. `elsilente-1.0.0.jar`).
- Dev client / server: `.\gradlew runClient` / `.\gradlew runServer`.
- Data generation: `.\gradlew runData` (outputs to `src/generated/resources`, working dir `run-data`).
- `.\gradlew gameTestServer` runs Forge gametests and **crashes when none are registered** (none are).
- IDE setup: `.\gradlew genEclipseRuns` / `genIntellijRuns`.
- Gradle daemon is disabled (`org.gradle.daemon=false`), so builds are slower; allow generous timeouts.

## Generated assets (not edited by hand)
- Textures (PNG) and sounds (OGG) are produced by `python scripts/generate_assets.py` (deps: `numpy`, `pillow`, `soundfile`; optional `imageio-ffmpeg`). `--textures-only` skips audio.
- Entity atlas UVs in the script **must match** `client/ElSilenteModel.java`; if you change the model's UV layout, update the atlas coords in the script too (see `EYE_CORES`).
- Outputs are committed under `src/main/resources/assets/elsilente/`.

## Layout
- `ElSilenteMod.java` — entrypoint; registers DeferredRegisters and attribute/spawn callbacks.
- `registry/` — `DeferredRegister` for items, entities, sounds, creative tabs.
- `entity/ElSilenteEntity.java` — core AI; two phases toggled by the synced `Aggro` flag (STALK vs CHASE). Also holds `PENDING_SPAWNS`.
- `event/` — Forge handlers: `AmbushHandler` (server tick: ambushes + pending respawns), `RitualHandler` (`ProjectileImpactEvent`), `TheftHandler` (`LivingDeathEvent`), `AmuletHandler`, `SpawnAudit` (startup spawn log).
- `client/` — client-only renderer/model, registered via `@Mod.EventBusSubscriber(..., value = Dist.CLIENT)`.
- `util/Spawns.java` — shared spawn-position helpers.

## Conventions / gotchas
- Keep comments, log strings, docs and tooltips in Spanish. Keep `assets/elsilente/lang/es_es.json` and `en_us.json` in sync.
- Prefer unicode escapes (e.g. `\u00c9l`) over raw accented literals in `.java` to avoid encoding issues.
- `build/`, `run/`, `.gradle/` are gitignored dev/output and must not be edited; `run/` holds a dev world save and logs used for debugging.
- `changelog.txt` is the upstream Forge changelog, not this mod's history.
