# Nachverifikation: slice-handshake-login nach der Fix-Runde (dc08d50) — 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD und Entscheidungen), frischer Kontext. Der Bericht repariert nichts; außer dieser Datei wurde im Repo nichts geändert, nichts committet. Mutationen und Sonden liefen in Kopien des Baums (Docker-Build-Kontext, nichts gemountet).

**Gegenstand:** [slice-handshake-login](../plan/planning/in-progress/slice-handshake-login.md), Fix-Runde `git log 26b6327..HEAD`: Commits `b85a3f4` (Code und Tests) und `cc1b007` (Slice-Plan und Clientprofil), gemergt in `dc08d50`. Geprüfte Befunde: Findings F-1 bis F-6 aus [dem Review](2026-10-30-bda6cd6-slice-handshake-login.md) sowie M-1, M-2, L-1 bis L-7 aus [der Verifikation](2026-10-30-bda6cd6-verifikation-slice-handshake-login.md). Bezug: [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0003](../plan/adr/0003-transportframework.md), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md), [ADR-0011](../plan/adr/0011-pruefwertformat.md), [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-008](../../spec/spezifikation.md), [SPEC-012](../../spec/spezifikation.md), [SPEC-028](../../spec/spezifikation.md), AGENTS.md §3.7 und §3.8.

**Stand-Hinweis:** `main` steht inzwischen auf `13434ec`. Seit `dc08d50` änderten sich unter `src/` nur Dateien außerhalb von `protocol/session` (Token, Ergebnisabbildung); `protocol/session`, `docs/plan/planning/in-progress/slice-handshake-login.md` und `contracts/client-profile.md` sind unverändert. Mutationen und Testläufe nutzen den Baum von `dc08d50` (`src` byte-gleich mit `git archive dc08d50`).

**Ergebnis in einem Satz:** F-4, F-5 und F-6 sind behoben, F-1/M-2 mit Rest, F-2 ehrlich offen, F-3/M-1 per Festlegung geändert und offen; die behobenen Teile sind durch frisch gelaufene, mutationsgeprüfte Tests belegt; zwei Festlegungen der Fix-Runde tragen weiter, als ihr Beleg reicht (N-1, N-2), und die Entscheidung zur Abbruchausnahme des Adapters ist mit einer Folge im Listener-Kontext offen, die der Slice nicht nennt (N-3).

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| `docker build --target test --no-cache-filter test --progress=plain .` (Stage `test`, frisch, kein Cache-Treffer; entspricht `make test` ohne Cache) | `BUILD SUCCESSFUL in 3m 40s`, 340 Tests PASSED, kein FAILED |
| `make gates` (Exit 0) | grün, aber die Docker-Stages kamen größtenteils aus dem Cache (39 CACHED); als Frischbeleg gilt der Lauf darüber, nicht dieser (Beobachtung `BEO-TDS/test-stage-cache-verdeckt-wiederholung`) |
| `git diff 26b6327..HEAD -- src` nach `@Suppress`, `noqa`, `nolint` | keine Fundstelle; `config/detekt/detekt.yml`, `gradle.lockfile` in der Fix-Runde unverändert (`build.gradle.kts` und `tools/mk/ci.mk` änderten andere Commits des Bereichs 26b6327..HEAD) |
| Flakiness: Stage-`test`-Image, `docker run --cpus=1` bzw. `--cpus=0.25` neben 40 Endlosschleifen auf 20 Kernen, je 6 Läufe von HandshakeDriverTest, AuthExecutorTest, HandshakeTransportTest (Wanduhr: 150 ms Frist, 400 ms Block, 300 ms Frist, 3 s Adapter) | 12 von 12 Läufen grün (je 6 bei 1 CPU: 20 bis 26 s, 6 bei 0,25 CPU: 100 bis 111 s); kein Treffer |
| Mutationen | Abschnitt 3 |
| Sonden (zwei Wegwerf-Tests in Kopien, nicht im Repo) | Abschnitt 4 |

## 2. Status der Befunde

Legende: **behoben** = Code oder Text geändert und durch einen rot gesehenen Test bzw. Textstelle belegt; **ehrlich offen** = als offen geführt, mit Träger oder Entscheidung; **Rest** = behoben mit benanntem Rest.

### Review-Findings

| ID | Status | Beleg |
|---|---|---|
| F-1 (verwaiste Adapteraufrufe) | **Rest** | `AuthPool` begrenzt auf 4 Plätze, ein wartender Aufruf beginnt nach Fristablauf nie (Tests „Verwaiste Adapteraufrufe sind begrenzt …“, „Ein wartender Aufruf beginnt …“). Mutationen a3 (Begrenzung entfernt), a4 (call.cancel entfernt), a5 (Wipe in invokeOnCompletion entfernt) färben je ihren Test rot. Rest: die Begrenzung gilt je Treiber-Instanz, nicht je Pool (N-1); Drosselung und Lastaussage bleiben offen, Träger `slice-qa-sicherheit` im Slice §6 benannt |
| F-2 (Packet Size ohne Anwender) | **ehrlich offen, Text behoben** | KDoc von `AuthenticatedSession` und `LoginResponses.negotiatedPacketSize`, Zeile in `contracts/client-profile.md` und Slice §6 sagen „angekündigt, nicht angewandt“; Grenzen der Annahme getestet (a7: `<=` zu `<` färbt „Die Packet Size wird an beiden Grenzen entschieden“ rot). Ausgang „weiter offen; kein Träger-Slice“ ist ehrlich; ein Anwender fehlt weiterhin, das Register ist nicht nachgeführt (Planner-Aufgabe laut Slice §6) |
| F-3 (Abbruchausnahme des Adapters) | **geändert, Entscheidung offen** | Der Treiber schließt den Kanal bei jedem Ende außer `Ready` (Tests „Eine Abbruchausnahme des Adapters wird weitergereicht …“, „Bricht der Aufrufer ab …“; a2 färbt beide rot). Die Verifikation (M-1) empfahl die Gegenrichtung; die Fix-Runde wählte „weiterreichen und Kanal schließen“ und führt es als „weiter offen bis zur Entscheidung“. Bewertung in Abschnitt 5 |
| F-4 (expire-Zweig ungetestet, offener Punkt aus [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) nicht im Code) | **behoben** | Test „Fristablauf waehrend des Schreibens der Annahme schliesst den Kanal“; a6 (Schließen im Zweig entfernt) rot. KDoc von `HandshakeSession.expire` nennt Festlegung und offenen Punkt ([ADR-0004](../plan/adr/0004-session-zustandsautomat.md)); Slice §6 führt ihn mit Träger [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) |
| F-5 (überlebender Mutant, Matrixzelle nur über Zählung) | **behoben** | Test „Eine gueltige LOGIN7-Nutzlast in einem Paket falschen Typs loest keine Anmeldung aus“ (fünf Pakettypen, jeweils Schließen mit `PROTOCOL_VIOLATION`, Gegenprobe mit LOGIN7 → `AUTHENTICATING`). Dass der Test die Zellen fängt, habe ich nicht mit einer Zellen-Mutation neu gefahren; der Test prüft das Verhalten direkt, nicht die Zählung |
| F-6 (Importaussage zu eng) | **behoben** | Slice §3, KDoc von `HandshakeEvent`: nennen `TdsMessage`, `PacketType`, `FramingLimits`, `PacketHeader`; ein Abgleich mit den Imports von `protocol/session` stimmt |

(F-7 bis F-13 waren INFO. F-7 (Fristbeginn) ist im KDoc von `HandshakeDriver` als Abstand zu [SPEC-008](../../spec/spezifikation.md) benannt; F-12 (Kanal bei Abbruch des Aufrufers nicht geschlossen) ist mit dem Finally-Schließen behoben, a2.)

### Verifikation

| ID | Status | Beleg |
|---|---|---|
| M-1 | siehe F-3 | Entscheidung offen, nicht behoben im Sinne der Empfehlung |
| M-2 | siehe F-1 | **Rest** (N-1) |
| L-1 (Slice-Text Imports) | **behoben** | siehe F-6 |
| L-2 (Zellen LOGIN7 × PRELOGIN/ATTENTION) | **behoben** | siehe F-5 |
| L-3 (ENVCHANGE nicht als Hex) | **behoben** | Test „Die Annahme als vollstaendiger Handvektor mit den ENVCHANGE-Bytes“. Längenfelder von Hand nachgerechnet: Datenbank 1 + 1 + 2 + 1 = 5; Packet Size 1 + 1 + 8 + 1 + 8 = 19 (0x13); Collation 1 + 1 + 5 + 1 = 8; a9 (ENVCHANGE-Reihenfolge Datenbank/Packet Size getauscht) färbt drei Tests rot, darunter den Handvektor |
| L-4 (M25, M26, M30) | **behoben** | a6 (= M25), a7 (= M26), a8 (= M30: `closed()` behält `pending`) färben jeweils einen Test rot |
| L-5 (DoD-Wortlaut „kontrollierte Ablehnung“) | **behoben im Plan** | DoD-Punkt 3 trägt jetzt die Festlegung „Schließen ohne Antwort“ (cc1b007) |
| L-6 (Abdeckungsmatrix) | **ehrlich offen** | `docs/plan/spec-abdeckung.md` zeigt [SPEC-008](../../spec/spezifikation.md) weiter mit 0 Tests und „OHNE NACHWEIS“; kein Gate, DoD verlangt keine Nachführung; Beobachtung `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` führt es |
| L-7 (Cache) | **belegt** | dieser Bericht fuhr die Stage mit `--no-cache-filter test` |

## 3. Mutationen (Kopien des Baums von dc08d50, Stage `test`, je ein Lauf)

| Nr. | Mutation | Ergebnis | Rote Tests |
|---|---|---|---|
| a1 | Executor: Abbruchausnahme des Adapters nicht mehr weiterwerfen (= Ablehnung ADAPTER_FAILURE für alle Ausnahmen) | **erkannt** | genau einer: „Eine Abbruchausnahme des Adapters wird weitergereicht und der Kanal geschlossen“; die übrigen 339 Tests bleiben grün |
| a2 | Treiber: Finally-Schließen entfernt | **erkannt** | „Eine Abbruchausnahme …“, „Bricht der Aufrufer ab, ist der Kanal geschlossen“ |
| a3 | `limitedParallelism` entfernt (AuthPool-Begrenzung) | **erkannt** | „Ein wartender Aufruf beginnt …“, „Verwaiste Adapteraufrufe sind begrenzt …“ (zusätzlich ein roter Listener-Test, siehe N-4) |
| a4 | `call.cancel()` im Finally von `authenticate` entfernt | **erkannt** | „Verwaiste Adapteraufrufe sind begrenzt …“ |
| a5 | Wipe in `invokeOnCompletion` entfernt | **erkannt** | „Ein wartender Aufruf beginnt …“ |
| a6 | M25: Zwangsschließen in `expire()` entfernt | **erkannt** | „Fristablauf waehrend des Schreibens der Annahme …“ |
| a7 | M26: Packet Size `<=` zu `<` an der Obergrenze | **erkannt** | „Die Packet Size wird an beiden Grenzen entschieden“ |
| a8 | M30: `closed()` behält `pending` | **erkannt** | „Der Kontext nach dem Schliessen traegt keine Anmeldedaten …“ |
| a9 | ENVCHANGE-Reihenfolge Datenbank und Packet Size vertauscht | **erkannt** | drei Tests in LoginResponseTest, darunter der Handvektor |
| a10 | Wipe im Finally des Executors entfernt (der Wipe in `invokeOnCompletion` bleibt) | **überlebt** | keiner. Äquivalent im Verhalten: beide Wipes laufen beim Ende des Aufrufs, der zweite deckt den ersten ab. Der Wipe im Finally ist redundant |
| a11 | `session.expire()` im Fristzweig des Treibers entfernt | **erkannt** | „Fristablauf schliesst ohne Antwort“, „Ein langsamer Adapter haelt LOGIN_TIMEOUT ein“ |
| a12 | Treiber meldet das Ende immer als nicht übergeben (schließt auch bei `Ready`) | **erkannt** | vier Tests (u. a. beide Annahme-Pfade) |

Ein erster Lauf von a1 und a10 scheiterte an einem Fehler meines Ersetzungsmusters (Kompilierfehler); beide wurden mit korrigiertem Muster wiederholt, die Tabelle zeigt nur die gültigen Läufe.

## 4. Sonden (Wegwerf-Tests in Kopien)

* **Sonde 1 — Gilt die Obergrenze je Pool?** Zwei `HandshakeDriver` teilen ein `AuthPool(parallelism = 1)`, zwei Verbindungen melden sich gleichzeitig an, der Adapter schläft 300 ms und zählt die gleichzeitigen Aufrufe. Ergebnis: **2 gleichzeitige Aufrufe**. Ursache: `AuthExecutor` ruft `pool.dispatcher.limitedParallelism(pool.parallelism)`, und jeder Treiber baut seinen eigenen Executor, also je Treiber eine eigene begrenzte Sicht. Die Obergrenze gilt je Treiber-Instanz.
* **Sonde 2 — Wann ist das Passwort eines abgebrochenen, wartenden Aufrufs überschrieben?** Pool mit einem Platz, ein Aufruf hält ihn, der zweite wird eingereiht und abgebrochen; 300 ms später ist sein Passwort **nicht** überschrieben und der Aufruf nicht beendet; nach Freigabe des Platzes und `join()` ist es überschrieben (der Test der Fix-Runde belegt genau diesen Endzustand).

## 5. Entscheidung: Abbruchausnahme weiterreichen und Kanal schließen, oder Ablehnung ADAPTER_FAILURE

**Stand der Entscheidung.** Gewählt ist „weiterreichen und schließen“ (Treiber; die DoD-Zusage „Ausnahme wie Rejected“ ist für diesen Fall eingeschränkt; Grundlage [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md)). Sie ist im Slice §6 als Festlegung der Fix-Runde mit Ausgang „weiter offen bis zur Entscheidung“ geführt. Das ist formal ehrlich. Zwei Lücken bleiben:

1. **Die DoD ist nicht angepasst.** DoD-Punkt 2 sagt weiter wörtlich „eine Ausnahme im Adapter wird wie `Rejected` beantwortet und intern geloggt“; die `CancellationException` ist eine Ausnahme im Adapter und wird nicht so beantwortet. Die Abweichung steht nur in §6. Wer die DoD abhakt, hakt gegen einen Wortlaut ab, den der Code für diesen Fall nicht erfüllt. Eine der beiden Stellen muss sich bewegen (DoD-Wortlaut mit Festlegung oder Code).
2. **Die Begründung trägt nicht.** §6 sagt, bei der Ablehnung könne der Aufrufer „einen Abbruch seiner eigenen Coroutine nicht von einem Adapterfehler unterscheiden“. Der Abbruch der Aufrufer-Coroutine erreicht den Treiber aber nie über den Adapter, sondern als eigene Ausnahme beim Warten im Treiber; unterscheidbar ist er im Executor über den Zustand des eigenen Jobs. Mutation a1 belegt, dass die Gegenrichtung mechanisch möglich ist: Ohne die eine Zeile wird jede Adapter-Ausnahme zu ADAPTER_FAILURE, und alle Tests außer dem Entscheidungstest bleiben grün, auch „Bricht der Aufrufer ab“ und die Frist-Tests. Ob dabei in Randfällen (Aufrufer bricht ab, der nicht unterbrechbare Aufruf wirft danach) ein Ergebnis verloren geht, ist folgenlos, weil nach dem Abbruch niemand mehr wartet. Ich habe die zustandsprüfende Variante nicht gebaut, die Aussage stützt sich auf a1 und die Lektüre.

**Konsequenzen im Listener-Kontext (aus dem Code gelesen, nicht gefahren; der Treiber ist noch an keinen `ConnectionHandler` angeschlossen, die Folgen sind daher vorausschauend).**

* `ManagedConnection.finish` ordnet jede `CancellationException` aus dem Handler als `ConnectionEnd.SHUTDOWN` ein (`failure is CancellationException`), nicht als `HANDLER_FAILED`; im Ereignis `Closed` steht dann kein Typ des Fehlers. Ein fehlerhafter Adapter, der eine `java.util.concurrent.CancellationException` wirft (etwa über `Future.get`), sähe für den Betrieb wie ein geordnetes Beenden aus, nicht wie ein Adapterdefekt. Das untergräbt die Beobachtbarkeit des Adapterfehlers, die das Ablehnungsmuster (intern geloggt, Typ des Fehlers) für andere Ausnahmen liefert.
* Der Client bekommt statt ERROR 18456 und DONE ein stilles Schließen, dasselbe Bild wie bei `LOGIN_TIMEOUT`. Das widerspricht dem Gedanken von [SPEC-012](../../spec/spezifikation.md), dass ein abgelehnter Login ein einheitliches Muster zeigt; ein Client kann den Adapterdefekt nicht von einer Zeitüberschreitung unterscheiden.
* Der Handler (noch nicht geschrieben) muss die Ausnahme fangen oder bewusst durchlassen; trifft er die Wahl nicht, gilt die Einordnung als `SHUTDOWN`. Der Kanal ist in beiden Fällen geschlossen (Treiber und `ManagedConnection` schließen, das Schließen ist wirkungslos beim zweiten Aufruf), die Verbindung bleibt also nie offen: der eigentliche Schaden aus M-1 ist behoben.
* Für die Ablehnung spricht die Einheitlichkeit: eine Fehlerklasse, ein Antwortmuster, ein Logeintrag mit Typ. Für das Weiterreichen spricht nur, dass die Kotlin-Konvention Abbruchausnahmen nicht verschluckt; sie gilt für den Abbruch des eigenen Geltungsbereichs, nicht für eine Ausnahme aus fremdem blockierendem Code, der in einem eigenen Geltungsbereich läuft.

**Empfehlung an den Planner** (Entscheidung ist seine, ich ändere nichts): die Ablehnung ADAPTER_FAILURE für eine Abbruchausnahme, die nicht vom eigenen Abbruch stammt; zustandsprüfend im Executor, mit einem Test für beide Fälle (Adapter wirft die Ausnahme bei aktivem Job; Aufrufer bricht ab). Wird die jetzige Form beibehalten, gehören DoD-Punkt 2 und der Hinweis auf die Einordnung als `SHUTDOWN` im Listener in den Plan.

## 6. AuthPool-Festlegungen: Kennzeichnung nach §3.8 und §3.7

| Aussage | Kennzeichnung | Urteil |
|---|---|---|
| 4 Plätze auf `Dispatchers.IO` | KDoc von `AuthPool`, `AuthExecutor` und `HandshakeDriver` sowie Slice §6: „Festlegung ohne Messung“; „die richtige Zahl liegt nicht vor“ | **korrekt gekennzeichnet** |
| Keine Last-Aussage, keine Drosselung | KDoc und §6 nennen es ausdrücklich; Träger `slice-qa-sicherheit` | **korrekt gekennzeichnet** |
| „Mehr als parallelism Adapteraufrufe laufen nie zugleich“ (KDoc `AuthExecutor`); „`AuthPool` begrenzt die gleichzeitigen Aufrufe“, „Behoben ist die Zahl“ (Slice §6) | als Tatsache formuliert | **trägt weiter als der Beleg** (N-1): gemessen ist die Begrenzung je Treiber-Instanz mit einem Treiber (Test) und dagegen 2 Aufrufe bei 2 Treibern (Sonde 1) |
| „Das Passwort ist überschrieben, sobald der Aufruf endet, auch wenn er nie begann“ | wörtlich wahr, Test und Test-KDoc nennen „erst, wenn die Warteschlange ihn erreicht“ | **wörtlich korrekt**, aber der Slice-Text sagt „beginnt nie, sein Passwort wird überschrieben“ ohne diesen Zeitpunkt (N-2) |
| Kommentare nach §3.7 | die neuen KDoc-Absätze beschreiben den Ist-Zustand (Zusage, Grenze, Kopplung); kein Konjunktiv über verworfene Alternativen, keine Chronik | **konform**; einzige Beanstandung ist die breite Aussage oben |

## 7. Neue Befunde

* **N-1 — MEDIUM — Die Obergrenze verwaister Adapteraufrufe gilt je `HandshakeDriver`, nicht je `AuthPool`.** Der Executor erzeugt in seinem Konstruktor eine eigene `limitedParallelism`-Sicht; zwei Treiber mit demselben Pool laufen mit je 4 Plätzen (Sonde 1: 2 gleichzeitige Aufrufe bei Pool mit 1 Platz). KDoc und Slice §6 behaupten die Begrenzung als Eigenschaft des Pools. Solange ein Treiber alle Verbindungen bedient, stimmt es; das ist nirgends festgelegt, und ein Treiber je Verbindung höbe die Begrenzung auf. Kein Test belegt die Zusage über Treiber hinweg. Betroffen: M-2/F-1 gelten damit als **Rest**, nicht als behoben. Ausgang offen: entweder die Sicht im `AuthPool` halten (eine Sicht je Pool) oder die Aussage auf „je Treiber“ einschränken und festlegen, dass ein Treiber je Listener läuft. [AGENTS.md §3.8](../../AGENTS.md).
* **N-2 — LOW — Das Passwort eines bei Fristablauf wartenden Aufrufs bleibt, bis die Warteschlange ihn erreicht.** Sonde 2: 300 ms nach dem Abbruch bei blockiertem Pool nicht überschrieben. Bei hoher Last (alle Plätze eine Ableitung lang belegt, viele wartende Verbindungen) liegen die Klartext-Passwörter der Abgebrochenen entsprechend länger im Speicher, und die Warteschlange hält je Verbindung einen Eintrag. Der Test und das KDoc sagen es; der Plantext in §6 nicht. Eine Messung des Zeitfensters fehlt. [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit).
* **N-3 — MEDIUM (Entscheidung) — Folgen der Abbruchausnahme im Listener sind im Plan nicht genannt.** Siehe Abschnitt 5: Einordnung als `SHUTDOWN`, stilles Schließen, DoD-Wortlaut nicht angepasst.
* **N-4 — INFO — Ein Wanduhr-Test außerhalb des Slice ist unter Last einmal rot geworden.** In der Mutation a3 (drei Docker-Builds parallel plus Fremdlast des Hosts) schlug `ListenerLifecycleTest > login timeout closes a connection stuck in the handshake in both paths` mit `NoSuchElementException: Collection is empty` bei `handler.wires.single()` fehl: der Test greift auf die Verbindung des Handlers zu, bevor der Handler sie registriert hat. Der Code von a3 berührt den Listener nicht (der Treiber ist dort nicht angeschlossen), der frische Basislauf und alle 12 Lastläufe derselben Tests waren grün. Ein Einzelfund, nicht reproduziert; Gegenstand des Listener-Slice ([ADR-0003](../plan/adr/0003-transportframework.md)), hier nur gemeldet.
* **N-5 — INFO — Wanduhr-Test „Verwaiste Adapteraufrufe sind begrenzt …“ hat eine Zeitannahme.** Er verlangt, dass der erste Adapteraufruf innerhalb der Frist von 150 ms nach Start von `run` beginnt (sonst `calls == 0`). Unter meiner Last (0,25 CPU gegen 40 Schleifen) trat das nicht ein (6 von 6), aber die Annahme steht im Test nicht und ist ein möglicher Flake auf einem stark belegten Runner. Keine Änderung nötig, solange `make gates` grün bleibt; bei Rot zuerst diese Annahme prüfen.

## 8. DoD des Slice, erneut bestätigt

Der Slice trägt weiterhin keine Häkchen und §7 ist „Wird bei der Closure gefüllt“; die Prüfung betrifft deshalb die Liefer-Punkte und die Nachweis-Pflichten, die vor der Closure erfüllbar sind.

| DoD-Punkt | Bestätigung |
|---|---|
| Liefer-Punkt 1 (Zustandsautomat und PRELOGIN) | **bestätigt**, wie in der Verifikation von `bda6cd6`; neu belegt: gültiges LOGIN7 in Paketen falschen Typs, Kontext nach `closed()`, Fristablauf in `READY` |
| Liefer-Punkt 2 (Login-Antworten) | **bestätigt mit Einschränkung**: ENVCHANGE-Bytes jetzt als Handvektor gepinnt. Der Satz „eine Ausnahme im Adapter wird wie `Rejected` beantwortet“ gilt nicht für die Abbruchausnahme (Abschnitt 5, N-3) |
| Liefer-Punkt 3 (nicht unterstützte Modi, ungültige Felder) | **bestätigt**, die Festlegung „Schließen ohne Antwort“ steht jetzt im DoD-Wortlaut |
| `@spec`-Tags, Tests laufen in `make test` und sind grün | **bestätigt**: 340 PASSED im frischen Lauf; alle neuen Tests tragen `@spec` |
| Aussage = Test oder Festlegung | **bestätigt mit Einschränkung**: N-1 (Pool-Aussage breiter als der Beleg); die Zuordnung *Test | Festlegung* je Aussage gehört in die Closure-Notiz und fehlt noch |
| `make gates` grün | **bestätigt** (Exit 0; die Stage `test` zusätzlich frisch, siehe Abschnitt 1) |
| Review durchgeführt, Report liegt vor | **bestätigt**: [Review](2026-10-30-bda6cd6-slice-handshake-login.md) und [Verifikation](2026-10-30-bda6cd6-verifikation-slice-handshake-login.md); die Fix-Runde ist nicht erneut reviewt, dieser Bericht ersetzt kein Review |
| Doku-Update (öffentlicher Vertrag) | **bestätigt**: `contracts/client-profile.md` nachgeführt |
| Closure-Notiz, Lerneintrag, Beobachtungs-Register, Risiko-Ausgänge, drei Paarungen | **nicht bestätigt, noch nicht fällig**: §7 leer; die Ausgänge der Risiken sind zum Teil „offen bis zur Closure“; neu in §6 stehen drei Einträge mit „weiter offen“ (Verwaiste Aufrufe, Packet Size, Abbruchausnahme) plus einer mit Träger [ADR-0004](../plan/adr/0004-session-zustandsautomat.md). Das Register ist nicht fortgeschrieben (die Packet-Size-Lücke ist als Planner-Aufgabe genannt; ein offener Punkt hängt an [ADR-0004](../plan/adr/0004-session-zustandsautomat.md)) |

## 9. Empfehlung an den Planner

1. **Vor der Closure entscheiden:** Abbruchausnahme (Abschnitt 5); danach DoD-Punkt 2 und Slice §6 zur Entscheidung passend halten.
2. **N-1 beheben oder die Aussage einschränken** (Pool-Sicht oder „je Treiber“); bis dahin in §6 den Satz „Behoben ist die Zahl“ auf „je Treiber“ begrenzen; F-1/M-2 bleiben als Rest „weiter offen; Träger `slice-qa-sicherheit`“ geführt.
3. **N-2 in §6 aufnehmen** (Zeitpunkt des Überschreibens) oder messen.
4. In die Closure-Notiz: Zuordnung *Test | Festlegung*, a10 als äquivalenter Mutant, Packet Size ohne Anwender (kein Träger-Slice, Register nachführen), L-6 als offen.
5. N-4 an den Listener-Slice melden (Wanduhr-Test unter Last).
