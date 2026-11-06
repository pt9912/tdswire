# `make spec-abdeckung` sieht Tests in C# nicht, `SPEC-045` bleibt ohne Nachweis

**Sub-Area:** `*` (Werkzeug, Abdeckungsmatrix)

`tools/spec-abdeckung.sh` liest den KDoc-Block-Tag `@spec SPEC-<NNN>` nur in Kotlin-Tests. Die 22 C#-Tests des Mitschnitt-Laufs tragen `// @spec SPEC-045` als Kommentar; in `docs/plan/spec-abdeckung.md` führt `SPEC-045` null Nachweise, obwohl die Tests grün laufen (in `make referenztest-dotnet`, nicht in `make test`).
