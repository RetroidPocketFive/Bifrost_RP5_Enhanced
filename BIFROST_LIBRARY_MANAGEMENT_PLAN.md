# Bifrost Library Management — Implementation Plan

## Baseline and scope

- Implementation branch: `feature/bifrost-library-management`
- Starting point: approved baseline `baseline/v1.7.0-alpha.5`
- Baseline commit: `e42341a6d290a216d00bee1de0c36a6f35e6f879`
- Keep all existing LED, calibration, sampling, preset, and profile-switching behaviour intact.
- Extend the existing `BackupArchiveTransfer` and `ThemeArchiveTransfer` components instead of introducing a competing transfer implementation.

## User-facing navigation

- Home-screen gear opens **General Settings**.
- The Current Preset panel opens **Profile Settings** for the active lighting profile.
- General Settings includes a direct **Profile Settings** navigation entry.
- Profile Settings includes **Lighting Profiles**, **Saved Sample Areas**, and **Import / Export**.

## Folder layout

When the user selects a parent folder through Android's Storage Access Framework, Bifrost creates or reuses:

```text
Bifrost/
  Saved/
    Profiles/
    Themes/
    Sample Area/
    Media/
      Images/
      Sounds/
      Music/
      Videos/
    Settings/
    Calibration/
    Devices/
    Presets/
    Logs/
    Backups/
  manifest.json
```

Folder creation should be lazy or idempotent: re-running setup must not delete or overwrite user files. If the user selects an existing Bifrost folder, reuse compatible folders and report conflicts without destructive changes.

## Import/export modes

1. **Single item:** export/import one lighting profile, theme, Sample Area profile, or media item.
2. **Selected items:** select multiple supported items/categories.
3. **Full backup:** portable archive containing selected settings, profile data, and media, plus a versioned manifest.
4. **Folder-organised export:** place files in their matching subfolders for easy manual browsing and copying.

Import must preview contents and offer safe duplicate handling (skip, rename, or replace). Replacing items requires explicit confirmation. Importing a lighting profile must not silently overwrite global calibration. Global settings and calibration are separate selectable categories in full backups.

## Android storage and security

- Use Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`, `ACTION_OPEN_DOCUMENT`, and `ACTION_CREATE_DOCUMENT`) rather than broad storage permissions.
- Persist the URI permission when the platform grants it, and let users change/re-authorize the root folder.
- Validate schema and version, impose sensible limits on archive entry count and uncompressed size, reject path traversal/absolute paths and unexpected file types, and avoid executing imported content.
- Use safe temporary staging and only apply an import after validation.
- Media support is for user-selected images, sounds, music, and videos handled by supported Android APIs.
- Removing or reducing reliance on feature plugins is a separate change. Do not remove plugin code or INTERNET permission until all dependencies and intended network features have been reviewed.

## Proposed implementation phases

1. **Inventory and tests:** identify current preference keys, profile data format, Sample Area persistence, media storage, and existing import/export entry points.
2. **Folder root and layout:** add a SAF-backed storage helper that creates the folder tree without destructive side effects.
3. **Settings navigation:** introduce General Settings and Profile Settings while retaining existing screens and entry points.
4. **Saved Sample Areas:** expose existing saved Sample Area configurations in Profile Settings; do not duplicate their underlying storage model.
5. **Folder-based transfer:** extend existing archive transfer code to support category selection, safe file naming, manifest/versioning, and folder-organised exports.
6. **Full backup/restore:** add preview, duplicate handling, cancellation, validation, and rollback-safe apply behaviour.
7. **Plugin reduction review:** map plugin features/dependencies before deciding what can safely be removed.
8. **Verification:** build and run available tests, inspect the APK artifact, then have the user test on the Retroid Pocket 5 before merging into the approved baseline.

## Acceptance criteria

- Existing advanced features remain available and retain their current behaviour.
- The user chooses the storage root and can change it later.
- Folder setup is repeatable and never wipes unrelated files.
- Profiles and Sample Area profiles can be transferred separately or together.
- A full backup can be restored without silently overwriting global calibration.
- Invalid or incompatible imports fail safely with a clear message.
- No plugin removal or broad permission removal occurs without dependency review.
- Changes remain on the feature branch until build and device verification are complete.