# Config migration and persistence tests

Run from the repository root with JDK 17 or newer and curl available:

```sh
bash tests/config-jvm/run-tests.sh
```

The runner compiles the actual production `Config.java` and `Schedule.java`, with minimal host-only `android.content.Context` / `SharedPreferences` facades and an in-memory preference implementation. It downloads pinned `org.json:json:20240303` from Maven Central into the ignored `build` directory and verifies its SHA-256. This JSON implementation is for these JVM tests only; the APK uses Android's built-in JSON implementation and receives no dependency from this test.

The checks exercise v1 migration, repeated loading, preemptive location saving, paused plans, weekly assignments and custom times, future-only activation timestamps, draft isolation, preservation of live handled/skip state when saving stale drafts, rejection of invalid plans, commit failure handling, and safe handling of corrupt data. They do not test real Android preference disk I/O, Android process lifecycle, alarm delivery, Samsung unlock behavior, or Google Maps guidance.
