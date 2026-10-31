# Publishing

The `publishMods` task uploads the selected Fabric or NeoForge jar to Modrinth and CurseForge. It uses the root `CHANGELOG.md` as the release notes. The configured game versions come from `mod.mc_releases` in `stonecutter.properties.yaml`.

Run a dry run explicitly with:

```sh
./gradlew :1.21.1-fabric:publishMods :1.21.1-neoforge:publishMods -Ppublish.dry_run=true
```

Without `publish.dry_run`, the task uses dry-run mode if either upload token or either project ID is missing. Set both `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` as environment variables, or provide `publish.modrinth_token` and `publish.curseforge_token` Gradle properties, to allow live uploads after you set both project IDs. Do not commit tokens.

The project IDs in `stonecutter.properties.yaml` are the same as the former addon's IDs. Live publishing stops when either ID is blank.

Set `publish.release_type` to `stable`, `beta`, or `alpha`. The current value is `beta`.
