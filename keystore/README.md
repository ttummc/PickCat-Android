# Shared test signing key

`pickcat-debug.jks` is a shared test/debug signing key for reproducible debug builds and in-app updates.

- Store password: `android`
- Key alias: `androiddebugkey`
- Key password: `android`

This key is public and is only suitable for this project's noncommercial test builds. Do not use it for other applications or production distribution. If the project starts shipping a production-signed build, migrate to a private release key stored outside the repository.
