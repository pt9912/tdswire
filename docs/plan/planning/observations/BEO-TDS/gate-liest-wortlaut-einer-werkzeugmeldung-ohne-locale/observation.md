# Ein Gate liest den Wortlaut einer Werkzeugmeldung, ohne dass die Sprache der Meldung festgelegt ist

**Sub-Area:** `*` (Docker-Stages, `referenztest-dotnet/test-lauf.sh`)

Die Prüfung „ein Lauf ohne ausgeführten Test ist rot“ (Exit 3 des Modus `test`) sucht die englische Meldung von `dotnet test` (`No test matches`, `No test is available`). Mit `LANG` oder `LC_ALL` auf `de_DE.UTF-8` schreibt das echte `dotnet` „Kein Test entspricht …“; die Stage endete grün ohne einen Test (Verifikation V-1). Behoben durch eine feste Locale im Skript (`LC_ALL=C.UTF-8`, `LANG=C.UTF-8`, `LANGUAGE=en`). Grenzen, die bleiben: nur `de_DE.UTF-8` und `DOTNET_CLI_UICULTURE` mit `de-DE` und `fr-FR` sind gemessen; dass die feste Locale am echten `dotnet` greift, belegt eine einmalige Handmessung (2026-11-07), kein Gate, und der Selbsttest läuft gegen einen Stub; ein Image-Update (SDK-Pin) kann den Wortlaut ändern; die Suche läuft über die gesamte Ausgabe, auch über den Text eines Tests.
