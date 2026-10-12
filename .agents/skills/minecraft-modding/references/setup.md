# Setup, build and finding the real API

## Java (macOS, Homebrew)
- Minecraft 1.21.1 needs Java 21. `brew install openjdk@21` (keg-only, no sudo; `/usr/bin/java` stays a stub).
- Every shell that runs Gradle needs:
  ```sh
  export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
  ```
  Don't edit the user's shell config without asking; document the line in README/AGENTS.md.

## New project from the official MDK
```sh
curl -sL https://api.github.com/repos/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/tarball -o /tmp/mdk.tgz
mkdir MyMod && tar xzf /tmp/mdk.tgz -C MyMod --strip-components=1 && cd MyMod && git init
```
- `gradle.properties`: set `mod_id` (lowercase), `mod_name`, `mod_group_id`; add `geckolib_version=4.9.3`.
- `build.gradle`: add the GeckoLib repo and dependency:
  ```groovy
  repositories { maven { url = 'https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/'
      content { includeGroupByRegex("software\\.bernie.*") } } }
  dependencies { implementation "software.bernie.geckolib:geckolib-neoforge-${minecraft_version}:${geckolib_version}" }
  ```
  Latest versions: `curl -s https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-neoforge-1.21.1/maven-metadata.xml`.
- `src/main/templates/META-INF/neoforge.mods.toml`: add a required `geckolib` dependency block.
- Delete the example mod sources; package e.g. `dev.<user>.<modid>`.
- Extra run configs in `neoForge { runs { ... } }`, e.g. a scripted client:
  ```groovy
  autopilot { client(); systemProperty 'mymod.autopilot', 'true' }   // task: runAutopilot
  ```
- `.gitignore` already ignores `run/` (game dir: saves, logs, screenshots). Add a `.scratch/` for temp files.

## Find the real API before writing code
```sh
./gradlew createMinecraftArtifacts   # first run downloads/decompiles (~2 min)
mkdir -p /tmp/mcsrc && cd /tmp/mcsrc
unzip -qo ~/MyMod/build/moddev/artifacts/neoforge-21.1.*-sources.jar 'net/minecraft/world/entity/LivingEntity.java'
grep -n "travelRidden\|getRiddenInput\|tickRidden" net/minecraft/world/entity/LivingEntity.java
# GeckoLib
curl -sLO https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-neoforge-1.21.1/4.9.3/geckolib-neoforge-1.21.1-4.9.3-sources.jar
unzip -qo geckolib-*-sources.jar -d gl && grep -rn "public .*(" gl/software/bernie/geckolib/animation/AnimationController.java
```
Read how vanilla does the closest existing feature (horses for riding, `LocalPlayer` for input/sprint/jump,
`ProjectileUtil` for crosshair picking, `GameTestHelper` for test helpers) and copy the pattern.
Before a handoff, `unzip -l <sources.jar> | grep <Class>` every class the plan names.

## Commands
| Command | What |
|---|---|
| `./gradlew compileJava -q` | fast compile check |
| `./gradlew build` | jar in `build/libs/` (players also need GeckoLib) |
| `./gradlew runClient` | game with the mod (offline "Dev" account, opens a window) |
| `./gradlew runGameTestServer` | headless tests; exits non-zero on failure |
| `./gradlew run<Name>` | custom run configs |
Run long game tasks in the background and poll the log (no `timeout` on macOS):
```sh
(./gradlew --console=plain runAutopilot > /tmp/ap.log 2>&1; echo "done $?" >> /tmp/ap.log) &
for i in $(seq 1 100); do sleep 5; grep -q "^done" /tmp/ap.log && break; done
```
Startup check without a test harness: launch `runClient`, wait for `Sound engine started` in the log,
grep for `ERROR|Exception` (GeckoLib reports bad geo/animation JSON at resource load), then `pkill`.

## Mod structure that stayed readable
```
MyMod.java                 @Mod entry: register DeferredRegisters, attribute + creative-tab listeners
registry/ModEntities|ModItems.java   DeferredRegister + holders (EntityType.Builder: sized, eyeHeight, passengerAttachments)
entity/…Entity.java        gameplay; item/…Item.java
SomeGameEvents.java        @EventBusSubscriber(modid=...) game-bus events (damage etc.)
client/                    @EventBusSubscriber(modid=..., value=Dist.CLIENT): renderers, model, camera/HUD events
client/dev/                autopilot harnesses, inert unless a system property is set
test/                      @GameTestHolder(MODID) @PrefixGameTestTemplate(false) tests
```
NeoForge 21.1 routes mod-bus events automatically: `@EventBusSubscriber(bus = Bus.MOD)` is deprecated.
Recipes live in `data/<modid>/recipe/` (singular in 1.21), structures in `data/<modid>/structure/`.
