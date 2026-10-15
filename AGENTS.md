# AGENTS.md

## Project
- Minecraft Forge mod for **MC 1.20.1 / Forge 47.2.0 / Java 17**. Mod id `elsilente` ("El Silente de las Sombras"), base package `com.elsilente`. Windows + PowerShell environment.
- Version and metadata live in `gradle.properties` (source of truth). `META-INF/mods.toml` and `pack.mcmeta` use `${...}` placeholders expanded by `processResources` — never hardcode values there; edit `gradle.properties`.
- Single Gradle project, no CI, no unit tests. Code comments and logs are **Spanish**; repo-root `.md` docs are Spanish too.
- Docs (keep in sync with code): `GUIA.md` (player guide: historia/progresión/items — the one players read), `README.md` (technical summary), `RITUAL.md` (ritual walkthrough), `MEJORAS.md` (improvement rounds, all done), `DISENO.md` + `El silencio de las sombras.md` (historical design / original pitch).
- Extra dependency: **GeckoLib 4.4.x** (`geckolib-forge-1.20.1`, cloudsmith maven `geckolib3/geckolib`, declared with `fg.deobf` in build.gradle and mandatory in `mods.toml`). El Silente uses `GeoEntityRenderer` + JSON geo model/animations.

## Commands (run from repo root)
- **JDK 17 is required for Gradle**: the machine's default `java` is 26 and breaks Gradle 8.1.1 ("Unsupported class file major version 70"). Prefix every `gradlew` call with `export JAVA_HOME="C:/Users/Usuario/.gradle/jdks/eclipse_adoptium-17-amd64-windows/jdk-17.0.20.1+1"` (Git Bash). This JDK 17 fue auto-provisionado por Gradle; no hay otro JDK 17 en la máquina.
- Build: `.\gradlew build` → `build/libs/elsilente-<version>.jar` (e.g. `elsilente-1.0.0.jar`).
- Dev client / server: `.\gradlew runClient` / `.\gradlew runServer`.
- Data generation: `.\gradlew runData` (outputs to `src/generated/resources`, working dir `run-data`).
- `.\gradlew gameTestServer` runs Forge gametests and **crashes when none are registered** (none are).
- IDE setup: `.\gradlew genEclipseRuns` / `genIntellijRuns`.
- Gradle daemon is disabled (`org.gradle.daemon=false`), so builds are slower; allow generous timeouts.

## Generated assets (not edited by hand)
- Textures (PNG) and sounds (OGG) are produced by `python scripts/generate_assets.py` (deps: `numpy`, `pillow`, `soundfile`; optional `imageio-ffmpeg`). `--textures-only` skips audio.
- **Item textures are 64x64**: the 6 item generators (`make_corazon`, `make_amuleto`, `make_simbolo_alba`, `make_guadana`, `make_lampara`, `make_campana_vigilia`) use the SDF render pipeline (`ITEM_RES`/`ITEM_SS`, `_shade`, `_heart_field`, `_sd_*`) — paint at 256x256 and downsample for real antialiasing; don't regress them to 16x16 pixel art. Entity/block textures keep their native sizes.
- Entity atlas UVs in the script **must match** the models: `client/SombraDebilModel.java`, `client/UmbralModel.java` and `client/ElSilenteGeoModel.java` + `assets/elsilente/geo/models/entity/el_silente.geo.json` (same 64x64 atlas as before the GeckoLib migration); if you change the UV layout, update the atlas coords in the script too (see `EYE_CORES`).
- GeckoLib assets: `assets/elsilente/geo/` (`.geo.json` models) and `assets/elsilente/animations/`; every bone referenced in animations must exist in the geo model.
- Outputs are committed under `src/main/resources/assets/elsilente/`.

## Layout
- `ElSilenteMod.java` — entrypoint; registers DeferredRegisters and attribute/spawn callbacks.
- `registry/` — `DeferredRegister` for items, entities, sounds, creative tabs.
- `entity/ElSilenteEntity.java` — core AI; two phases toggled by the synced `Aggro` flag (STALK vs CHASE). Also holds `PENDING_SPAWNS`. Implements `GeoEntity` (GeckoLib controllers; attack via `triggerAnim("attacks", "attack")`). Same class backs `silente_verdadero` (realm variant, `createRealmAttributes()`).
- `entity/SombraDebilEntity.java` (neutral; evolves into El Silente), `entity/UmbralEntity.java` (boss: phases, slam, enrage, never despawns).
- `event/` — Forge handlers: `AmbushHandler` (server tick: ambushes + pending respawns), `RitualHandler` (`ProjectileImpactEvent`), `TheftHandler` (`LivingDeathEvent`), `AmuletHandler`, `PresenceHandler` (presence per UUID), `DreamHandler` (nightmare on wake), `AdvancementHandler` (code-granted criteria), `ReinoHandler` (Umbral death opens the portal; realm teleport round-trip), `SpawnAudit` (startup spawn log).
- `block/` — `TrofeoUmbralBlock(+Entity)` (safe zone vs El Silente), `LineaSalBlock` (anti-spawn, radius 12), `PortalUmbralBlock` (realm portal, entered by walking in).
- `client/` — client-only renderers (`ElSilenteRenderer`: `GeoEntityRenderer` + emissive eye `GeoRenderLayer`; `SombraDebilRenderer`; `UmbralRenderer`), `ElSilenteGeoModel`, `ReinoSombrasEffects` (realm fog/sky, registered with `RegisterDimensionSpecialEffectsEvent`), presence/nightmare/phase-sound FX; registered via `@Mod.EventBusSubscriber(..., value = Dist.CLIENT)`.
- `config/ElSilenteConfig.java` — `config/elsilente-common.toml` (sections: general / spawns / presence / defenses / endgame).
- `util/Spawns.java` — shared spawn-position helpers; `util/SaltBarrier.java` — salt protection radius.
- `src/main/resources/data/elsilente/` — advancements, recipes, loot tables, worldgen (`santuario`), `dimension/` + `dimension_type/` (`reino_sombras`) and `forge/biome_modifier/` spawns.

## Conventions / gotchas
- Keep comments, log strings, docs and tooltips in Spanish. Keep `assets/elsilente/lang/es_es.json` and `en_us.json` in sync.
- Prefer unicode escapes (e.g. `\u00c9l`) over raw accented literals in `.java` to avoid encoding issues.
- `build/`, `run/`, `.gradle/` are gitignored dev/output and must not be edited; `run/` holds a dev world save and logs used for debugging.
- `changelog.txt` is the upstream Forge changelog, not this mod's history.
- **GeckoLib + `runClient` (importante)**: GeckoLib 4.4.9 incluye un mixin (`TextureManagerMixin`) cuyo refmap traduce `getTexture`→SRG `m_118506_`. En el entorno dev (official/Mojmap) ese método ya se llama `getTexture`, así que aplicar el refmap crashea en "Initializing game" (issue bernie-g/geckolib#746). El fix —ya aplicado en `build.gradle`— es `property 'mixin.env.disableRefMap', 'true'` en el run config `client`. **No lo quites**: solo afecta al run dev; el jar de producción sigue usando el refmap con normalidad.
