# Slice slice-oeffentliche-flaeche-verkleinern-und-belegen: Öffentliche Fläche auf das Nötige verkleinern und jeden zugesagten Namen über die öffentliche Fläche belegen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose (Begründung in §6: die Telemetrie, deren Ereignismodell den größten Teil der Fläche stellt, wird in dieser Welle gebaut und von ihr weitergeführt; die Entscheidung zur Fläche steht vor deren Folge-Slices)

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) (Scope: Modulgrenzen, Tests, Dokumentation), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend) (Einbettung durch einen Integrator), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) (Telemetrie als Teil der Fläche), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (Zusage der Fläche, Nachweis), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (Ereignismodell), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (Bibliothek ist das Produkt, Testserver bindet nur die öffentliche Fläche)

**Berührte Spec-Stellen:** SPEC-059 (Tabelle „Zusage“, „Die Namen nach Package“) · SPEC-035 (nur falls `simulation`-Namen fallen) · `spezifikation.md` Abschnitte 4 und 5 (Ereignismodell) · Änderungsprotokoll

**Verantwortlich:** pt9912.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die öffentliche Fläche der Bibliothek enthält nur, was ein Einbettender zum Einsetzen der Bibliothek braucht, und jeder Name, den [SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) zusagt, ist über die öffentliche Fläche in `src/integrator` oder im Testserver (`testserver/kotlin`) belegt; ein Sensor meldet Abweichungen in beiden Richtungen.

**Nutzerentscheidung (2026-11-05, wörtlich):** „Das soll eine API sein. Also nur die Klassen, die man wirklich dafür benötigt.“ Das entscheidet die **Richtung** zu Option (c) „Fläche schrumpfen“ des Registereintrags [BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung](../observations/BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung/observation.md) (Optionen (a), (b), (c) aus §6 von [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md)) im Prinzip. Sie entscheidet **nicht**, welche konkreten Namen bleiben: Der Schnitt ist Gegenstand von Liefer-Punkt 1 und braucht die Bestätigung des Nutzers, bevor Code geändert wird. Option (c) ändert die API (Modell, Abbildung, Tests je Art, Adapter-Slice) und muss vor einem Release fallen, weil sie danach die Zusage der Typnamen bräche.

**Kriterium je Name** (aus der Nutzerentscheidung abgeleitet, Setzung dieses Plans): Ein Name bleibt öffentlich, wenn ein Einbettender ihn für eine dieser Aufgaben braucht: Konfiguration, Start und Stop, Backend-Anbindung, Anmeldung und Benutzerliste, Entgegennehmen von Telemetrie. Ist er Implementierungsdetail, wird er `internal` oder entfällt (zusammengefasst).

**Befund der Ausgangsmessung (grobe Namenssuche vom 2026-11-05, kein Aufrufbeleg, keine Zusage nach `AGENTS.md` §3.8):** `SPEC-059` nennt 67 Namen (ohne `AuthAdapter`, `ConfigKeys`). Der Testserver verwendet 31 davon, der Einbettungstest 37. Von keinem der beiden verwendet werden 23: `AcceptFailed`, `AdapterFailed`, `BindFailed`, `ConnectionFailed`, `LoginRejected`, `RequestFailed`, `StartRefused`, `TlsFirstNotOffered`, `TlsHandshakeFailed`, `TlsStartRejected`, `SimulationBackendReady`, `StartRefusal`, `LoginRejectionReason`, `TlsFirstRefusal`, `EventLevel`, `QueryParameter`, `ParameterDirection`, `RowFilter`, `FilterTerm`, `SimQuery`, `UserListError`, `UserListErrorKind`, `Principal`. Grenzen: Die Suche prüft Namen, nicht Aufrufe; Unit-Tests mit `internal`-Zugriff können diese Namen belegen, das belegt nicht die Erreichbarkeit über die öffentliche Fläche; ein Name, der nur als Typ eines Rückgabewerts vorkommt, wird nicht gefunden, ist aber erreichbar. **Die Zahlen sind vor dem Start neu zu messen** (Drift: `slice-betrieb-log-adapter-testserver`, `slice-betrieb-zustandsereignisse` und andere Slices verändern Testserver, Tests und Modell).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Verhalten des Servers (Protokoll, Anmeldung, Abbruch) — der Slice ändert Sichtbarkeit und Zuschnitt von Namen, nicht, was der Server tut; jede Verhaltensänderung wäre ein anderer Vorgang mit eigenem Beleg.
- JSON-Adapter, `LOG_LEVEL` und Inspektion des laufenden Prozesses — [slice-betrieb-log-adapter-testserver](../open/slice-betrieb-log-adapter-testserver.md); er formatiert die Arten, die dieser Slice festlegt, und fängt deshalb nach ihm an (siehe §4).
- Verdrahtungstest für `AcceptFailed` und `ConnectionFailed`, Rot-Fälle der Ausgabe-Sperre, Lücke `Logger.log` — [slice-betrieb-telemetrie-verdrahtung-belegen](../open/slice-betrieb-telemetrie-verdrahtung-belegen.md); sein Belegobjekt ändert sich mit dem Schnitt, die Reihenfolge ist in §4 gesetzt.
- Neue Ereignisarten, `request_id`, `duration_ms` — [slice-betrieb-zustandsereignisse](../open/slice-betrieb-zustandsereignisse.md) und [slice-betrieb-metriken-tracing](../open/slice-betrieb-metriken-tracing.md); hier wird nur zugeschnitten, was gebaut ist.
- Änderungen am Lastenheft — die Fläche ist Sache der Spezifikation (Rang 2) und einer möglichen ADR (§6); das Lastenheft nennt keine Namen.
- Ein Folge-Slice zur `PRELOGIN`-Serverversion (Gedanke einer Folge für JDBC/`PRELOGIN`): in `open/` und in der Roadmap gibt es keinen solchen Slice; der Slice rührt das Protokoll nicht an, ein Verweis entfällt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Schnitt bestätigt und umgesetzt** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend)): Der Implementer misst die Ausgangszahlen neu und leitet den Schnitt nach dem Kriterium aus §1 aus dem Bestand ab. Er legt dem Nutzer eine Liste **bleibt / wird `internal` / entfällt (zusammengefasst)** mit Begründung je Gruppe vor; **vor der Bestätigung ändert er keinen Code.** Hauptkandidat zum Zusammenfassen ist das Ereignismodell (18 Arten, 4 Aufzählungen): etwa eine kleine Ereignisklasse mit Art statt je Art ein öffentlicher Typ, mit Blick auf die Überlappung der vier Abbruch-Arten mit `StartOutcome.Aborted`, auf `EncryptionWeakened` und `PlainTestModeActive` als eine Art und auf `SimulationBackendReady`. Das sind Kandidaten, keine Entscheidung. Nach der Bestätigung gilt der Schnitt im Code: Namen außerhalb der Liste sind `internal` oder entfallen; Abbildung, Tests je Art und Einbettungstest sind nachgezogen.
- [x] **Sensor** (Klasse und Form offen, §6): Ein Sensor liest die Namensliste aus `SPEC-059`, die tatsächlich öffentlichen Deklarationen des Hauptmoduls (`explicitApi` Modus `strict`) und die Verwendungen in `src/integrator` und `testserver`. Er meldet (a) Namen der Liste ohne Beleg über die öffentliche Fläche, (b) öffentliche Deklarationen ohne Eintrag in der Liste, und bricht bei jedem Fund. **Beleg:** Mutation je Zweig macht ihn rot (ein zugesagter Name ohne Verwendung; eine neue öffentliche Deklaration ohne Eintrag; zusätzlich ein Fall, der grün bleiben muss). Seine Grenzen stehen in seinem Kopf und in der Closure-Notiz: Namenssuche ist kein Aufrufbeleg; im Bytecode ist `internal` öffentlich, der Sensor gilt für Kotlin-Quellen.
- [x] **Spec und Doku nachgezogen:** `SPEC-059` (Tabelle „Zusage“, „Die Namen nach Package“) nennt genau die bestätigte Liste, der Satz „ob die Felder der Fälle … Teil der Zusage sind, ist offen“ ist durch die Entscheidung ersetzt; Abschnitte 4 und 5 der Spezifikation und das KDoc der betroffenen Typen folgen; das Änderungsprotokoll nennt die Änderung. Aussagen nur so weit, wie der Sensor oder ein Test sie trägt (`AGENTS.md` §3.8).

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder neue Test trägt im KDoc den Block-Tag `@spec SPEC-059`; die Tests laufen in `make test` und sind grün.
- [x] `make gates` grün; ist der Sensor ein Gate, ist er in der Sensors-Tabelle von `harness/README.md` mit Klasse und Bindung eingetragen und `make gate-zuordnung` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: `SPEC-059` und Änderungsprotokoll (öffentlicher Vertrag berührt).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — der Eintrag `BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung` bekommt eine Evidence-Datei und seinen Ausgang; **kein Zaehler wird gesetzt**, er folgt aus den Dateien.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| Vorlage an den Nutzer (Liste bleibt / `internal` / entfällt) | neu | Schnitt nach dem Kriterium aus §1; Bestätigung vor Code (Liefer-Punkt 1) |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/` und Abbildung in `runtime` | refactor | Ereignismodell zusammenfassen oder `internal` setzen, je nach Bestätigung |
| weitere Packages des Hauptmoduls (`config`, `config.auth`, `domain`, `port`, `simulation`) | update | Sichtbarkeit der Namen ohne Beleg über die öffentliche Fläche |
| `src/integrator/` (Einbettungstest) | update | Belege der bleibenden Namen über die öffentliche Fläche |
| `src/test/kotlin/…` | update | Tests je Art folgen dem Zuschnitt; Unit-Tests mit `internal`-Zugriff belegen Verhalten, nicht Erreichbarkeit |
| Sensor (Skript unter `tools/` mit Fragment unter `tools/mk/`, Selbsttest) | neu | Vergleich Liste, Deklarationen, Verwendungen; Klasse und Eingangsliste nach [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) (§6) |
| `spec/spezifikation.md` (SPEC-059, Abschnitte 4 und 5, Änderungsprotokoll) | update | Rang 2; Zusage nennt die bestätigte Liste |
| `harness/README.md` §Sensors | update | Zeile des Sensors, falls Gate oder Werkzeug |

### Schnitt (Liefer-Punkt 1, vom Nutzer am 2026-11-06 bestätigt: „deine empfehlungen“)

Maßstab ist das Kriterium aus §1. Die Zahlen und Befehle stehen im [Messprotokoll](../../../messungen/oeffentliche-flaeche-ausgang-2026-11-06.md) (Z-Nummern), dort auch die Tabelle mit der Aufrufstelle je Typname. Ein Beleg unten ist die erste Stelle mit Namenstreffer, kein Nachweis der Wirkung (`AGENTS.md` §3.8). Kürzel der Pfade: **T** `src/testserver/kotlin`, **I** `src/integrator`, **TT** `src/testserver/test`.

**Ausgang der Neumessung (Typnamen, Messprotokoll Z1 bis Z15):** 83 öffentliche Typen im Hauptmodul; `SPEC-059` nennt 66 mit Namen, 15 weitere deckt „mit seinen Fällen“, 2 (`FilterTerm.In`, `FilterTerm.IsNull`) nennt sie nicht. 54 der 83 haben einen Namenstreffer in I oder T, 29 keinen (davon 4 nur in TT).

| Nr. | Gruppe | Namen | Entscheidung | Beleg (Aufrufstelle) |
|---|---|---|---|---|
| 1 | Start und Stop | `TdsServer`, `BackendSource` (`Custom`, `Simulation`, `None`), `StartOutcome` (`Running`, `Aborted`): 8 | bleibt | T `Main.kt:42-47, 76, 91`; I `TelemetryEmbeddingTest.kt:77-78`, `EmbeddingTest.kt:153` |
| 2 | Konfiguration | `ServerConfig`, `ListenSettings`, `LoginSettings`, `RequestSettings`, `EncryptionSetting`, `TlsKeyMaterial`, `AuthSettings`, `ConfigIssue`, `IssueKind`: 9 | bleibt | T `ConfigLoader.kt:20, 24, 54-56, 79, 100, 104, 127`; I `TelemetryEmbeddingTest.kt:50-56` (`LoginSettings`, `RequestSettings`, `ConfigIssue` nur T) |
| 3 | Konfiguration | `ConfigValidation`: 1 | wird `internal` | Aufrufer im Hauptmodul `Bootstrap.kt:87` (`start` prüft immer, Befunde als `Aborted.issues`); außerhalb nur TT `ConfigLoaderTest.kt:101, 233, 260`, die danach anders prüfen |
| 4 | Backend-Anbindung | `QueryBackend`, `QueryExecution`, `QueryRequest`, `ResultColumn`, `QueryEvent` (`ResultStart`, `Row`, `ResultEnd`, `Failure`), `SqlType` (8 Fälle): 18 | bleibt | I `EmbeddingTest.kt:207-223`, `TelemetryEmbeddingTest.kt:155-164`; T `KompatibilitaetsServer.kt:141-153`, `SimColumnTypes.kt:31-64` |
| 5 | Backend-Anbindung | `QueryParameter`, `ParameterDirection`, `QueryEvent.UpdateCount`, `QueryEvent.ReturnStatus`, `QueryEvent.OutputParameter`: 5 | bleibt; der Einbettungstest belegt sie (siehe unten) | kein Treffer in I, T, TT; `QueryParameter` steht in `QueryRequest.parameters` (`QueryRequest.kt:18`) |
| 6 | Anmeldung | `Principal`: 1 | wird `internal` | Treffer nur im Hauptmodul (`HandshakeContext.kt:13`, `UserListAuthAdapter.kt:82`, `HandshakeEvent.kt:80`, alle `internal`), in keiner öffentlichen Signatur |
| 7 | Anmeldung und Benutzerliste | `UserList` (`parse`, `plainTestModeFrom`), `UserListResult` (`Valid`, `Invalid`), `PasswordChars`: 5 | bleibt | I `EmbeddingTest.kt:127, 190`; T `ConfigLoader.kt:113, 115, 154`, `ConfigFiles.kt:87, 89` |
| 8 | Anmeldung und Benutzerliste | `UserListError`, `UserListErrorKind`: 2 | bleibt (Signaturtypen von `ConfigIssue.detail`, `ConfigIssue.kt:40`, und `UserListResult.Invalid.error`, `UserList.kt:36`); der Einbettungstest belegt sie | TT `ConfigUsersFileTest.kt:30-31, 140`; in I kein Treffer |
| 9 | Anmeldung | Mitglied `PasswordChars.reveal` | wird `internal` | Aufrufer im Hauptmodul `Bootstrap.kt:146`, `UserListAuthAdapter.kt:88`; außerhalb nur TT `ConfigUsersFileTest.kt:214`, das danach anders prüft |
| 10 | Backend-Anbindung (Simulation) | `SimTable`, `SimColumn`, `RowSource`, `SimCatalog`, `SimulationBackend`: 5 | bleibt | [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md); I `EmbeddingTest.kt:168-181`; T `Main.kt:91`, `KompatibilitaetsServer.kt:164`, `DescriptionFile.kt:8` |
| 11 | Backend-Anbindung (Zeilenquelle) | `SimQuery`, `RowFilter`, `FilterTerm` (`In`, `IsNull`): 5 | bleibt (Signaturtypen von `SimTable.rows`, `onUpdate`, `onDelete`, `SimModel.kt:23-26`); der Einbettungstest belegt sie | `SimQuery` nur TT `SimDescriptionReaderTest.kt:653`; `RowFilter`, `FilterTerm`, `In`, `IsNull` nirgends |
| 12 | Telemetrie (Rumpf) | `TelemetrySink` (`accept`, `NONE`), `TelemetryEvent`, `EventLevel`: 3 | bleibt | I `TelemetryEmbeddingTest.kt:76, 98, 147`; `EventLevel` ohne Treffer, Typ von `level` (`TelemetryEvent.kt:59`) |
| 13 | Telemetrie (Arten) | 18 Ereignisklassen, `StartRefusal`, `LoginRejectionReason`, `TlsFirstRefusal`: 21 | Option B: entfallen als öffentliche Namen (unten) | 7 Arten mit Treffer in I, 11 Arten und die 3 Aufzählungen ohne Treffer in I, T, TT |

**Summe:** Die Gruppen 1 bis 13 decken die 83 Typen (8 + 9 + 1 + 18 + 5 + 1 + 5 + 2 + 5 + 5 + 3 + 21). Nach dem Schnitt sind 61 Typen öffentlich: 83 − `ConfigValidation` − `Principal` − 21 Telemetrie-Typen + `EventKind`. Mitglieder zählt der Sensor nicht (benannte Grenze).

**Telemetrie, Option B:** `TelemetryEvent` ist die einzige öffentliche Ereignisklasse; die Art steht in der Aufzählung `EventKind` (Name Setzung). Die 18 Klassen und `StartRefusal`, `LoginRejectionReason`, `TlsFirstRefusal` sind nicht mehr öffentlich (`internal` oder Felder der einen Klasse; die Form der Felder legt die Umsetzung fest und schreibt sie in `SPEC-059`). `EncryptionWeakened` und `PlainTestModeActive` (beide `WARN`, ohne Feld) werden eine Art. Preis: Felder sind nicht mehr je Art typisiert, und ein `when` über die Art ist nicht mehr compilerseitig vollständig; jede neue Art bricht ein `when` des Adapters (Risiko aus §6). Abbildung, Tests je Art und Einbettungstest werden umgebaut.

**Prüfung vor der Umsetzung: nennt `StartOutcome.Aborted.reason` die Felder der Abbruch-Arten?** Gelesen aus dem Quelltext, nicht ausgeführt (Messprotokoll, Abschnitt „Aborted.reason gegen die Felder der Abbruch-Arten“): `StartRefused` (Feld `reason`: `StartRefusal`) hat je Konstante einen festen Text (`Bootstrap.kt:133-142`, `SimulationSetup.kt:32`); `TlsStartRejected` (`part`, `form`) nennt beide im Text (`Bootstrap.kt:113`, `ListenerConfig.kt:54`); `BindFailed` (`address`, `port`, `exceptionType`) nennt Adresse und Port und bei einem Bindefehler den Typnamen, bei belegtem Port keinen, wie das Feld `null` ist (`ListenerConfig.kt:59, 64`). Alle Felder stehen im Text, aber als Prosa: `Aborted.reason` ist ein `String`, kein Feld ist strukturiert lesbar. Folge für B: das Ereignis bleibt der einzige strukturierte Weg zu diesen Feldern.

**Beleg im Einbettungstest für die Gruppen 5, 8 und 11:** Ein Test in `src/integrator` (Block-Tag `@spec SPEC-059`) belegt sie über die öffentliche Fläche: ein Backend liest einen `QueryParameter` samt `ParameterDirection` aus `QueryRequest.parameters` und erzeugt `UpdateCount`, `ReturnStatus` und `OutputParameter`; das `onUpdate` einer `SimTable` liest den `RowFilter` (`FilterTerm.In`, `FilterTerm.IsNull`, `SimQuery` über `rows`); `UserListError` und `UserListErrorKind` belegt ein Aufruf von `UserList.parse` mit ungültigem Text.

**Sensor von Liefer-Punkt 2:** Er vergleicht Typnamen (83 vor, 61 nach dem Schnitt); Mitglieder gehören nicht dazu, das steht als Grenze im Kopf des Sensors und in der Closure-Notiz.

**ADR:** keine. Die Änderung steht in `SPEC-059` und im Änderungsprotokoll; [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) bleibt unverändert.

**Kanten, die der Schnitt nicht entscheidet:**

- [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (angenommen, noch nicht gebaut) fügt einen zweiten Konstruktor von `SimulationBackend`, den Parameter `SimTable.onInsertBatch` und das Feld `SimColumn.maxLength` hinzu, alles Mitglieder der Gruppe 10, kein neuer Typname; der Schnitt lässt diese Typen unberührt.
- Die Handle-Grenze (`slice-adr-handle-grenze-ersatz`, Entwurf auf dem Zweig gleichen Namens, nicht auf `main`) kann einen öffentlichen Namen verlangen, etwa ein Feld in `RequestSettings` (Gruppe 2) oder einen neuen Typ. Der Schnitt reserviert nichts; der Träger stimmt den Namen mit dieser Liste ab, bevor er ihn anlegt.
- Die Slices `slice-katalog-und-ddl-zustand`, `slice-schreibpfad-bulk-load` und `slice-schreibpfad-merge-output` beginnen nach dem Schnitt (§4) und fügen ihre Namen zur Liste hinzu.
- Die Umsetzung beginnt erst, wenn `main` den Abbruch-Slice (`slice-abbruch-ressourcen-shutdown`, kann Ereignisse und `TelemetryEvent` ändern) und den Modul-Umbau (`testserver/` statt `src/testserver`) enthält.

### Umsetzung (Stand des Implementers)

Gebaut nach dem Schnitt oben; was die Umsetzung dabei festlegte, ist Setzung und steht in `SPEC-059`:

- **Form der Felder (Option B):** `TelemetryEvent` bleibt eine `sealed class`; die Unterklassen je Art sind `internal`, ihre Felder stehen als nullbare Zugriffe in der Basisklasse (`port`, `tables`, `reason`, `key`, `issueKind`, `part`, `form`, `address`, `exceptionType`, `principal`, `failure`, `errorCode`, `cause`). `kind` heißt das Feld der Art, deshalb heißt die Art des Konfigurationsfehlers `issueKind`. Die Gründe (`StartRefusal`, `LoginRejectionReason`, `TlsFirstRefusal`) sind `internal` und stehen als Name in `reason`.
- **Eine Art für zwei Abschwächungen:** `SECURITY_WEAKENED` trägt `reason` (`ENCRYPTION_TEST_UNENCRYPTED`, `PLAIN_TEST_MODE`); ohne das Feld wären zwei gleiche Ereignisse nicht zu unterscheiden. Das ist eine Ergänzung gegenüber „ohne Feld“ in der Vorlage und steht zur Bestätigung aus.
- **Sensor:** Werkzeug `make flaeche`, Gate der Klasse `immer` (Begründung: seine Eingänge, Spezifikation und Quellen von drei Orten, stehen nicht vollständig in der Eingangsliste einer Klasse mit Stempel; er braucht kein Gradle und kostet bei unverändertem Eingang nur den Layer-Cache). Gegengrund: ein Stempel der Klasse `tests` mit Spezifikation und den Quellen als Eingang ließe den Lauf bei unverändertem Eingang ganz aus; dafür müssten `spec/spezifikation.md` und die Stage in die Eingangsliste und die Stage-Liste von `tests` aufgenommen werden (Änderung von `tools/stempel/tests.eingaenge`, [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)). Bei Kosten von Sekunden ist das nicht verdient; wächst der Lauf, ist es die Alternative.
- **Tests des Testservers:** `ConfigValidation` und `PasswordChars.reveal` sind für das Modul nicht mehr übersetzbar (gewollt); `ConfigLoaderTest` prüft die Bereichsgrenzen über `TdsServer.start` (Befunde als `Aborted.issues`, Loopback), `ConfigUsersFileTest` prüft den Text der Kennwortdatei über `ConfigLoader.passwordText` und nicht mehr den Inhalt von `PasswordChars`. Grenze: `passwordText` ist ein Zugang nur für den Test; dass der Text im Material ankommt, belegen die Prozesstests des TLS-Starts (Mutation „leeres `PasswordChars`“: `ServerSmokeTest` und `Tds8ProcessTest` rot, `ConfigUsersFileTest` bleibt grün; Messprotokoll).
- **Felder je Art:** über die öffentliche Fläche belegt (`TelemetryEmbeddingTest`, `TelemetryFelderEmbeddingTest`) sind die Zuordnungen, die `SPEC-059`/Abschnitt 4 dort nennt; `exceptionType` bei `ACCEPT_FAILED`, `CONNECTION_FAILED`, `ADAPTER_FAILED`, `failure`, `reason` bei `TLS_FIRST_NOT_OFFERED` und `cause` sind nur durch die Kotlin-Typen gehalten (Grenze in Abschnitt 4).
- **Belegt über die öffentliche Fläche, ohne dass Testserver oder Altbestand sie nannten:** `EventLevel` (Einbettungstest der Telemetrie), `QueryParameter`, `ParameterDirection`, `UpdateCount`, `ReturnStatus`, `OutputParameter`, `SimQuery`, `RowFilter`, `FilterTerm` mit seinen Fällen, `UserListError`, `UserListErrorKind` (`FlaecheEmbeddingTest`).

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Die Richtung ist entschieden (Nutzerentscheidung 2026-11-05, §1); ein Verantwortlicher ist eingetragen, und [slice-testserver-gradle-modul](../done/slice-testserver-gradle-modul.md) liegt in `done/` (Reihenfolge vom Nutzer am 2026-11-06 entschieden: Modul-Umbau vor dem Schnitt der Fläche; der Sensor von Liefer-Punkt 2 liest den Pfad des Moduls und baut nicht für den alten Pfad; der Schnitt steht vor [slice-katalog-und-ddl-zustand](../next/slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](../open/slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md), die Namen hinzufügen). Der konkrete Schnitt ist **kein** Start-Trigger, sondern Liefer-Punkt 1 mit Bestätigung des Nutzers vor Code. Der Slice beginnt vor einem Release und vor [slice-betrieb-log-adapter-testserver](../open/slice-betrieb-log-adapter-testserver.md) (der Adapter formatiert die Arten) und vor [slice-betrieb-telemetrie-verdrahtung-belegen](../open/slice-betrieb-telemetrie-verdrahtung-belegen.md) (seine Belegobjekte ändern sich); diese beiden Slices tragen die Kante als eigenen Start-Trigger, sobald dieser Plan angenommen ist (Nachzug beim Wechsel nach `next/`, nicht in diesem Plan).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Schnitt und Umsetzung | Sensor.
- `in-progress` → `open` (blockiert): der Nutzer bestätigt den Schnitt nicht oder verlangt Änderungen am Lastenheft; oder ein Release liegt vor der Umsetzung (dann bräche (c) die Zusage der Typnamen; neue Entscheidung nötig).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, der Sensor meldet auf dem Stand der Closure weder einen Namen der Liste ohne Beleg noch eine öffentliche Deklaration ohne Eintrag, und die Closure-Notiz trägt die Zuordnung *Test | Sensor | Festlegung* und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Offene Nutzerentscheidung: der konkrete Schnitt.** Die Richtung (c) ist am 2026-11-05 im Prinzip entschieden; welche Namen bleiben, bestätigt der Nutzer an der Liste aus Liefer-Punkt 1. — **Ausgang:** eingetreten und entschieden: der Nutzer bestätigte die Liste am 2026-11-06 („deine empfehlungen“); Stand im Code 61 Typen.
- **Braucht die Verkleinerung eine ADR?** Sie schreibt die Zusage von [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) fort (das Lastenheft bleibt unberührt); `AGENTS.md` §3.5 verbietet das Überschreiben einer `Accepted`-ADR, eine Änderung wäre eine neue ADR mit `Supersedes`, etwa als Teil-Ersatz wie bei [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md). Ob die Änderung von `SPEC-059` samt Änderungsprotokoll genügt oder eine ADR nötig ist, ist offen und mit dem Nutzer zu klären, bevor Code ändert. — **Ausgang:** entfallen: Nutzerentscheid „keine ADR für die Richtung“; `SPEC-059` und Änderungsprotokoll tragen die Änderung. Anmerkung der Verifikation (V-2): [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (`Accepted`, unverändert) sagt noch, `ConfigValidation.check` werde „vom Lader gerufen“; benannte Grenze, keine Folge-ADR.
- **Sensor: Werkzeug oder Gate, und welche Klasse.** Als Gate wäre er in `make gates` (Klassen nach [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md); braucht er den Quelltext des Produkts, gehört er zu `tests`), als Werkzeug außerhalb (wie die E2E-Läufe, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)). Ein Werkzeug ohne Gate lockert nichts, ein Gate, das später rot wird, verlangt Pflege; `AGENTS.md` §3.6 verbietet nur, ein bestehendes Gate zu lockern. Die Entscheidung trifft der Implementer mit Begründung in der Closure-Notiz, bei Zweifel der Nutzer. — **Ausgang:** entfallen, entschieden: Gate `make flaeche` der Klasse `immer` (Begründung und Gegengrund in §3, Review F-6 LOW); wird der Lauf teurer, ist ein Stempel der Klasse `tests` die Alternative.
- **Grenzen des Sensors.** Namenssuche ist kein Aufrufbeleg (ein Name kann in einem Kommentar, einer Zeichenkette oder einem toten Aufruf stehen); im Bytecode ist `internal` öffentlich; Tests mit `internal`-Zugriff zählen nicht. Der Sensor belegt „im Quelltext der Einbettenden vorkommend“, nicht „erreichbar und wirksam“. — **Ausgang:** eingetreten, benannt: Typnamen statt Mitglieder, Namenssuche ist kein Aufrufbeleg, `internal` ist im Bytecode öffentlich, Deklaration mit Umbruch nach `public` unsichtbar; im Kopf von `tools/flaeche-pruefung.sh` und in §7. Dass die Namen in wirkenden Aufrufen stehen, halten `FlaecheEmbeddingTest` und die Einbettungstests, nicht der Sensor.
- **Namen ohne Beleg, die kein Einbettender braucht, aber lesen muss.** Ein Adapter liest Felder und Konstanten zwangsläufig; wer Arten zusammenfasst, verschiebt den Wert in eine Art-Konstante und macht jede neue Art zum Bruch eines `when`. Das ist beim Schnitt zu benennen. — **Ausgang:** eingetreten, benannt: Option B verschiebt den Wert in die Art-Konstante; jede neue Art bricht ein `when` des Adapters, Felder sind nicht je Art typisiert (Preis in §3 und Abschnitt 4 der Spezifikation, Review F-8).
- **Bereits entfernter Name: `StartRefusal.JVM_FLAG_MISSING`.** [slice-tls-d-migrate](../done/slice-tls-d-migrate.md) hat die Konstante des öffentlichen Enums `StartRefusal` entfernt ([ADR-0032](../../adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md), Folgepflicht; kein veröffentlichtes Artefakt, keine Versionspolitik). Die Zeile gehört in die Liste der Änderungen an der Fläche (Änderungsverlauf von `SPEC-059`) und ist bei der Neumessung der Ausgangszahlen zu beachten; ob und wie `StartRefusal` selbst im Schnitt bleibt, entscheidet dieser Slice mit dem Nutzer, hier ist nichts vorweggenommen. — **Ausgang:** entfallen: `StartRefusal` ist `internal`; die Zeile steht im Änderungsprotokoll von `SPEC-059`.
- **Möglicher neuer Name aus der Handle-Grenze.** [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) (`Accepted` am 2026-11-06) setzt einen einstellbaren Grenzwert der Handles je Sitzung; ob er ein neuer öffentlicher Name ist oder eine bestehende Konfiguration trägt, ist dort offen (Offen 7) und wird in der Spezifikation mit diesem Slice abgestimmt; den Namen baut [slice-handle-freigabe-und-grenze-bauen](../open/slice-handle-freigabe-und-grenze-bauen.md), der nach diesem Slice startet. Der Slice berücksichtigt den Namen in seiner Liste. — **Ausgang:** weiter offen: der Schnitt reserviert keinen Namen; [slice-handle-freigabe-und-grenze-bauen](../open/slice-handle-freigabe-und-grenze-bauen.md) stimmt Name und Ort mit der Liste ab, ein neuer öffentlicher Name bricht `make flaeche`, bis `SPEC-059` ihn nennt.
- **Welle.** Der Slice steht in `welle-betrieb-diagnose`, weil dort das Ereignismodell gebaut wird und die Folge-Slices von ihm abhängen; die Closure der Welle (alle Slices in `done/`) hängt damit an diesem Slice. Passt der Nutzer den Schnitt der Wellen an, ist „ohne Welle“ (Closure-Bedingung gleich der DoD) die Alternative. — **Ausgang:** entfallen: der Slice blieb in `welle-betrieb-diagnose`; die Welle-Closure hängt an den übrigen Slices der Welle.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

- **Gegenstand:** geliefert. Commits: Umsetzung `7387fedd`, `502bafb7`, `dc7bd467`, `f9eda4ef`; Merge der Vorlage `5247c043`; Review `98128e9d`; Nachbesserung `abd0a3f9`, `464821e4`; Verifikation `4534eced`; V-1 behoben `4bc82b15`; Merge von `main` (E2E-Slice mit Stufe 8, Mitschnitt-Slice, Prüfpunkte) `706ce956`. Messprotokoll: [oeffentliche-flaeche-ausgang-2026-11-06](../../../messungen/oeffentliche-flaeche-ausgang-2026-11-06.md).
- **Zuordnung Test | Sensor | Festlegung:** *Sensor* (`make flaeche`, `tools/flaeche-pruefung.sh`, Selbsttest 14 Fälle): die 61 Typnamen der Zeile „Zusage“ von `SPEC-059` sind öffentlich deklariert und in `src/integrator` oder `testserver/kotlin` verwendet; jede öffentliche Typdeklaration steht in der Liste. *Test* (`FlaecheEmbeddingTest`, `TelemetryEmbeddingTest`, `TelemetryFelderEmbeddingTest`, `TelemetryEventTest`): die Namen stehen in wirkenden Aufrufen, jede Art gehört zu genau einer Klasse, die Felder, die Abschnitt 4 dort nennt. *Festlegung* (nur Text, ohne Sensor und Test): `exceptionType` bei `ACCEPT_FAILED`, `CONNECTION_FAILED`, `ADAPTER_FAILED`, `failure`, `reason` bei `TLS_FIRST_NOT_OFFERED` und `cause` (nur durch Kotlin-Typen gehalten, Grenze in Abschnitt 4), die Wertemengen der Marken, die Mitglieder der Typen.
- **Was hat funktioniert:** (1) Die Liste der Bestätigung (bleibt / `internal` / entfällt) vor jeder Codeänderung: 83 → 61 Typen ohne Rücknahme. (2) Der Sensor mit einer Mutation je Zweig und vier Fällen, die grün bleiben müssen; Review und Verifikation fuhren die Mutationen unabhängig (`Principal` wieder `public`, `ExtraEvent`: Exit 1). (3) Die Modulgrenze von `testserver/` machte die Übersetzbarkeit zum Beleg: `ConfigValidation` und `PasswordChars.reveal` sind für den Testserver nicht mehr nutzbar, die Tests gehen über `TdsServer.start`. (4) Nach dem Merge von `main` (E2E-Slice, `testserver/kotlin/.../EigenesBackendServer.kt`) zählt der Sensor die neuen Dateien als Beleg (61 Namen, Exit 0), und kein jetzt `internal` Name steht im neuen Testserver-Code: `make test` Exit 0.
- **Was ging anders als geplant:** (1) Option B brauchte eine Ergänzung gegenüber der Vorlage: `SECURITY_WEAKENED` trägt `reason`, sonst wären zwei Abschwächungen zwei gleiche Ereignisse (Nutzerpunkt 1). (2) Der Name der Art des Konfigurationsfehlers ist `issueKind`, weil `kind` das Feld der Art ist. (3) Die Zusage der Felder je Art war in Abschnitt 4 breiter als Test und Sensor (Review F-2); behoben durch Einschränkung und `TelemetryFelderEmbeddingTest`. (4) Der Plan nannte die zweiten Fundstellen nicht (siehe Lerneintrag).
- **Kosten von Option B (benannt, nicht behoben):** Felder sind nicht je Art typisiert (Textfelder, nullbare Zugriffe in der Basisklasse, vier Wertemengen in `reason`); ein `when` über `kind` ist als Enum vollständig, prüft aber nicht, welche Felder die Art füllt; jede neue Art bricht ein `when` des Adapters; ein neues Feld ist ein Mitglied der einen öffentlichen Klasse und ändert `SPEC-059`; `Aborted.reason` nennt die Felder der Abbruch-Arten nur als Text, das Ereignis ist der einzige strukturierte Weg zu ihnen (Review F-8, Abschnitt 4).
- **Review und Verifikation:** Review `98128e9d` (Stand `f9eda4ef`; 0 HIGH, 2 MEDIUM F-1, F-2, 3 LOW F-3 bis F-5 und F-6, 3 INFO F-7 bis F-9): F-1, F-2, F-4, F-5, F-6 behoben in `abd0a3f9`, `464821e4` (F-6 als Gegengrund in §3); F-3 als benannte Grenze (Naht `passwordText` bleibt); F-7 bis F-9 INFO, als Nutzerpunkte und Grenzen unten geführt. Verifikation `4534eced`: 0 DoD-Verletzung, 0 Entscheidungsverletzung; V-1 (LOW, `ConfigValidation.check` im KDoc des Lader) behoben in `4bc82b15`; V-2 (INFO) [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) sagt, `ConfigValidation.check` werde „vom Lader gerufen“, was nicht mehr gilt: die ADR ist `Accepted` und bleibt unverändert (`AGENTS.md` §3.5), **benannte Grenze statt Folge-ADR** (Nutzerentscheid „keine ADR für die Richtung“). Berichte: `docs/reviews/2026-11-06-f9eda4e-review-slice-oeffentliche-flaeche-verkleinern-und-belegen.md`, `docs/reviews/2026-11-06-464821e-verifikation-slice-oeffentliche-flaeche-verkleinern-und-belegen.md`.
- **Gemessen** (Einzelmessungen am 2026-11-06, ein Host, Last nicht konstant, kein Beleg für andere Hosts): Verifikation am Stand `464821e4`: `make flaeche` (zusätzlich `docker build --no-cache --target flaeche`) 14 Fälle ok, 61 Namen; Frischbeleg `make clean-build` Exit 0, `--no-cache`, 978 Zeilen `PASSED`; `make e2e` Exit 0 mit **sieben** Stufen (die achte liegt erst nach dem Merge von `main` vor und ist hier nicht gefahren); Mutationen: `Principal` public und `ServerStopped` mit falscher Art (rot im `integratorTest`, Unit-Test nicht erreicht), `AcceptFailed` mit falscher Art (Unit-Test allein rot), `BindFailed.address` fest (Einbettungstest rot). Nach dem Merge von `main` (Closure): `make flaeche` Exit 0 (14 Fälle ok, 61 Namen, `testserver/kotlin` einschließlich `EigenesBackendServer.kt` zählt als Beleg); `make test` Exit 0, 14 Gradle-Tasks ausgeführt; Frischbeleg `make clean-build` Exit 0 (`--no-cache`, 980 Zeilen `PASSED`, 0 `FAILED`; der eine Treffer des Wortes ist der Testname `FAILED_CLOSE_CONNECTION`); `make docs-check` 992 Dateien, 0 Befunde; `make gates` Exit 0, die Gruppen `werkzeug`, `tests`, `stempel` bei passendem Stempel übersprungen (**Cache-Falle G1:** der Lauf belegt für `tests` den Stempel, nicht frische Gradle-Arbeit; der Frischbeleg ist `make clean-build`), gelaufen sind `docs-check`, `gate-zuordnung` (16 Gates, 4 `immer`), Selbsttest 33 Fälle, `stempel-eingaenge` und `flaeche`. Nicht gefahren: `make e2e` mit der achten Stufe nach dem Merge (kein Gate; der Sensor und die Modulübersetzung des neuen Testserver-Codes sind grün).
- **Nutzerpunkte (festgehalten, Empfehlung „bestätigen“, nicht vom Planner entschieden):** (1) `SECURITY_WEAKENED` mit Feld `reason` (Ergänzung gegenüber „ohne Feld“); (2) die Namen `EventKind` und `issueKind` (Setzung, in `SPEC-059` so gekennzeichnet); (3) die Felder je Art sind in `SPEC-059` nur durch Test gehalten, `exceptionType` bei `ACCEPT_FAILED`, `CONNECTION_FAILED`, `ADAPTER_FAILED`, `failure`, `reason` bei `TLS_FIRST_NOT_OFFERED` und `cause` nicht von außen auslösbar, nur durch Kotlin-Typen gehalten; (4) die Handle-Grenze reserviert keinen Namen (Abstimmung beim Bau-Slice); (5) Namenssuche ist kein Aufrufbeleg (Grenze des Sensors).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-oeffentliche-flaeche-verkleinern-und-belegen.md` ergänzt in [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md) (F-1, F-4, F-5, V-1; verkörpert, Datei 19), [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (F-2, F-9; verkörpert), [BEO-TDS/test-naht-im-produktivcode](../observations/BEO-TDS/test-naht-im-produktivcode/observation.md) (F-3; 2 Dateien, offen) und [BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung](../observations/BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung/observation.md) (2 Dateien, Ausgang `gestrichen`: die Fläche ist entschieden und umgesetzt). Neu angelegt: [BEO-TDS/test-reihenfolge-verdeckt-nachgeordneten-unit-test](../observations/BEO-TDS/test-reihenfolge-verdeckt-nachgeordneten-unit-test/observation.md) (Review und Verifikation, 1×, offen).
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; der Sensor `make flaeche` ist Liefergegenstand und trägt keinen Herkunfts-Anker. Gelernt: (1) Die Fläche zu verkleinern ist ein Umbau mit **zweiter-Fundstellen-Streuung**: Der Review fand sie in Nutzer-Doku, offenen Slices und Welle, und der Review-Nachzug ließ eine im KDoc des Testservers stehen (V-1), weil er nur den im Finding genannten Pfad abgrub; die Regel im Reviewer-Skill gilt, der Nachzug muss sie auf das Muster des Findings über das ganze Repo anwenden — gezählt in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (verkörpert). (2) Eine Zusage der Spezifikation über Feldbelegung, die nur ein Teil der Tests hält, ist breiter als ihr Beleg; behoben wird durch Einschränkung mit benannter Grenze und einen Test über die öffentliche Fläche — `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert). (3) Eine Test-Naht im Produktcode (`passwordText`) schwächt die Aussage des Tests auf die Hilfsfunktion, das Gegenstück halten Prozesstests — `BEO-TDS/test-naht-im-produktivcode` (2×, offen). (4) **Neue Klasse:** `make test` bricht am ersten roten Task ab; ein Unit-Test hinter einem roten `integratorTest` kommt nicht zur Ausführung, eine Mutation „fängt“ ihn erst, wenn der Einbettungstest sie nicht auslöst — `BEO-TDS/test-reihenfolge-verdeckt-nachgeordneten-unit-test` (1×). (5) Spec-Lücke benannt, nicht geschlossen: die Zeile „von `TdsServer.start` und vom Lader gerufen“ in [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (V-2) bleibt als Grenze. Das Register ist die Quelle; keine Klasse erreichte hier erstmals 3×.
- **Risiken aus §6:** je Zeile mit Ausgang eingetragen (Schnitt entschieden, ADR entfallen, Sensor als Gate `immer` entschieden, Grenzen des Sensors und Preis von Option B eingetreten und benannt, `JVM_FLAG_MISSING` entfallen, Handle-Grenze weiter offen, Welle entfallen).
- **Folge-Slices:** [slice-katalog-und-ddl-zustand](../next/slice-katalog-und-ddl-zustand.md) (Start-Trigger „Schnitt der Fläche in `done/`“ erfüllt (die beiden anderen Bedingungen liegen schon in `done/`); fügt zweiten Konstruktor von `SimulationBackend` und `SimColumn.maxLength` hinzu, Mitglieder, die der Sensor nicht sieht), [slice-schreibpfad-bulk-load](../open/slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md) (jeweils über die Kette), [slice-handle-freigabe-und-grenze-bauen](../open/slice-handle-freigabe-und-grenze-bauen.md) (Start-Trigger „Schnitt der Fläche in `done/`“ erfüllt; Name der Grenze mit der Liste abstimmen), [slice-betrieb-log-adapter-testserver](../open/slice-betrieb-log-adapter-testserver.md) und [slice-betrieb-telemetrie-verdrahtung-belegen](../open/slice-betrieb-telemetrie-verdrahtung-belegen.md) (formatieren und belegen die Arten dieses Schnitts). Nichts gestartet.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-betrieb-diagnose](../welle-betrieb-diagnose.md). Anker ↔ Folge-Slice: kein Anker gesetzt. Register ↔ Closure-Notiz: alle genannten Kennungen existieren als Verzeichnis mit nicht leerem `evidence/` (geprüft am Dateisystem). Folge-Slices: alle existieren als Datei im Planning-Lifecycle.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Hauptmodul, Source-Set `integrator`, Modul `testserver`, Werkzeuge und Spezifikation, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung` (Gegenstand dieses Slice; Richtung entschieden, Schnitt offen), `BEO-TDS/aussagen-ueber-messung-hinaus` (die Zusage der Liste trägt nur so weit wie der Sensor; verkörpert), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (Mutation je Zweig des Sensors; verkörpert). Zähler nicht gesetzt; sie folgen aus den Dateien im Register.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
