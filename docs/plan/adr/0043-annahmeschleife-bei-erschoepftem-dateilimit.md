# ADR-0043: Die Annahmeschleife des Listeners übersteht ein erschöpftes Limit offener Dateien, indem der Fehlerpfad der Annahme beim Start vorbereitet wird (Klassen berührt, Instanzen vorgebaut)

**Status:** Proposed

**Datum:** 2026-11-08

**Autor:** Architect (Entwurf); Annahme: ausstehend (pt9912)

**Bezug:** [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0003](0003-transportframework.md), [ADR-0020](0020-logformat-und-observability-schnittstelle.md)

**Schärft:** [TDS-FA-09.a](../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose) (Abschnitt 4: der Satz zu `ACCEPT_FAILED`), `SPEC-048` (nur als Abgrenzung: kein Fall von `TDS_INTERNAL_ERROR`, es gibt keine Anfrage), [SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden) (nur gelesen)

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

**Befund (gemessen, eine Umgebung).** Slice [slice-listener-annahmeschleife-bei-erschoepften-dateien](../planning/open/slice-listener-annahmeschleife-bei-erschoepften-dateien.md), Herkunft Review F-2 von slice-betrieb-telemetrie-verdrahtung-belegen: In einer Kind-JVM mit `ulimit -n` von 128 bis 160 endet der `failed`-Callback von `TdsListener.acceptOne` ohne vorgeladene Klassen mit einem `NoClassDefFoundError` (Thread `tdswire-listener-io-1`), weil `Accept.Failed` nicht mehr geladen werden kann. Die Continuation wird nicht fortgesetzt; die Schleife steht, es kommt weder `ACCEPT_FAILED` noch ein Wiederannehmen. **Mit vorgeladenen Klassen** entsteht das Ereignis, und der Server nimmt nach dem Schließen der Verbindungen wieder an. Andere Umgebungen, ein Betrieb aus einem Jar statt aus Klassenverzeichnissen und andere Callbacks der NIO-Gruppe sind **nicht gemessen** (`AGENTS.md` §3.8).

**Ist.** `acceptOne` setzt im `failed`-Callback `Accept.Failed(exc.javaClass.name)` zusammen und setzt die Continuation fort; `acceptConnections` meldet `ListenerEvent.AcceptFailed`, wartet `ACCEPT_RETRY` und nimmt wieder an. Der Ablauf ist richtig, solange der Callback Klassen laden kann.

**Was die Entscheidung bestimmt.** Es gibt keine öffentliche Fläche und keine Anfrage, an der ein Client den Mangel sieht; betroffen ist die Verfügbarkeit nach einem Ressourcenmangel ([TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität): Stabilität; der Prozess stürzt nicht ab, nimmt aber nicht mehr an). Die Wahl ändert daher weder die öffentliche Fläche noch eine Zusage über die Annahme hinaus (Grenze offener Dateien je Prozess, Betriebsanleitung: ausdrücklich nicht Gegenstand, siehe Slice §1).

## Entscheidung

Vorschlag: **Option A**. Der Listener bereitet den Fehlerpfad der Annahme beim Start vor: die Klassen, die der `failed`-Callback und der Weg bis `ListenerEvent.AcceptFailed` berühren, werden einmal berührt, und der Callback setzt eine vorgebaute Instanz fort, soweit sich das ohne neue Klasse ausdrücken lässt. Welche Klassen das sind, bestimmt der Test der Kind-JVM **ohne** Vorladen (er ist der Beleg, keine Liste im Quelltext, die für vollständig erklärt wird).

Begründung in einem Satz: Der einzige Weg, der gemessen trägt, ist „Klassen vorhanden“; A stellt genau das im Produktcode her, mit dem kleinsten Eingriff, ohne neue Fläche und ohne neues Verhalten bei Erfolg.

**Grenze, die A nicht schließt (Setzung, benannt):** Klassen des Embedders (die Senke von `TelemetrySink`, ihre Formatierung) kann die Bibliothek nicht vorbereiten; ein Ereignis kommt dort nur an, wenn auch die Senke unter dem Limit arbeitet. Die Bibliothek sichert ihren Teil: die Schleife läuft weiter. Bei Rückfall (Option B) gilt dasselbe.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A — Fehlerpfad beim Start vorbereiten** (gewählt als Vorschlag) | Kleinster Eingriff in `TdsListener`; entspricht dem gemessenen Gutfall (Klassen vorgeladen); keine Fläche; der Test ohne Vorladen belegt, die Mutation (Vorbereitung entfernen) macht ihn rot | Welche Klassen genügen, ist nur durch Messung bekannt; bei einer Änderung am Fehlerpfad kann eine Klasse hinzukommen (der Test zeigt es dann rot); Jar-Betrieb nicht gemessen |
| B — den `LinkageError` im Callback fangen, vorgebaute Instanz (`Accept.Failed` ohne Typname) fortsetzen, weiter annehmen | Trägt auch, wenn eine Klasse fehlt; die Schleife steht sicher nicht | Der Typname geht verloren, das Ereignis bleibt aus oder ist leer; der Catch-Block und das Fortsetzen brauchen selbst Klassen (nicht gemessen); verdeckt andere `Error` im Callback (Slice §1 schließt andere Callbacks aus) |
| C — bei erschöpftem Limit den Listener mit Meldung beenden | Kein stilles Hängen | Die Meldung braucht dieselben Klassen; ein Embedder verliert die Annahme dauerhaft, obwohl das Limit wieder frei wird; widerspricht dem gemessenen Wiederannehmen |
| D — nichts tun, Spezifikation nennt die gemessene Bedingung als Grenze | Kein Produktcode; ehrlich nach §3.8 | Die Annahme bleibt in dieser Lage stehen; TDS-QA-02 (Stabilität) bleibt an der Stelle ungedeckt; Ziel des Slice nicht erreicht |

## Konsequenzen

- Positiv: Nach Erschöpfung und Freiwerden nimmt der Server wieder an, ohne Eingriff; `ACCEPT_FAILED` entsteht auch ohne Vorladen im Test; `AcceptChild` kann das Vorladen verlieren und belegt dann den Produktcode.
- Negativ: Ein Stück Vorbereitung im Start, das nur ein Test begründet; Grenze beim Embedder (oben); gemessen bleibt eine Umgebung.
- Folgepflicht: im Slice (kein eigener Folge-Eintrag): Satz zu `ACCEPT_FAILED` in Abschnitt 4 nennt die gemessene Bedingung; Rückfall auf B, wenn der Test mit A rot bleibt (dann mit Grund in der Closure-Notiz). Unabhängig davon ist der Befund Register-Eintrag (`BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still`); kein neuer Eintrag nötig.

**Akzeptiertes Negativ:** Die Wirkung in anderen Umgebungen und für andere Callbacks der NIO-Gruppe bleibt ungemessen; sie hat keinen eigenen Träger, weil der Befund im Register steht und der Slice sie ausdrücklich ausschließt.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Kind-JVM-Test (`AcceptChild`, `TelemetryWiringTest`) | ohne Vorladen: `ACCEPT_FAILED` im Sammler, danach Wiederannehmen; Mutation: Vorbereitung entfernt, Test rot | `make test` |

## Re-Evaluierungs-Trigger

Der Test wird in der Umgebung der Gates rot, obwohl A umgesetzt ist (dann B), oder ein Betrieb aus dem Jar zeigt das Hängen trotz A.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-08 | Proposed | [slice-listener-annahmeschleife-bei-erschoepften-dateien](../planning/open/slice-listener-annahmeschleife-bei-erschoepften-dateien.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
