# Backup and restore

The code lives in `data/backup/`, `ui/backup/` and `notifications/AutoBackupWorker.kt`.

## File format
A backup is a **zip**:
```
backup.json              # BackupFile, pretty-printed JSON
attachments/<uuid>.jpg   # every photo referenced by backup.json
```
`BackupFile` (`BackupFile.kt`) mirrors the entities through DTOs, kept separate from Room so that a
schema change is always a deliberate format change. The current `FORMAT_VERSION` is **4**:

| Format | Added |
|---|---|
| 1 | vehicles, fuel, service, maintenance items, deadlines |
| 2 | `odometerEntries` |
| 3 | `attachments` (photos in the zip) |
| 4 | `attachments.vehicleId` (service book), `maintenanceItems.icon` |

Plain JSON files from before photos existed can still be restored. Unknown keys are ignored, so an
older app can read fields added later within the same format.

## Restore
1. `BackupArchive.read` parses the file and validates it without changing anything. It rejects:
   - files from a newer format;
   - broken references (an entry for a missing vehicle, or a photo without exactly one owner);
   - duplicate records;
   - unsafe photo names (only `[A-Za-z0-9-]{1,64}.jpg` is accepted);
   - photos listed in the JSON but missing from the zip;
   - archives larger than 1 GB.
2. The user confirms after seeing a `BackupSummary`.
3. `restore` writes the photos first. It then replaces all data in one transaction (`BackupRepository.replaceAll`) and finally deletes photos that are no longer referenced.

Errors are reported as `InvalidBackupException`, with a message the user can read.

## Automatic backups
- The user picks a folder through the Storage Access Framework. The persisted tree URI is stored in `BackupSettings` (`backup_settings` preferences).
- `AutoBackupWorker` runs **weekly**. `FolderBackup` writes `carthing-auto-yyyy-MM-dd-HHmmss.zip` and keeps the newest **5** automatic backups (`BackupPolicy`). Other files in the folder are never touched.
- If a run fails, for example because the folder was deleted, the error is stored and shown in the backup dialog. The job itself still reports success.

## Reminders
`BackupPolicy.shouldRemind` asks the daily check to post a reminder when all of these hold:
- there is at least one vehicle;
- the last backup, manual or automatic, is more than **30 days** old or doesn't exist;
- no reminder has been sent in the last **14 days**.

## Photos
`PhotoStore` saves imported images to `filesDir/attachments/`. Each one is rotated upright, scaled
to at most 2000 px on the long side, and saved as a JPEG at quality 85 under a random UUID name.
The daily check deletes files that no attachment row refers to and that are more than 24 hours
old. The age check protects forms that haven't been saved yet.

## Android's own backup
Android's cloud backup and device transfer include the database and preferences but **not photos**,
and they leave out `backup_settings.xml`. That's why the in-app zip backup is the one to recommend.

## Changing the format
When adding or changing backed-up data:
1. Add the field to the DTO with a default value, so older files still parse.
2. Bump `FORMAT_VERSION` if older apps must refuse the new files. Otherwise note the change in the table above.
3. Update `export`, `replaceAll` and `validate` in `BackupRepository`.
4. Extend `BackupRepositoryTest` / `BackupArchiveTest` with a round trip, and add a case for reading an older file.
