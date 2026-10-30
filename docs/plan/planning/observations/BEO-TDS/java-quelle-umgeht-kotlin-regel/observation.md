# Eine Kotlin-Regel wird durch Java-Quelltext umgangen

**Sub-Area:** `*` (Transport, Build und Gates)

Die Test-Attrappe `FailingCloseChannel.java` ist das erste Java-Quellfile des Repos. `AsynchronousSocketChannel.connect` verlangt den Typ `Void`, den die detekt-Regel `ForbiddenVoid` in Kotlin verbietet; in Java ist er erlaubt, und detekt prüft Java nicht. `AGENTS.md` §3.2 nennt nur `@Suppress` und `@SuppressWarnings` im Kotlin-Quelltext; der Zweck der Regel wird ohne Ausnahmeeintrag, ADR oder Carveout umgangen.
