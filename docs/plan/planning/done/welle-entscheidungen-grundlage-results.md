# Welle welle-entscheidungen-grundlage — Offene Architekturentscheidungen treffen — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-entscheidungen-grundlage
**Abschluss:** 2026-10-30
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

- Die neun Entscheidungen [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) bis [ADR-0009](../../adr/0009-authentifizierungsmodus.md) stehen auf `Accepted`, je mit einem Nachweis aus einem Spike (Docker, ohne Mount, Spike-Code im Scratchpad) und einer Wahl des Nutzers; der ADR-Index trägt den Stand. Betroffen sind [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) bis [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) und [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität).

| ADR | Slice | Entscheidung (Wahl des Nutzers, 2026-10-29 und 2026-10-30) |
|---|---|---|
| [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) | `slice-adr-clientprofil` | ausgehandeltes Profil: `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10, TDS 7.4 und TDS 8.0 |
| [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) | `slice-adr-tls` | profilabhängige Modi, Standard `REQUIRED`; unverschlüsselter Testmodus nur durch ausdrückliche Konfiguration |
| [ADR-0003](../../adr/0003-transportframework.md) | `slice-adr-transport` | Kotlin-Coroutines mit `AsynchronousSocketChannel` und eigener `SSLEngine`-Schleife |
| [ADR-0004](../../adr/0004-session-zustandsautomat.md) | `slice-adr-session-zustand` | expliziter Zustandsautomat, `Mutex` je Sitzung |
| [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) | `slice-adr-rpc` | RPC-Mindestkatalog (`sp_executesql`, `sp_prepexec`, `sp_execute`) nach Clientprofil |
| [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) | `slice-adr-backend-streaming` | `Flow` von Ereignissen mit Vorschau für `DONE_MORE`; kleine Ergebnisse dürfen eine fertige Liste sein |
| [ADR-0007](../../adr/0007-fehler-und-attention.md) | `slice-adr-fehler-attention` | enge Form von Option B mit A als Grundregel: offen nur bei Class ≤ 19 mit vollständigem ERROR und DONE sowie nach Cancel oder Timeout mit DONE[ATTN], sonst schließt der Server |
| [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) | `slice-adr-kompatibilitaetstests` | containerisierte Referenztests (Pflicht-Matrix) plus Referenzabgleich gegen einen echten SQL Server als eigenes Make-Target |
| [ADR-0009](../../adr/0009-authentifizierungsmodus.md) | `slice-adr-authentifizierung` | SQL-Authentifizierung über eine Adapter-Schnittstelle, Einstiegsimplementierung mit fester Benutzerliste |

**Das Mehr gegenüber den Slice-DoDs (Welle-Datei §3): alle neun ADRs zugleich `Accepted` und im Index.** Die Zuordnung *gemessen | Setzung | nicht gemessen* führt die Aussagen der Welle in vier Stärken; *gemessen* heißt am Spike-Testserver oder am echten SQL Server in TDS 7.4 ohne Pool beobachtet, wo die ADR es sagt.

| Stärke | Aussagen | Beleg im Repo |
|---|---|---|
| gemessen | Messzeilen der Spikes je ADR (Matrix, Zufallsfolgen, Wettläufe, Referenzclient in drei Verbindungsarten, Heap und Dauer bei großen Ergebnissen, Fehler- und Cancel-Szenarien, Ableitungsdauer); die Fehler- und Cancel-Szenarien der [ADR-0007](../../adr/0007-fehler-und-attention.md) zusätzlich an einem echten SQL Server 2022 | Messtabellen in den ADRs |
| Setzung des Nutzers | die Wahl der Option je ADR; Class-Schwelle ab 20 auch für 21 bis 24, Verbindungsende nach abgelehntem Login, Class 16 und 20 der eigenen Codes, Schließen ohne ERROR und DONE bei nicht abschließbarem Strom, `deadlineEpochMillis` im Vertrag, Iterationszahlen und Parserregeln der Benutzerliste | in den ADRs als Setzung gekennzeichnet; Detailsetzungen als „Voreinstellung“ |
| nicht gemessen | ein Server dieses Repos (es gibt keinen), TDS 8.0 (überwiegend), Pooling, ein echter SQL Server für unzulässige Übergänge, ATTENTION im Leerlauf und `DONE_MORE`, ein echter Treiber hinter dem Backend-Vertrag | „Nicht gemessen“-Abschnitte der ADRs; Beobachtungen im Register |
| nicht nachprüfbar | die Spike-Messungen von [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../../adr/0007-fehler-und-attention.md) und [ADR-0011](../../adr/0011-pruefwertformat.md): die Verifikation wiederholte sie nicht, weil kein Spike-Code im Repo liegt | Verifikationsbericht; die Zählung der Matrix in [ADR-0004](../../adr/0004-session-zustandsautomat.md) ist deshalb als nicht belegt gekennzeichnet |

Zwei Entscheidungen standen neben der Welle: [ADR-0010](../../adr/0010-detekt-pre-release.md) (§6 der Welle-Datei schloss sie aus) ist `Accepted` und gehört zur Qualitäts-Welle: sie ist Bezug von `slice-qa-wartbarkeit` und `slice-qa-reproduzierbarkeit` (beide `welle-qualitaet-grundlage`), und die Welle-Datei nennt sie jetzt dort. [ADR-0011](../../adr/0011-pruefwertformat.md) entstand im Slice `slice-adr-pruefwertformat` der Welle `welle-handshake-login` und ist `Accepted`; sie ergänzt [ADR-0009](../../adr/0009-authentifizierungsmodus.md) ohne `Supersedes`.

**Jeder in Welle-Datei §6 ausgeschlossene Gegenstand hat einen Träger als Datei** (Closure-Trigger, Prüfung am 2026-10-30):

| Ausgeschlossen | Träger |
|---|---|
| Produktionscode der gewählten Optionen | die funktionalen Wellen `welle-transport-tls`, `welle-handshake-login`, `welle-backend-streaming`, `welle-fehler-abbruch` und `welle-anfrageverarbeitung` (Dateien unter `docs/plan/planning/`) |
| [ADR-0010](../../adr/0010-detekt-pre-release.md) | `slice-qa-wartbarkeit` und `slice-qa-reproduzierbarkeit` (`welle-qualitaet-grundlage`) |
| Neue Anforderungen oder Änderungen am Lastenheft | kein Träger nötig: `spec/lastenheft.md` ist seit der Planung der Welle unverändert (`git log` über den Bereich leer) |

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- Ein Slice je Entscheidung mit Spike vor der Wahl: Der Nutzer sah erst die Messwerte, dann die Optionen, und die ADR führt Wahl und Messung getrennt (Wahl als Setzung, Messung als Messzeile).
- Review, Re-Review und Verifikation in frischem Kontext fanden, was der Autor nicht sah: Aussagen breiter als die Messung (elfmal im Register), Spec-Stellen ohne Setzungsmarke, einen Abhängigkeitszirkel zwischen [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md), den die Annahmereihenfolge auflöste.
- Eine Folge-ADR statt Überschreiben: [ADR-0011](../../adr/0011-pruefwertformat.md) schärft [ADR-0009](../../adr/0009-authentifizierungsmodus.md), ohne sie anzufassen (Hard Rule 3.5).
- Die Trennung der Messobjekte in [ADR-0007](../../adr/0007-fehler-und-attention.md) (echter SQL Server gegen Testserver) machte sichtbar, welche Aussage woher kommt.
- Die Prüfung der offenen Punkte gegen den Inhalt der Slice-Dateien (nicht gegen deren Namen) fand Lücken, die kein Review sieht, weil der Review die ADR prüft und nicht die Slices.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile moeglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- Offene Punkte der Entscheidungen hatten nach der Annahme keinen Träger oder keinen Entscheider (fünf Slices betroffen). Konsequenz: je Punkt ein Träger in den DoD der Slices der funktionalen Wellen, der Folge-Slice `slice-adr-backend-grenzen` für fünf Punkte von [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), und die Regel unten.
- [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) stand `Accepted`, bevor der Review lief, und wurde per Nutzerentscheidung auf `Proposed` zurückgestuft und nach Verifikation und Re-Review wieder angenommen. Konsequenz: keine Regeländerung; der Weg `Accepted` → `Proposed` → `Accepted` bleibt als Finding F-14 des Re-Reviews beim Architect.
- Zwei Slices (`slice-adr-fehler-attention`, `slice-adr-pruefwertformat`) lagen in `open/`, obwohl an ihnen gearbeitet wurde (Verifikation V-25). Konsequenz: `Verantwortlich` gesetzt und die Übergänge als reine Moves nachgeholt; die Arbeit lief davor.
- Der Spike-Code liegt nicht im Repo und ist nach den Entscheidungen nicht gelöscht, sondern liegt im Scratchpad der Sitzung außerhalb des Repos; die DoD-Zeilen belegen deshalb nur „nicht im Repo“. Die Messungen sind damit weder im Repo nachprüfbar noch in der Sitzung gesichert. Konsequenz: die Zählung der Spike-Matrix in [ADR-0004](../../adr/0004-session-zustandsautomat.md) ist als nicht belegt gekennzeichnet; offen ist die Frage an den Nutzer, ob Spike-Code oder Rohlogs archiviert werden (nicht entschieden, keine Regel geschrieben).
- Die Entscheidung zu [ADR-0011](../../adr/0011-pruefwertformat.md) entstand in einer anderen Welle (`welle-handshake-login`) und wird mit ihr archiviert, nicht mit dieser.

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (Feld
und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der Backticks).

Vier Einträge haben die Schwelle erreicht (`aussagen-ueber-messung-hinaus` 11×, `messung-am-echten-sql-server-fehlt` 6×, `tds-8-0-und-pooling-nicht-gemessen` 6×, `offene-punkte-ohne-traeger` 5×); `anmeldepfad-sicherheit-nicht-gebaut` steht mit 2× und fünf weitere mit 1× darunter und werden hier nicht gelesen.

- **Workflow-Command** ergänzt: Ein Entscheidungs-Slice, dessen ADR `Accepted` offene Punkte führt, schließt erst, wenn jeder Punkt einen Träger hat, der als Datei existiert und den Punkt in DoD oder Risiko führt (geprüft gegen den Inhalt, nicht den Namen); „Nutzerentscheidung“ ist kein Träger; sonst steht in der Closure-Notiz „Folge-ADR nötig“ mit dem Re-Evaluierungs-Trigger.
  — liegt in `.claude/commands/implement-slice.md`.
  Auslöser: `BEO-TDS/offene-punkte-ohne-traeger` (`slice-adr-rpc`, `slice-adr-session-zustand`, `slice-adr-backend-streaming`, `slice-adr-fehler-attention`, `slice-adr-pruefwertformat` — 5×).
- **Regel bereits verkörpert, hier nicht erneut:** `BEO-TDS/aussagen-ueber-messung-hinaus` (11×) trägt seit `welle-protokollcodec` `AGENTS.md` §3.8 und den Reviewer-Skill; die vier neuen Funde dieser Closure (`slice-adr-session-zustand`, `slice-adr-backend-streaming`, `slice-adr-fehler-attention`, `slice-adr-pruefwertformat`) hat der Review mit eben dieser Regel gefunden.
- **Messlücke** mit Träger statt Regel, Stand geprüft und unverändert: Antworten eines echten SQL Servers (Tokenreihenfolge, DONE-Statusbits, RETURNVALUE-Zählung, `DONE_MORE`). Ausgang `geplant`, Träger `slice-qa-reproduzierbarkeit` (Vergleichslauf D, DoD-Punkt 3). Neu in dieser Welle belegt, aber im Träger nicht genannt: unzulässige Übergänge, ATTENTION im Leerlauf, Class 21 bis 24 und Herunterfahren; der Stand in `state.md` sagt es.
  Auslöser: `BEO-TDS/messung-am-echten-sql-server-fehlt` (`slice-adr-authentifizierung`, `slice-adr-rpc`, `slice-codec-tds-paket`, `slice-adr-session-zustand`, `slice-adr-backend-streaming`, `slice-adr-fehler-attention` — 6×).
- **Messlücke** mit Träger statt Regel, Stand geprüft und unverändert: TDS 8.0, Pooling (`RESETCONNECTION`), mehrpaketiges LOGIN7. Ausgang `geplant`, Träger `slice-qa-kompatibilitaet` (DoD-Punkt 3).
  Auslöser: `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (`slice-adr-authentifizierung`, `slice-adr-rpc`, `slice-codec-tds-paket`, `slice-adr-session-zustand`, `slice-adr-backend-streaming`, `slice-adr-fehler-attention` — 6×).
- **Spec-Lücken** benannt, offen, unter der Schwelle (in den Slice-Closures geführt, kein `liegt in`, weil in einer versionierten Spec und nicht an einem Zielort): `SPEC-001` legt den Zuschnitt der Zustandsaufzählung nicht fest (Entscheider `slice-handshake-login`); `SPEC-002` führt `deadlineEpochMillis`, nicht aber, wer die Frist durchsetzt (Entscheider `slice-adr-backend-grenzen`); `SPEC-016` nennt „Fehler/Disconnect“ für `TDS_TIMEOUT`, ohne dass entschieden ist, ob Cancel und Timeout Fehler sind (Entscheider `slice-attention-cancel-timeout`); `SPEC-051` führt Einzelfälle, deren Deckung durch das Lastenheft nicht belegt ist (`BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, 1×).

## Beobachtungs-Register (Zeiger)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register — der Zähler wird **nicht** hier gepflegt; diese
Sektion ist ein Zeiger und trägt keine Daten.

Der Zähler steht in [`../observations/`](../observations/).
Was in dieser Welle **3×** erreicht hat, steht oben unter
*Steering-Loop-Einträge*.

## Folge-Slices

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — **derivativ**: Diese Liste zeigt nur,
das Original ist die Slice-Datei. Jeder genannte Folge-Slice muss als Datei im
Planning-Lifecycle existieren; genannt ohne angelegt ist dieselbe Klasse wie
ein halluziniertes Gate.

- `slice-adr-backend-grenzen` (Grenzen des Backend-Vertrags: Zeilendarstellung, kleine Ergebnisse, `OutOfMemoryError`, blockierendes Backend, Frist; Entscheidung mit dem Nutzer, ohne Welle) — angelegt in `slice-adr-backend-streaming`.
- Träger, die die offenen Punkte der Entscheidungen tragen und als Dateien existieren: `slice-handshake-login`, `slice-attention-cancel-timeout`, `slice-fehler-zustandsuebergang`, `slice-fehler-mapping`, `slice-abbruch-ressourcen-shutdown`, `slice-sql-batch-dispatch`, `slice-backend-port-vertrag`, `slice-backend-sequenzierer`, `slice-backend-streaming-nachweis`, `slice-auth-adapter-benutzerliste`, `slice-bootstrap-konfiguration`, `slice-qa-sicherheit`, `slice-qa-synchronitaet`, `slice-qa-kompatibilitaet`, `slice-qa-reproduzierbarkeit`.

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl).

- Alle Slices der Welle in `done/`: die neun Slices `slice-adr-clientprofil`, `slice-adr-tls`, `slice-adr-transport`, `slice-adr-session-zustand`, `slice-adr-rpc`, `slice-adr-backend-streaming`, `slice-adr-fehler-attention`, `slice-adr-kompatibilitaetstests` und `slice-adr-authentifizierung` liegen in `docs/plan/planning/done/` (je Datei mit `Welle: welle-entscheidungen-grundlage`; `ls` und `grep` am 2026-10-30).
- Alle neun ADRs `Accepted` und im Index: `docs/plan/adr/README.md` trägt die Zeilen [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) bis [ADR-0009](../../adr/0009-authentifizierungsmodus.md) mit `Accepted`; ebenso [ADR-0010](../../adr/0010-detekt-pre-release.md) und [ADR-0011](../../adr/0011-pruefwertformat.md) (`grep -c "| Accepted |"` ergibt 11).
- `make gates` grün: @GATES@
- Trigger-Audit: **Carveouts** 0 offen (`docs/plan/carveouts/` trägt nur `.gitkeep`; keine Kennung `CO-` im Bestand). **Bootstrap-aware Gates** 0 offen (kein Make-Target trägt eine Schwelle oder Reifestufe; die Bindungsspalte von `harness/README.md` führt dafür nur Vorlagenzeilen). **ADR-Re-Evaluierungs-Trigger** der elf angenommenen ADRs:
  - [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) (neuer unterstützter Client, fehlgeschlagener Handshake im Profil): nicht eingetreten; kein Client kam hinzu, kein Handshake ist gebaut (`slice-handshake-login` in `open/`).
  - [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) (neuer Verschlüsselungsdefault, neue Sicherheitsrichtlinie): nicht eingetreten; der Testmodus-Schalter der Benutzerliste ([ADR-0011](../../adr/0011-pruefwertformat.md)) ist unabhängig vom Schalter der Verschlüsselung.
  - [ADR-0003](../../adr/0003-transportframework.md) (Leaks, unzureichende Backpressure, neue JVM-Anforderungen): nicht nachgewiesen. Der Spike zu [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) maß ein Backend, das bei einem langsamen Leser 53 755 Zeilen vorauslief; die `Broken pipe`-Meldungen bei harten Abbrüchen im Spike von [ADR-0004](../../adr/0004-session-zustandsautomat.md) sind nicht analysiert. Wächter: `slice-abbruch-ressourcen-shutdown` (DoD-Punkt 1, unbehandelte Ausnahmen bei hartem Abbruch) und `slice-auth-adapter-benutzerliste` §6 (blockierende Ableitung im Transport).
  - [ADR-0004](../../adr/0004-session-zustandsautomat.md) (neue Nachrichtentypen oder Session-Races, Schreibwettlauf auf dem echten Transport, widersprechende Messung am echten Server): nicht eingetreten; Wächter `slice-fehler-zustandsuebergang` §6 (Mutex) und `slice-qa-reproduzierbarkeit`; für unzulässige Übergänge am echten Server ist keine Messung geplant (siehe Steering-Loop-Einträge).
  - [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) (neuer Aufruftyp, abweichende Antwort eines echten Servers): nicht eingetreten; Wächter `slice-qa-kompatibilitaet` und `slice-qa-reproduzierbarkeit`.
  - [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) (Backpressure-Probleme, blockierender Treiber, widersprechende Messung zu `DONE_MORE`): nicht eingetreten; Wächter `slice-adr-backend-grenzen`, `slice-backend-streaming-nachweis` und `slice-qa-reproduzierbarkeit`.
  - [ADR-0007](../../adr/0007-fehler-und-attention.md) (Recovery-Lücke, Client mit Pool scheitert, Messung an einem Server dieses Repos oder mit TDS 8.0): nicht eingetreten; Wächter `slice-qa-synchronitaet` und `slice-qa-kompatibilitaet`.
  - [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) (neue SqlClient-Hauptversion, geänderte EULA, SQL-Server-Image mit TDS 8.0): nicht eingetreten; die Spikes zu [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md) nennen `Microsoft.Data.SqlClient` 7.1.1, die Version des Profils. Wächter `slice-qa-reproduzierbarkeit`.
  - [ADR-0009](../../adr/0009-authentifizierungsmodus.md) (neuer Authentifizierungsmodus, neue Sicherheitsrichtlinie des Betreibers): nicht eingetreten; [ADR-0011](../../adr/0011-pruefwertformat.md) wählt Verfahren innerhalb des Modus.
  - [ADR-0010](../../adr/0010-detekt-pre-release.md) (Stabil-Release von detekt 2.x, Versionswechsel bricht `detekt.yml`): **nicht geprüft.** Die Version ist in `gradle/libs.versions.toml` auf `2.0.0-alpha.6` gepinnt; ob ein Stabil-Release erschienen ist, sagt nur die Release-Liste des Projekts, und kein Sensor im Repo prüft sie (Netz-Abruf, wie der Freshness-Audit der Baseline). In dieser Closure erfolgte kein Abruf. Der Trigger hat keinen Wächter und ist eine geschuldete Handlung.
  - [ADR-0011](../../adr/0011-pruefwertformat.md) (weiteres Verfahren, anderes Format oder Parser-Bibliothek, Messung auf dem Laufzeit-Image, Betreiberanforderung an die Übergabe): nicht eingetreten; Wächter `slice-auth-adapter-benutzerliste` §6.
- Archivierung (Schritt 4): @ARCHIV@
