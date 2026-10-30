# Review slice-transport-coroutines-geruest (95c5c56)

* Gegenstand: Diff `f6c668f..95c5c56` (Pin, Kanal, Nachrichtenleser und -schreiber, Tests)
* Plan: [slice-transport-coroutines-geruest](../plan/planning/in-progress/slice-transport-coroutines-geruest.md), Welle [welle-transport-tls](../plan/planning/welle-transport-tls.md)
* Grundlage: [ADR-0003](../plan/adr/README.md), [ADR-0001](../plan/adr/README.md), [TDS-FA-01](../../spec/lastenheft.md), [TDS-QA-02](../../spec/lastenheft.md), [TDS-QA-06](../../spec/lastenheft.md), [SPEC-007](../../spec/spezifikation.md), [SPEC-024](../../spec/spezifikation.md), [SPEC-027](../../spec/spezifikation.md), [SPEC-028](../../spec/spezifikation.md), [SPEC-042](../../spec/spezifikation.md), [SPEC-046](../../spec/spezifikation.md), [SPEC-048](../../spec/spezifikation.md), `AGENTS.md` §3.1–3.8
* Sensor: `make gates` lief grün (Exit 0; Teile aus dem Docker-Cache).

Ergebnis: kein HIGH, kein MEDIUM. Die Nebenläufigkeit von Adapter und Speicher-Kanal ist in den geprüften Abläufen korrekt; Befunde sind Vertragsschärfe und Aussagenbreite.

## Findings

### F1 · LOW · Einhaltung der Ein-Leser/Ein-Schreiber-Festlegung nur dokumentiert
* quelle: [ADR-0003](../plan/adr/README.md), Maintainability
* pfad: `SuspendingChannel.kt` · "höchstens ein Lesevorgang und höchstens ein Schreibvorgang zugleich"; `MemoryChannel.kt` · "Die Einschränkungen von [SuspendingChannel] gelten."
* befund: Der Adapter erzwingt es indirekt (das JDK wirft `ReadPendingException`/`WritePendingException` aus `start`, sie verlassen den Adapter als Ausnahme). Der Speicher-Kanal erzwingt nichts: zwei gleichzeitige Schreiber verschränken Bytes still (partielle Schreibvorgänge), zwei Leser teilen sich ein CONFLATED-Signal, einer kann ohne Fehler hängen bleiben. Kein Test belegt das Verhalten bei Verletzung.
* verifizierbar: nein (kein Gate, nur Test)
* klasse: Festlegung nicht erzwungen, Verhalten bei Verletzung je Implementierung verschieden

### F2 · LOW · KDoc "nie eine Ausnahme des Kanals" breiter als das Verhalten
* quelle: Hard Rule 3.8
* pfad: `ChannelResult.kt` · "nie eine Ausnahme des Kanals"; `SocketChannelAdapter.kt` · "Programmierfehler (gleichzeitiger zweiter Lesevorgang, nicht verbundener Kanal) bleiben Ausnahmen"
* befund: Die Schnittstellen-KDoc sagt "nie", der Adapter wirft in den genannten Fällen (und bei schreibgeschütztem Puffer, `IllegalArgumentException`). Die Einschränkung steht nur in der Adapter-KDoc, nicht im Vertrag, den Aufrufer lesen.
* verifizierbar: nein
* klasse: Aussage breiter als Beleg (KDoc)

### F3 · LOW · `SocketChannelAdapter.close()` verwirft Fehler ohne Aussage im Vertrag
* quelle: Hard Rule 3.8, Maintainability
* pfad: `SocketChannelAdapter.kt` · `runCatching { channel.close() }`; `SuspendingChannel.kt` · "Schließt den Kanal; ein weiterer Aufruf ist wirkungslos."
* befund: `runCatching` fängt jede `Throwable`, nicht nur `IOException`; die KDoc der `close()` sagt nichts über das Verschlucken. Für den Abbruchpfad ist das vertretbar (der Kanal soll zu sein), aber der Vertrag verspricht "Schließt den Kanal" ohne Beleg, dass er zu ist. Ein Test für das Fehlerverhalten von `close()` fehlt.
* verifizierbar: nein
* klasse: Fehler verschluckt ohne dokumentierte Zusage

### F4 · LOW · Grenzwechsel vor/nach Login am laufenden Leser nicht vorgesehen
* quelle: [SPEC-046](../../spec/spezifikation.md), [SPEC-007](../../spec/spezifikation.md)
* pfad: `MessageReader.kt` · "Mit `limits` aus [FramingLimits.preLogin] gilt die Grenze vor dem Login, mit den Standardgrenzen die danach."
* befund: `limits` ist ein Konstruktorparameter, der Assembler ist privat. Ein Leser, der über LOGIN7 hinaus lebt, behält die Vor-Login-Grenze; ein Austausch des Lesers verwürfe bereits gelesene Folgebytes (Puffer, `ready`, Assembler-Zustand) und könnte die Folgenachricht desynchronisieren. Der Plan verlangt nur "Grenze als Parameter", die KDoc suggeriert jedoch den Wechsel. Der Slice für den Sitzungsautomaten ([ADR-0004](../plan/adr/README.md)) muss das lösen.
* verifizierbar: nein
* klasse: Vertrag lässt Folgeanforderung offen

### F5 · LOW · RST-Zweig des Abbruchtests schwächer als seine KDoc
* quelle: Hard Rule 3.8
* pfad: `SocketChannelLoopbackTest.kt` · "RST einen Kanalfehler oder, falls die Daten zuvor ankamen, ebenfalls `MESSAGE_TRUNCATED`"
* befund: Für `reset=true` wird nur `Failed` geprüft; die KDoc nennt zwei Ausgänge, der Test unterscheidet sie nicht. Das ist plattformrobust, aber ein `Failed` mit beliebigem Grund besteht. Das RST-Verhalten ist betriebssystemabhängig und nur auf dieser Umgebung gemessen.
* verifizierbar: ja (Test)
* klasse: Testaussage schwächer als Dokumentation

### F6 · INFO · Zeitbasierter Rückstau-Test ist einseitig
* quelle: Maintainability
* pfad: `SocketChannelLoopbackTest.kt` · `delay(500)`
* befund: Ein langsamer Lauf macht den Test nicht rot (er prüft "noch aktiv nach 500 ms"), er kann nur zu Unrecht grün sein, wenn Betriebssystem-Puffer 32 MiB fassen. Er belegt das Suspendieren des Sockets, nicht den Writer; die Aussage "Gemessen ist das Suspendieren auf dieser Maschine" ist ehrlich begrenzt. Der Speicher-Rückstau ist über `MemoryChannel` deterministisch belegt. Der Test hält rund 96 MiB Heap (Nutzdaten plus Paketliste).
* verifizierbar: ja
* klasse: zeitbasierter Test

### F7 · INFO · Speicher-Kanal im Produktionsbaum
* quelle: [ADR-0003](../plan/adr/README.md), Plan §3
* pfad: `MemoryChannel.kt` · `class MemoryChannel`
* befund: Der Plan führt ihn ausdrücklich unter `src/main`; Schichtregeln (detekt, `make gates`) sind eingehalten, keine Hard Rule berührt. Er hat in `src/main` keinen Verbraucher außer Tests und kopiert byteweise mit Modulo; für Produktion nicht ausgelegt, was die KDoc nicht sagt. Wenn spätere Slices ihn für Simulation nutzen, ist das zu klären.
* verifizierbar: nein
* klasse: Testhilfe im Produktionsbaum

### F8 · INFO · `MessageWriter` hält alle Pakete einer Nachricht im Speicher
* quelle: [SPEC-028](../../spec/spezifikation.md)
* pfad: `MessageWriter.kt` · "[PacketWriter.split] erzeugt alle Pakete einer Nachricht auf einmal"
* befund: Der Speicher wächst mit der Nachricht (Payload plus Kopie); als Festlegung gekennzeichnet und im Slice nicht zu lösen. Für große Ergebnisströme ist die Nachrichtengröße nach oben ungebunden (der Schreiber prüft `maxMessageBytes` nicht). Zuständig: nach Einschätzung des Aufrufers `slice-backend-streaming-nachweis` (Backpressure und Speichergrenze); der Reviewer nannte keinen belegten Slice und `slice-qa-performance`.
* verifizierbar: nein
* klasse: Speicher proportional zur Nachricht

### F9 · INFO · Pin und Lock
* quelle: [SPEC-042](../../spec/spezifikation.md), [TDS-QA-06](../../spec/lastenheft.md)
* pfad: `gradle/libs.versions.toml` · `coroutines = "1.10.1"`; `gradle.lockfile` · `org.jetbrains:annotations:23.0.0`
* befund: Version steht nur im Katalog, `build.gradle.kts` nennt sie nicht; Lock und Prüfsummen (`kotlinx-coroutines-core-jvm-1.10.1.jar`, `.module`, BOM) sind eingetragen. `annotations:23.0.0` kommt als Abhängigkeit von `kotlinx-coroutines-core-jvm` und hebt per Konfliktauflösung die 13.0 auf Compile-/Runtime-Classpath an; 23.0.0 war für `detekt` schon geprüft (Prüfsumme vorhanden), 13.0 bleibt für die Kotlin-Werkzeug-Konfigurationen. Es sind reine Annotationen, kein Laufzeiteffekt. `CoroutinesPinTest` belegt Katalog-Version über den Jar-Namen und sagt selbst, dass er die Prüfsumme nicht belegt.
* verifizierbar: ja (`make gates`)
* klasse: Transitive Abhängigkeit durch Lock

### F10 · INFO · `@spec`-Tag SPEC-027 weit gefasst
* quelle: Maintainability
* pfad: `MemoryChannelTest.kt` · "@spec SPEC-027" an den Abbruch-/Schließen-Tests
* befund: [SPEC-027](../../spec/spezifikation.md) fordert Fuzz-/Property-Tests; Tests zu Schließen und Abbruch tragen den Tag, nur Fuzz-Test und Abschneide-Test passen unmittelbar. Reine Zuordnungsfrage für die Rückverfolgung, `[SPEC-028]` für Zerstückelung folgt der Codec-Konvention.
* verifizierbar: nein
* klasse: Tag-Zuordnung

## Geprüft ohne Befund
* Resume-Rennen im `SocketChannelAdapter`: Abbruch schließt Kanal, spätes `completed`/`failed` wird von `suspendCancellableCoroutine` verworfen; Fehler als `ChannelResult.Failed`, `CLOSED` vs. `IO_FAILURE` über `isOpen`.
* `MemoryChannel.Pipe`: Zustand hinter `lock`, Signale CONFLATED mit erneuter Prüfung (kein verlorenes Wecken bei je einem Leser und Schreiber), Schließen weckt wartende Gegenseiten, Ringpuffer-Umlauf getestet.
* `MessageReader`: ruft Assembler ohne Nachrechnen; nach Fehler/Ende sticky und ohne weiteres Lesen, vollendete Nachrichten zuerst; Grenzabbruch vor dem Weiterlesen durch Zählkanal belegt (Speicher-Kanal und Socket).
* `MessageWriter`: suspendiert über `writeFully`, keine eigene Warteschlange, Fehler als Ergebniswerte.
* Verzeichnisse `src/main/.../transport/channel`, `.../transport/messaging`, `src/test/...`: keine Imports aus `port`/`simulation`/`runtime`, kein `@Suppress`, Kommentare tragen Zusage/Kopplung/Grenze (Hard Rule 3.7).
* `build.gradle.kts`, `gradle/`, `gradle.lockfile`: Docker-only, keine Host-Aufrufe, `deps-lock`-Form.
* Plan-Dokument: Änderungen sind Ist-Beschreibung ohne Chronik.
