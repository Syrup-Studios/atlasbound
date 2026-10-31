# Changelog

## 0.4.0

Atlasbound is now a standalone fork of Antique Atlas 4. It reads map data from the server world and sends it to clients. Each atlas keeps its own exploration and markers.

### Migration from 0.3.3

- On server start, Atlasbound checks for saved atlas data files. The first player to use an existing atlas can import their Surveyor exploration data and personal or shared Surveyor landmarks into it. Later holders do not import their data into that atlas.
- Imported Surveyor landmarks become simple editable Atlasbound markers. Surveyor-specific icons and formatting are not retained.
- Before import, Atlasbound copies player exploration and landmark source data to `world/data/atlasbound-surveyor-migration`. This lets it read the source after a player save changes. If a snapshot cannot be saved, Atlasbound uses the readable source in memory for the current server session.
- New atlases do not receive legacy data. The importer reads compressed NBT files up to 64 MiB. Invalid or unreadable legacy data is skipped where possible and does not stop server startup. It keeps readable exploration regions and landmarks, preserves existing atlas markers, and imports markers up to the atlas marker limit.

- Antique Atlas 4 and Surveyor are no longer required dependencies.
