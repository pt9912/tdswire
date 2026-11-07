# Tests mit Wanduhr und Fristen werden unter Last des Hosts rot, ohne dass sich Code geändert hat

**Sub-Area:** `*` (Tests, Verifikation)

Tests, die auf ein Ereignis mit fester Wanduhr-Frist warten (`TelemetryJsonTest … principal`: 20 s auf `LOGIN_ACCEPTED`; `ListenerAbortTest … nach der Frist`: `shortTimeout`), werden bei hoher Last des Hosts (Lastmittel 22 bis 33 durch parallele Läufe) vereinzelt rot und sind im nächsten Lauf grün. Sie liegen außerhalb des Diffs des Vorgangs, der sie rot sah; ein rotes Gate und eine eingecheckte Abdeckungsmatrix (`docs/plan/spec-abdeckung.md`) halten den Flake fest. Verwandt: `BEO-TDS/wanduhr-test-nur-wenige-laeufe-gesehen` (dort Risiko, kein Auftreten), `BEO-TDS/flake-ohne-namen-des-roten-testfalls` (dort fehlte der Name), `BEO-TDS/test-tempdir-aufraeumen-race-unter-last` (eigener Mechanismus).
