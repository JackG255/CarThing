# Testing

All tests are JVM unit tests in `app/src/test/`. They use JUnit 4, with Robolectric where Android is
needed. There are no instrumented tests.

```
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests "com.carthing.data.maintenance.*"
```
Reports are written to `app/build/reports/tests/`. When CI fails, it uploads them as the `test-report` artifact.

## Kinds of tests
| Kind | Examples | Setup |
|---|---|---|
| Pure logic | `FuelEconomyTest`, `UsageTest`, `MaintenanceStatusTest`, `ReminderPolicyTest`, `DeadlineStatusTest`, `BackupPolicyTest`, OCR parser tests | Plain JUnit; pass fixed `now`/`today` values and a `ZoneId` |
| Database and repositories | `DaoTest`, `RepositoryTest`, `MaintenanceRepositoryTest`, `BackupRepositoryTest` | `@RunWith(RobolectricTestRunner::class)`, `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`, `runTest` |
| Migrations | `MigrationTest` | `MigrationTestHelper` with the exported schemas; see below |
| ViewModel | `VehicleDetailViewModelTest` | Real repositories over an in-memory database |

## Conventions
- Never read the real clock in logic you test. Pass `nowMillis` in, or inject `clock: () -> Long`, as `BackupRepository` and `AttachmentRepository` do.
- Build repositories directly in tests. Don't go through `AppContainer`.
- Add a regression test for each fixed bug next to the related tests.

## Migration tests
The exported schemas are added to the debug assets (`sourceSets["debug"].assets.srcDir("schemas")`),
so `MigrationTestHelper` can read them under Robolectric. For a new migration:
1. Create the database at the old version and insert rows with raw SQL.
2. Call `helper.runMigrationsAndValidate(name, newVersion, true, MIGRATION_x_y)`.
3. Open it with Room (`openRoom()`) and check the data through the DAOs.

See the schema checklist in [DATA_MODEL.md](DATA_MODEL.md#checklist-changing-the-schema).
