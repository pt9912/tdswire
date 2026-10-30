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

**Verantwortlich:** — bis zur Priorisierung.

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

**Entscheidungen des Nutzers (offen — vor dem Start `next` → `in-progress` zu beantworten; die Ausgangsvorschläge sind Planer-Vorschläge, keine Entscheidungen):**

- **F1 NuGet-Audit.** Befund ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), Spike): Audit fragt beim Restore online; `System.Security.Cryptography.Pkcs` 9.0.18 (Abhängigkeit von `Microsoft.Data.SqlClient` 7.1.1) meldete zum Messzeitpunkt `NU1902`/`NU1903`, mit `-warnaserror` bricht der Build; der Spike setzte `NuGetAudit=false`. Optionen: (a) `NuGetAudit=false` im Projekt, befristet mit Carveout und Trigger (`AGENTS.md` §3.6); (b) wie (a), dazu die Schwachstellenprüfung als eigenes, ausdrücklich nicht reproduzierbares Target ohne Gate-Wirkung (etwa `dotnet list package --vulnerable`; nicht gemessen); (c) Audit an, Warnungen nicht als Fehler — das Ergebnis hängt dann am Abfragetag (Folgerung, nicht gemessen). Ausgangsvorschlag: (a) für dieses Gerüst; ob (b) dauerhaft gilt und ob ein dauerhafter Verzicht eine ADR braucht (Gate-Lockerung?), entscheidet der Nutzer.
- **F2 `trustedSigners`.** Befund ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), Spike): `signatureValidationMode=require` greift unter Linux; ohne Liste scheitert der Restore mit `NU3034`; mit dem Repository-Zertifikat von nuget.org als einzigem Eintrag scheiterten weiter Pakete („signed but not by a trusted signer“); eine funktionierende Liste ist nicht gebaut. Optionen: (a) Liste bauen (Autorenzertifikat Microsoft und Repository-Zertifikat, dazu die Autoren der übrigen Pakete im Lock; Aufwand nicht gemessen) und `require` setzen; (b) keine Signaturprüfung, Integrität allein über Lock und `contentHash` (belegt: `NU1403`) — Grenze: das erste Lock vertraut nuget.org beim Schreiben, der Default des Modus ist nicht gemessen; (c) (b) jetzt, (a) als Folgearbeit mit Carveout. Ausgangsvorschlag: (c), weil (a) ohne Messung nicht abschätzbar ist; (a) als eigener Folge-Slice, falls der Nutzer die Signaturprüfung als Gate will.
- **F3 Aufnahme in `make gates`.** [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) verlangt B in `make gates`, D nicht. Gemessen am Minimal-Stub: Kaltbau 21,6 s, vollständig gecacht 0,9 s, Image 1,59 GB, Netz für Basis-Image und Restore bei geänderter `.csproj` oder `packages.lock.json`; für dieses Gerüst nicht gemessen. Optionen: (a) Target mit `GATE_CHECKS +=` schon jetzt — Restore-, Lock- und Digest-Drift wird sofort rot; (b) Target ohne Gate-Wirkung, Aufnahme erst mit [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md), wenn echte Szenarien laufen. Ausgangsvorschlag: (a); die Messung des Gerüsts (Kaltbau, warm, Platte) steht in der Closure-Notiz.
- **F4 Ort und Dockerfile.** Ausgangsvorschlag: Verzeichnis `referenztest-dotnet/` an der Repo-Wurzel mit eigenem `Dockerfile` und dem Verzeichnis als Build-Kontext (`docker build referenztest-dotnet/`), Projekt `TdsWire.Referenztests.csproj`, Smoke-Test unter `referenztest-dotnet/Smoke/`. Begründung: Die Gradle-Stages (`COPY` einzelner Pfade) und die .NET-Stages teilen keinen Layer-Cache; ein eigener Kontext hält .NET-Änderungen aus den Gradle-Layern und umgekehrt. `.d-check.yml` prüft Pfade nur unter `spec`, `docs`, `harness` (`codepaths`), `structure` gilt nur für `harness/README.md` — daraus ergibt sich für den Ort keine Sperre (aus der Konfiguration gelesen, nicht mit einem Lauf belegt). Gegenvorschlag: zusätzliche Stages im Wurzel-`Dockerfile`, wie [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) §Folgepflicht sie nennt; das mischt die Build-Kontexte, spart aber einen zweiten Frontend-Pin. Ein eigenes Dockerfile braucht einen eigenen Frontend-Pin per Digest.

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

- [ ] **Liefer-Punkt 1 — Testprojekt und gesperrter Restore:** Das Projekt am gewählten Ort (F4) baut in Docker ohne Host-.NET und ohne Mount (Build-Kontext per `COPY`). Basis-Image und Dockerfile-Frontend sind per Digest gepinnt (der in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) gekürzt genannte SDK-Digest ist aufgelöst und gegen das Tag `10.0.403` geprüft). `nuget.config` mit `<clear />`, einer Quelle und `packageSourceMapping`; `packages.lock.json` mit `RestoreLockedMode`, in einer Dockerfile-Stage erzeugt und per `--output` herausgegeben (Target nach dem Vorbild `make deps-lock`). NuGet-Audit und `trustedSigners` sind nach den Antworten auf F1 und F2 umgesetzt, ein befristeter Ausstieg trägt Carveout und Trigger. Belegt durch je einen roten Lauf bei geänderter Version in der `.csproj` und bei verfälschtem `contentHash` (die Fehlercodes sind in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) gemessen, hier am neuen Projekt nachzumessen).
- [ ] **Liefer-Punkt 2 — Make-Target und Smoke-Test ohne Server:** Make-Fragment unter `tools/mk/` (Name nach dem Belang) mit Test-Target und Lock-Target, je eine Zeile in `harness/README.md` §Sensors. Der Smoke-Test läuft ohne TDS-Server (Vorschlag: `SqlConnection.Open` gegen einen bewusst nicht belegten Loopback-Port mit kurzem `ConnectTimeout` und erwarteter `SqlException`; das beobachtete Fehlerbild wird gemessen, nicht angenommen) und belegt nur, dass Restore, Build und Test in Docker ohne Mount laufen — über das Verhalten eines TDS-Servers sagt er nichts. Ein absichtlich roter Smoke-Test macht das Target rot (Selbstprobe). Aufnahme in `GATE_CHECKS` nach F3; die Messung des Gerüsts (Kaltbau, warm, Image-Größe) steht in der Closure-Notiz.
- [ ] **Liefer-Punkt 3 — Szenarienliste:** Die Liste in §3 ist vom Implementer gegen den Stand von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und [ADR-0009](../../adr/0009-authentifizierungsmodus.md) geprüft und bei Bedarf ergänzt; [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) verweist auf sie; im Testprojekt liegt kein Szenario, das einen Server voraussetzt.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `harness/README.md` §Sensors (neue Targets); `contracts/client-profile.md` nur, falls das Gerüst eine Angabe des Profils berührt.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

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
| `docs/plan/carveouts/` | neu, falls F1/F2 es verlangen | befristeter Ausstieg für Audit oder Signaturprüfung |
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

- Kaltbau und Netzbedarf in `make gates`: gemessen ist nur der Minimal-Stub (21,6 s kalt, 0,9 s gecacht, 1,59 GB Image); dieses Gerüst ist nicht gemessen. Ausgangsvorschlag: in der Closure messen, F3 danach bestätigen oder zurücknehmen. — **Ausgang:** offen bis zur Closure.
- Ein grüner Test-Stage-Lauf ist danach gecacht (0,9 s ohne Ausführung, [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)); der Smoke-Test braucht Loopback und Zeit. Ausgangsvorschlag: `docker build --target test` wie bei `make test` akzeptieren, solange der Smoke nur Restore, Build und Test belegt; bei Bedarf `--no-cache-filter`. — **Ausgang:** offen bis zur Closure.
- Das Fehlerbild des Smoke-Tests ist nicht gemessen (Ausnahmetyp, Dauer bis `ConnectTimeout`, Verhalten bei `--network none`). Ausgangsvorschlag: erst messen, dann nur auf das Gemessene assertieren. — **Ausgang:** offen bis zur Closure.
- `NU1902`/`NU1903` für `Pkcs` 9.0.18 sind für einen Tag gemessen; Audit-Befunde ändern sich mit dem Abfragetag. Ausgangsvorschlag: F1 (a) mit Carveout. — **Ausgang:** offen bis zur Closure.
- `trustedSigners`: der Aufwand einer funktionierenden Liste ist unbekannt. Ausgangsvorschlag: F2 (c); eine Zeitbox für einen Versuch setzt der Nutzer. — **Ausgang:** offen bis zur Closure.
- Das Test-Framework ist in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) nicht benannt; sein Paketbaum geht in Lock und Audit ein (das Lock des Spikes führte 37 `contentHash`-Einträge, das Framework dort ist nicht dokumentiert). Ausgangsvorschlag: ein Standard-Framework der Microsoft-Testplattform, Wahl und Version in der Closure-Notiz. — **Ausgang:** offen bis zur Closure.
- Digest des SDK-Images: [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) nennt ihn gekürzt (`sha256:de4fe90a…`); gemessen wurde auf einem x86_64-Host, arm64 nicht. Ausgangsvorschlag: den vollständigen Digest beim Auflösen des Tags `10.0.403` holen und prüfen, die Plattform in der Closure benennen. — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure gefüllt.

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
