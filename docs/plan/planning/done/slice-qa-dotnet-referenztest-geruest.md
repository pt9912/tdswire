# Slice slice-qa-dotnet-referenztest-geruest: .NET-Referenztest-Gerüst in Docker ohne Server

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)

**Berührte Spec-Stellen:** SPEC-021 · SPEC-025 · SPEC-032

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein .NET-10-Referenztestprojekt (`Microsoft.Data.SqlClient` 7.1.1, Profil TDS 7.4 und 8.0 nach [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)) baut und läuft containerisiert ohne Host-.NET und ohne Mount, mit gesperrtem Restore; ein Make-Target führt einen ersten Smoke-Test aus, der keinen TDS-Server braucht. Das Gerüst ist die Grundlage, auf der [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) die Szenarien gegen den Server dieses Repos schreibt.

**Teilweise Übernahme (kein Ganz-Übergang, `Übernimmt:` entfällt):** den .NET-Restore-Teil von [slice-qa-reproduzierbarkeit](../in-progress/slice-qa-reproduzierbarkeit.md) (NuGet-Audit und `trustedSigners`, [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) §Offene Punkte 1 und 2) und das Testprojekt samt Make-Target aus [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) (dessen DoD-Punkte 1 und 2 sind auf das Gerüst gekürzt). Der Nachzug im ersten Plan ist offen: er steht in `in-progress/` und wird von diesem Plan nicht geändert.

**Entscheidungen des Nutzers (2026-10-30):**

- **F1 NuGet-Audit.** Entschieden (Nutzer, 2026-10-30): Option (b). `NuGetAudit=false` im Projekt, befristet mit Carveout [CO-001](../../carveouts/CO-001-nuget-audit-aus.md) (Trigger und Folge-Slice dort), dazu ein eigenes, ausdrücklich nicht reproduzierbares Prüf-Target ohne Gate-Wirkung, `make referenztest-dotnet-audit`, das Schwachstellen am Tag des Laufs meldet. Offen, im Folge-Slice [slice-qa-dotnet-lieferkette-haerten](../open/slice-qa-dotnet-lieferkette-haerten.md) zu führen: ob ein dauerhafter Verzicht auf den Audit eine ADR braucht (Gate-Lockerung, `AGENTS.md` §3.6).
- **F2 `trustedSigners`.** Entschieden (Nutzer, 2026-10-30): Option (c). Jetzt nur Lock (`packages.lock.json`, `RestoreLockedMode`) und `contentHash`; die Signaturprüfung (`trustedSigners`-Liste) ist Folgearbeit unter [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md) mit Trigger und Folge-Slice [slice-qa-dotnet-lieferkette-haerten](../open/slice-qa-dotnet-lieferkette-haerten.md). Grenze, im Carveout festgehalten: das erste Lock vertraut nuget.org beim Schreiben.
- **F3 Aufnahme in `make gates`.** Entschieden (Nutzer, 2026-10-30): Option (a). `make referenztest-dotnet` steht in `GATE_CHECKS`; Laufzeit, Netz-, Platten- und Imagebedarf des Gerüsts misst der Implementer (kalt und gecacht, mehrere Läufe); die Werte gehören in die Closure-Notiz (§7).
- **F4 Ort und Dockerfile.** Entschieden (Nutzer, 2026-10-30): `referenztest-dotnet/` an der Repo-Wurzel mit eigenem `Dockerfile` und eigenem Build-Kontext (kein Layer-Cache mit den Gradle-Stages), Projekt `TdsWire.Referenztests.csproj`, Smoke-Test unter `Smoke/`, zweiter Frontend-Pin per Digest.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Szenarien gegen den Server dieses Repos (Handshake, Listener, Bootstrap, Anmeldung, Abfragen) — sie brauchen einen lauffähigen Server; Träger ist [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md). Hier entsteht keine Test-Methode, die einen TDS-Server voraussetzt.
- Vergleichslauf D gegen den SQL-Server-Container, EULA-Rechtsprüfung, SQL-Server-Tag für TDS 8.0 — [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) hat D als eigenes Target entschieden; Träger ist [slice-qa-reproduzierbarkeit](../in-progress/slice-qa-reproduzierbarkeit.md).
- CI-Workflow — gehört zu [slice-qa-reproduzierbarkeit](../in-progress/slice-qa-reproduzierbarkeit.md); dieser Slice liefert das Make-Target, das die CI später über `make gates` ruft.
- Zwei-Container-Variante (Netzwerk, Bereitschaftsschleife, `trap`) — [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) lässt sie zu, sobald ein Szenario getrennte Prozesse braucht; ohne Szenario kein Bedarf.

**Keine Mindestzahl.** Ein Slice mit *einem* echten Ausschluss ist besser als
einer mit vier erfundenen; die vier Klassen sind ein Suchraster, keine
Ausfüll-Liste. Suchreihenfolge: Was übernimmt ein **Folge-Slice** (mit
Kennung — und die Kennung muss den Punkt auch annehmen)? Was bleibt als
**Bestand** bewusst stehen (mit Begründung)? Was wäre ein **anderer Vorgang**?
Welche **Schicht** rührt der Slice nicht an?

Was hier steht, ist die Grenze, an der ein wachsender Slice sich messen lässt:
Wer später etwas mitnimmt, das hier ausgeschlossen war, hat den Plan
**geändert**, nicht nur ergänzt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **Liefer-Punkt 1 — Testprojekt und gesperrter Restore:** Das Projekt am gewählten Ort (F4) baut in Docker ohne Host-.NET und ohne Mount (Build-Kontext per `COPY`). Basis-Image und Dockerfile-Frontend sind per Digest gepinnt (der in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) gekürzt genannte SDK-Digest ist aufgelöst und gegen das Tag `10.0.403` geprüft). `nuget.config` mit `<clear />`, einer Quelle und `packageSourceMapping`; `packages.lock.json` mit `RestoreLockedMode`, in einer Dockerfile-Stage erzeugt und per `--output` herausgegeben (Target nach dem Vorbild `make deps-lock`). NuGet-Audit und `trustedSigners` sind nach den Antworten auf F1 und F2 umgesetzt, ein befristeter Ausstieg trägt Carveout und Trigger. Belegt durch je einen roten Lauf bei geänderter Version in der `.csproj` und bei verfälschtem `contentHash` (die Fehlercodes sind in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) gemessen, hier am neuen Projekt nachzumessen).
- [x] **Liefer-Punkt 2 — Make-Target und Smoke-Test ohne Server:** Make-Fragment unter `tools/mk/` (Name nach dem Belang) mit Test-Target und Lock-Target, je eine Zeile in `harness/README.md` §Sensors. Der Smoke-Test läuft ohne TDS-Server (Vorschlag: `SqlConnection.Open` gegen einen bewusst nicht belegten Loopback-Port mit kurzem `ConnectTimeout` und erwarteter `SqlException`; das beobachtete Fehlerbild wird gemessen, nicht angenommen) und belegt nur, dass Restore, Build und Test in Docker ohne Mount laufen — über das Verhalten eines TDS-Servers sagt er nichts. Ein absichtlich roter Smoke-Test macht das Target rot (Selbstprobe). Aufnahme in `GATE_CHECKS` nach F3; die Messung des Gerüsts (Kaltbau, warm, Image-Größe) steht in der Closure-Notiz.
- [x] **Liefer-Punkt 3 — Szenarienliste:** Die Liste in §3 ist vom Implementer gegen den Stand von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und [ADR-0009](../../adr/0009-authentifizierungsmodus.md) geprüft und bei Bedarf ergänzt; [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) verweist auf sie; im Testprojekt liegt kein Szenario, das einen Server voraussetzt.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: `harness/README.md` §Sensors (neue Targets); `contracts/client-profile.md` nur, falls das Gerüst eine Angabe des Profils berührt.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `referenztest-dotnet/` (Ort nach F4): `Dockerfile`, `TdsWire.Referenztests.csproj`, `nuget.config`, `packages.lock.json`, Ignore-Datei für `bin/` und `obj/` | neu | Gerüst, Restore-Sperre, Digest-Pins ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)) |
| `referenztest-dotnet/Smoke/` | neu | Smoke-Test ohne Server; Testart Negative (nicht erreichbarer Endpunkt, erwarteter Fehler, Zeitbegrenzung) nach [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), nur als Gerüstbeleg |
| `tools/mk/` Make-Fragment für .NET | neu | Test-Target, Lock-Target, ggf. `GATE_CHECKS +=` (F3) |
| `harness/README.md` | update | je Target eine Zeile (Doku-Gate `targets`) |
| `docs/plan/carveouts/` | neu | CO-001 (Audit) und CO-002 (Signaturprüfung) nach F1 und F2, dazu der Index `README.md` |
| `docs/plan/planning/open/slice-qa-dotnet-lieferkette-haerten.md` | neu | Folge-Slice der beiden Carveouts |
| `.dockerignore` | update, falls F4 das Wurzel-Dockerfile wählt | `bin/`, `obj/` |

**Szenarienliste (Vorbild: Pflicht-Matrix B und die Offene-Punkte-Listen; nichts davon wird hier umgesetzt).** Die Zeilen sind Kandidaten; Messwerte stammen aus [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) (einmal gemessen) und gelten nur dort. Die Spalte *Träger* nennt den Slice, der das Szenario schreibt.

| Nr. | Szenario | Quelle | Träger |
|---|---|---|---|
| S1 | Anmeldung mit SQL-Authentifizierung, `Encrypt=false` (TDS 7.4) | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) Pflicht-Matrix | slice-qa-kompatibilitaet |
| S2 | Anmeldung abgelehnt: falsches Kennwort und unbekannter Benutzer liefern dieselbe Ablehnung | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), SPEC-012 | slice-qa-kompatibilitaet |
| S3 | `SQL_BATCH` (`SELECT 1`, leeres Ergebnis) | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) | slice-qa-kompatibilitaet |
| S4 | RPC mit Parametern (`Int`, `NVarChar` mit `ü€`) und `NULL`-Parameter | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) | slice-qa-kompatibilitaet |
| S5 | Typen der Messung: `decimal(10,2)`, `bit`, `float`, `datetime2(3)`, `date`, `int`, `nvarchar` mit Zeichen außerhalb der BMP, `NULL` als `DBNull` | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) | slice-qa-kompatibilitaet |
| S6 | Mehrfachergebnisse (zwei Anweisungen, zwei Resultsets); Zeilenzahl bei `UPDATE` und `DELETE` | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) | slice-qa-kompatibilitaet |
| S7 | Fehler vor Zeilen; Verbindung danach nutzbar | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) | slice-qa-kompatibilitaet |
| S8 | Fehler nach Zeilen (Server-Referenz fehlt, nur die Annahme des Entwurfs) | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) §Offen, [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) | slice-qa-kompatibilitaet |
| S9 | OUTPUT-Parameter und Rückgabewert einer Prozedur | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) | slice-qa-kompatibilitaet |
| S10 | Cancel (`Command.Cancel()`) mit Folgeabfrage auf derselben Verbindung | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) | slice-qa-kompatibilitaet |
| S11 | TDS 7.4 mit TLS: `Encrypt=true;TrustServerCertificate=true` | [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), `contracts/client-profile.md` | slice-qa-kompatibilitaet |
| S12 | TDS 8.0: `Encrypt=Strict;HostNameInCertificate=<Name>` mit vertrauenswürdigem Serverzertifikat | [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), `contracts/client-profile.md` | slice-qa-kompatibilitaet |
| S13 | Anmeldemessungen aus [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen: Windows-SSPI (NTLM), TDS 8.0 mit SQL-Authentifizierung, mehrpaketiges LOGIN7, Pooling mit `ResetConnection` | [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) §1 | slice-qa-kompatibilitaet |
| S14 | RPC-Messungen aus [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) §Offen: `sp_unprepare` unter Pooling, `SET`-Text bei `SchemaOnly` und `KeyInfo`, Transaktionen (`0x0E`), TDS 8.0 für RPC | [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) §1 | slice-qa-kompatibilitaet |
| S15 | Verbindungsabbruch (getrennte Prozesse, Zwei-Container-Variante) | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) | slice-qa-kompatibilitaet; Zuordnung zu [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) offen |
| S16 | Vergleichswerte am echten SQL Server (18456/State 1/Class 14, 208, 8134, Cancel) | [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) Option D | slice-qa-reproduzierbarkeit (Vergleichslauf D) |
| S17 | Nicht unterstützte Anmeldemodi (`Integrated Security`, `Authentication=ActiveDirectory…`): Ablehnung wie eine falsche Anmeldung; gemessen ist in [ADR-0009](../../adr/0009-authentifizierungsmodus.md) nur ein MIT-Kerberos-Token | [ADR-0009](../../adr/0009-authentifizierungsmodus.md) | slice-qa-kompatibilitaet |
| S18 | RPC-Lücken aus [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) §Offen, die S14 nicht nennt: Batch-Flag mehrerer RPCs, Table-Valued-Parameter, MARS, Fehlerantwort auf eine unbekannte RPC-Form | [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) | slice-qa-kompatibilitaet |

Nicht aufgenommen: Drosselung wiederholter Anmeldeversuche ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen) — sie gehört zu [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), Träger [slice-qa-sicherheit](../open/slice-qa-sicherheit.md), nicht zur Client-Kompatibilität.

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Reihenfolge: (1) F1 bis F4 beantwortet; (2) Dockerfile-Stages mit Pins und Lock-Erzeugung; (3) Projekt, Smoke-Test, Make-Target; (4) Entscheidung F3 umsetzen; (5) Messung des Gerüsts.
- Das Lock entsteht in einer Dockerfile-Stage (`--output`), nicht auf dem Host; ein Mount kommt nirgends vor.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `make gates` ist auf `main` grün. Die Antworten auf F1 bis F4 sind kein Zusatz-Trigger, sondern Voraussetzung der ersten Umsetzungsschritte (siehe zweite Rückführung).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, etwa weil F2 (a) eine Signaturliste verlangt, deren Aufwand den Rest übersteigt (dann eigener Folge-Slice).
- `in-progress` → `open` (blockiert — Carveout?): F1 oder F2 bleibt unbeantwortet, oder das Basis-Image ist auf dem Zielsystem nicht beziehbar.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Kaltbau und Netzbedarf in `make gates`: gemessen ist nur der Minimal-Stub (21,6 s kalt, 0,9 s gecacht, 1,59 GB Image); dieses Gerüst ist nicht gemessen. Ausgangsvorschlag: in der Closure messen, F3 danach bestätigen oder zurücknehmen. — **Ausgang:** *entfallen, gemessen.* Kalt 17,6 bis 23,2 s, gecacht 1,0 bis 1,3 s, Image 1,61 GB (Tabelle unten, ein Host, amd64); der CI-Lauf auf GitHub (§7) zog das Basis-Image kalt in 7,4 s, der .NET-Schritt dauerte dort etwa 16 s. F3 (Aufnahme in `make gates`) bleibt bestätigt. arm64 ist nicht gemessen.
- Ein grüner Test-Stage-Lauf ist danach gecacht (0,9 s ohne Ausführung, [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)); der Smoke-Test braucht Loopback und Zeit. Ausgangsvorschlag: `docker build --target test` wie bei `make test` akzeptieren, solange der Smoke nur Restore, Build und Test belegt; bei Bedarf `--no-cache-filter`. — **Ausgang:** *weiter offen* → Beobachtungs-Register, [BEO-TDS/test-stage-cache-verdeckt-wiederholung](../observations/BEO-TDS/test-stage-cache-verdeckt-wiederholung/observation.md). Der Cache ist gemessen (Review F5, Verifikation I-2); `docker build --target test` bleibt wie bei `make test`, `--no-cache-filter test` erzwingt die Wiederholung.
- Das Fehlerbild des Smoke-Tests ist nicht gemessen (Ausnahmetyp, Dauer bis `ConnectTimeout`, Verhalten bei `--network none`). Ausgangsvorschlag: erst messen, dann nur auf das Gemessene assertieren. — **Ausgang:** *entfallen, gemessen.* `SqlException`, `Number 0`, `Class 20`, Dauer 769 bis 1000 ms bei `Connect Timeout=1` in 22 Läufen ohne roten Lauf, unter `--network none` (Verifikation §3); der Test assertiert nur dieses Gemessene. Abstand zur 5-s-Grenze etwa Faktor 5 (ein Host, amd64).
- `NU1902`/`NU1903` für `Pkcs` 9.0.18 sind für einen Tag gemessen; Audit-Befunde ändern sich mit dem Abfragetag. Ausgangsvorschlag: F1 (a) mit Carveout. — **Ausgang:** *eingetreten* → Carveout [CO-001](../../carveouts/CO-001-nuget-audit-aus.md) und Folge-Slice [slice-qa-dotnet-lieferkette-haerten](../open/slice-qa-dotnet-lieferkette-haerten.md). Der Audit am Tag des Laufs meldet Pkcs 9.0.18 (High und Moderate).
- `trustedSigners`: der Aufwand einer funktionierenden Liste ist unbekannt. Ausgangsvorschlag: F2 (c); eine Zeitbox für einen Versuch setzt der Nutzer. — **Ausgang:** *eingetreten* → Carveout [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md) und Folge-Slice [slice-qa-dotnet-lieferkette-haerten](../open/slice-qa-dotnet-lieferkette-haerten.md). Entschieden mit F2 (c); eine Zeitbox ist nicht gesetzt.
- Das Test-Framework ist in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) nicht benannt; sein Paketbaum geht in Lock und Audit ein (das Lock des Spikes führte 37 `contentHash`-Einträge, das Framework dort ist nicht dokumentiert). Ausgangsvorschlag: ein Standard-Framework der Microsoft-Testplattform, Wahl und Version in der Closure-Notiz. — **Ausgang:** *entfallen* durch Entscheidung: xunit 2.9.3, Microsoft.NET.Test.Sdk 18.11.0, xunit.runner.visualstudio 3.1.5; das Lock führt 36 Pakete (32 transitiv) mit `contentHash`. Der Paketbaum ist nicht auditiert ([CO-001](../../carveouts/CO-001-nuget-audit-aus.md)); ein Vergleich mit dem Baum des Spikes (37 Einträge, Framework nicht dokumentiert) ist nicht gefahren.
- Digest des SDK-Images: [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) nennt ihn gekürzt (`sha256:de4fe90a…`); gemessen wurde auf einem x86_64-Host, arm64 nicht. Ausgangsvorschlag: den vollständigen Digest beim Auflösen des Tags `10.0.403` holen und prüfen, die Plattform in der Closure benennen. — **Ausgang:** *entfallen.* Der Digest `sha256:de4fe90a98845d75910b9769662465c84de1a934665c431279d1382b2bddc02b` ist der Index-Digest des Tags `10.0.403` und gleich dem Pin im Dockerfile (Verifikation §1); gefahren ist amd64 (lokal und auf dem GitHub-Runner), der Index führt auch arm64 und arm/v7, deren Lauf nicht gemessen ist.

### Messwerte (Vorbereitung der Closure)

Aus der Verifikation ([Report](../../../reviews/2026-10-30-9fcd9f4-verifikation-slice-qa-dotnet-referenztest-geruest.md)), ein Host, amd64, Basis-Image lokal vorhanden; Plattformen arm64 und ein frischer Pull sind nicht gemessen:

| Größe | Messwert |
|---|---|
| Kaltbau (`--no-cache`) | 17,6 bis 23,2 s (zwei Messreihen, Abweichung ungeklärt) |
| gecachter Lauf | 1,0 bis 1,3 s |
| Lauf nach Quelltext- oder `.csproj`-Änderung | 11 bis 35 s |
| Image | 1,61 GB |
| Smoke-Test | 769 bis 1000 ms in 22 Läufen bei `Connect Timeout=1`, etwa 0,8 x Timeout |

Test-Stage-Cache: ein grüner Lauf der Stage `test` ist danach gecacht; `make gates` führt den Smoke-Test nur aus, wenn sich Eingänge der Stage ändern. Wiederholte grüne Gate-Läufe belegen die Stabilität des Tests nicht erneut; dafür gilt `--no-cache-filter test` oder ein `docker run` der Test-Image.

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

- **Belege:** Review [2026-10-30-9fcd9f4-slice-qa-dotnet-referenztest-geruest](../../../reviews/2026-10-30-9fcd9f4-slice-qa-dotnet-referenztest-geruest.md) (kein HIGH, ein MEDIUM, drei LOW, drei INFO), Verifikation [2026-10-30-9fcd9f4-verifikation-slice-qa-dotnet-referenztest-geruest](../../../reviews/2026-10-30-9fcd9f4-verifikation-slice-qa-dotnet-referenztest-geruest.md) (kein HIGH, ein MEDIUM, sechs LOW, drei INFO). Die Befunde sind im Merge `ef47fff` eingearbeitet. Bezug: [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md).
- **Was funktioniert hat:** Das Gerüst baut und testet in Docker ohne Host-.NET und ohne Mount; Basis-Image und Dockerfile-Frontend sind per Digest gepinnt, der Restore ist gesperrt. Die vier Entscheidungen des Nutzers (F1 bis F4) wurden vor dem ersten Code gestellt und trugen: zwei befristete Ausnahmen mit Carveout statt stillem Verzicht ([CO-001](../../carveouts/CO-001-nuget-audit-aus.md), [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md)).
- **Messwerte, Zuordnung** (ein Host, amd64, Basis-Image lokal vorhanden; Quellen: Implementer-Tabelle in §6 und Verifikation §1 bis §3):

  | Aussage | Zuordnung | Wert und Grenze |
  |---|---|---|
  | Kaltbau (`--no-cache`) | gemessen | 17,6 bis 23,2 s (zwei Messreihen, Abweichung ungeklärt); Verifier 17,6 s |
  | gecachter Lauf | gemessen | 1,0 bis 1,3 s; Verifier 1,03 s |
  | Lauf nach Quelltext- oder `.csproj`-Änderung | gemessen | 11 bis 35 s |
  | Image | gemessen | 1,61 GB (Stage `test`) |
  | Smoke-Test | gemessen | 769 bis 1000 ms in 22 Läufen bei `Connect Timeout=1` (ohne Last 771 bis 822 ms, unter CPU-Last bis etwa 1 s), kein roter Lauf, Abstand zur 5-s-Grenze etwa Faktor 5; Fehlerbild `SqlException`, `Number 0`, `Class 20`, `--network none` |
  | Dauer etwa 0,8 x Timeout heißt: Verbindungsversuche laufen bis kurz vor den Timeout | Folgerung aus Dauer und Text, nicht bewiesen | |
  | Grenze 5 s für den Smoke-Test | Setzung | als solche im Test gekennzeichnet |
  | Rot bei geänderter Version / verfälschtem `contentHash` (auch transitiv) | gemessen | `NU1004` (Version, nicht gelockte Referenz), `NU1403` (Hash, auch Pkcs 9.0.18); gelöschtes Lock bricht am `COPY`, nicht mit einem NU-Code |
  | Selbstprobe: absichtlich roter Smoke-Test macht `make referenztest-dotnet` rot | gemessen | Exit 2, `Failed: 1` |
  | Test-Stage ohne Netz | gemessen | mit `--network=none` bricht ein Netzzugriff, die Gegenprobe ohne die Option ist grün; der Restore selbst läuft mit Netz |
  | Lock wird byte-gleich neu erzeugt | gemessen | `make referenztest-dotnet-lock`, danach leerer `git status` |
  | Framework und Paketbaum | Setzung (Wahl) | xunit 2.9.3, Microsoft.NET.Test.Sdk 18.11.0, xunit.runner.visualstudio 3.1.5; 36 Pakete, davon 32 transitiv (Verifikation I-3) |
  | Audit-Befund | gemessen, am Abfragetag | Pkcs 9.0.18 transitiv, High (GHSA-846f-f764-76mx) und Moderate (GHSA-2fmv-rv2c-59x7); `make referenztest-dotnet-audit` endet mit Exit 0 auch bei Fund |
  | arm64, frischer Pull des Basis-Images lokal und der Defaultwert von `signatureValidationMode` ohne Eintrag | nicht gemessen | Index des SDK-Tags führt arm64, gefahren ist es nicht |
  | Verhalten gegen einen TDS-Server | nicht gemessen | der Slice enthält keinen Server-Test (§1) |

- **CI:** Der Workflow `.github/workflows/gates.yml` lief für den Commit `30baefa` (enthält das .NET-Gate) auf GitHub: Lauf 41028544823, Ergebnis *success*, Laufzeit 4 min 25 s (Job `gates` 4 min 22 s). Im Log lief `docker build --target test referenztest-dotnet`: Basis-Image kalt in 7,4 s bezogen, Restore 2,7 s, Smoke-Test bestanden (801 ms), der .NET-Schritt dauerte etwa 16 s. Der Lauf auf dem Runner belegt diesen einen Lauf, nicht die Stabilität über viele.
- **Was ging anders als geplant:** Der Kaltbau-Wert des Auftrags (23 s) ließ sich vom Verifier nicht reproduzieren (17,6 s); die Spanne ist deshalb als Spanne geführt, nicht geglättet. Zwei KDoc- bzw. Konfigurationsaussagen waren breiter als ihr Beleg (Review F1 „ohne Netz“ für den ganzen Ablauf; F2 „allein Lock und `contentHash`“); beide sind eingeschränkt ([AGENTS.md](../../../../AGENTS.md) §3.8). Der Index der Carveouts kürzte einen Trigger (F3). Der Szenarien-Abgleich gegen die „Offen“-Aussagen von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und [ADR-0009](../../adr/0009-authentifizierungsmodus.md) fehlte in der Verifikation und wurde in der Closure nachgeholt: S10, S13, S14, S17, S18 tragen die „Offen“-Punkte beider ADRs; Zeitgleichheit und Drosselung gehören zu [slice-qa-sicherheit](../open/slice-qa-sicherheit.md), der interne State des echten Servers zu S16.
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`. Festgestellt und gezählt: (1) Der Test-Stage-Cache verdeckt die Wiederholung grüner Läufe; `make gates` führt den Smoke-Test nur bei geänderten Eingängen aus, ein zweiter grüner Lauf belegt die Stabilität nicht erneut ([BEO-TDS/test-stage-cache-verdeckt-wiederholung](../observations/BEO-TDS/test-stage-cache-verdeckt-wiederholung/observation.md), 1×). (2) Die Regel [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) ist verkörpert (`AGENTS.md` §3.8) und trat in diesem Slice dennoch zweimal in Kommentaren auf (Review F1, F2; Verifikation M-1, L-1); der Reviewer fing es. Ob die Regel dem Implementer-Kontext vorlag, ist aus den Belegen nicht zu ersehen; ein neuer Sensor folgt daraus nicht. Eine benannte Spec-Lücke fiel nicht an.
- **Beobachtungs-Register (`../observations/`):** neu [BEO-TDS/test-stage-cache-verdeckt-wiederholung](../observations/BEO-TDS/test-stage-cache-verdeckt-wiederholung/observation.md); `evidence/slice-qa-dotnet-referenztest-geruest.md` ergänzt in [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (Review F1, F2; Stand bleibt `verkörpert`) und [BEO-TDS/messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md) (S8 und S16 der Szenarienliste halten Messungen eines echten SQL Servers; Stand bleibt `geplant`). Nicht aufgetreten sind in diesem Slice [BEO-TDS/tds-8-0-und-pooling-nicht-gemessen](../observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/observation.md) und [BEO-TDS/vergleichslauf-d-ungeklaert](../observations/BEO-TDS/vergleichslauf-d-ungeklaert/observation.md): S12 bis S14 führen TDS 8.0 und Pooling als Szenarien mit Träger, D ist ausgeschlossen (§1); keine neue Messlücke fiel an. Die Zähler folgen aus den Dateien; kein Eintrag erreicht mit diesem Slice 3× ohne Stand.
- **Carveouts und Folge-Slice:** [CO-001](../../carveouts/CO-001-nuget-audit-aus.md) und [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md) mit Folge-Slice [slice-qa-dotnet-lieferkette-haerten](../open/slice-qa-dotnet-lieferkette-haerten.md); sie verlangen kein rotes Gate. Der Nachzug in [slice-qa-reproduzierbarkeit](../in-progress/slice-qa-reproduzierbarkeit.md) (Umfang, DoD, Plan, Risiko) ist mit dieser Closure erfolgt; dessen Vergleichslauf D bleibt dort, die Szenarien liegen bei [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md).
- **Risiken aus §6:** Sieben, je ein Ausgang: gemessen und entfallen (Kaltbau, Fehlerbild), entfallen durch Entscheidung (Framework) bzw. Auflösung (Digest), eingetreten mit Carveout und Folge-Slice (Audit, `trustedSigners`), weiter offen im Register (Test-Stage-Cache).
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md). Anker ↔ Folge-Slice ist getragen (Carveouts nennen den Folge-Slice, dieser nennt beide zurück, Verifikation §4).

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` deklariert nur `*` (Default, GF); der Slice berührt das ganze Repo, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** `../observations/BEO-TDS/` durchgegangen. Treffer: `vergleichslauf-d-ungeklaert` (1 Evidence-Datei, Träger [slice-qa-reproduzierbarkeit](../in-progress/slice-qa-reproduzierbarkeit.md); hier nicht berührt, D ist ausgeschlossen); `tds-8-0-und-pooling-nicht-gemessen` (3 Evidence-Dateien, Träger [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md); S12 bis S14 der Szenarienliste halten die Punkte, gemessen wird dort); `messung-am-echten-sql-server-fehlt` (3 Evidence-Dateien, Träger [slice-qa-reproduzierbarkeit](../in-progress/slice-qa-reproduzierbarkeit.md); hier nicht berührt). Die beiden Einträge mit drei Dateien tragen bereits einen Slice als Stand; dieser Slice nimmt sie nicht auf. `aussagen-ueber-messung-hinaus` ist verkörpert (`AGENTS.md` §3.8) und gilt: jede Messaussage des Gerüsts trägt höchstens so weit wie ihre Messzeile.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
