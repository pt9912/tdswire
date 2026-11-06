# `ConfigKeys` trägt Konstanten ohne Aufrufer in `src/main`

**Sub-Area:** `*` (Bibliothek, Konfiguration)

`internal object ConfigKeys` führt `ENCRYPTION_POLICY`, `AUTH_USERS_FILE` und `AUTH_PLAIN_TESTMODE`; in `src/main` ruft sie niemand (gelesen am 2026-11-06 mit `grep`), `src/test` nutzt sie (etwa `EnvironmentConfig`). Der Test, der sie gegen `EnvKeys` des Testservers pinnte, entfiel im Umbau von [slice-explicit-api-umbau](../../../done/slice-explicit-api-umbau.md) (Verifikation LOW-B). Entscheidung offen: entfernen oder wieder an einen Aufrufer binden; keine Frage der Sichtbarkeit.
