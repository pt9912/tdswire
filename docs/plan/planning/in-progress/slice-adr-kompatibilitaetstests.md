# Slice slice-adr-kompatibilitaetstests: ADR-0008 entscheiden: Referenzclient und .NET-Kompatibilitätstests

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-entscheidungen-grundlage

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** SPEC-045 · SPEC-050 · SPEC-021 · SPEC-025 · SPEC-032 · ARC-010

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) ist mit einem Nachweis entschieden und `Accepted`; die offene Frage lautet: Pflichtumfang der Testmatrix und Häufigkeit der Läufe.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode der gewählten Option — er entsteht in einem Slice der funktionalen Welle, weil dieser Slice entscheidet und nicht baut.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Spike durchgeführt; seine Ergebnisse (Messwerte, Matrix, Befund) stehen in der ADR. Der Spike-Code ist temporär und nach der Entscheidung gelöscht.
- [x] [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) auf `Accepted`, Option gewählt, Geschichte und ADR-Index nachgezogen.
- [x] Spec-Stellen aus dem `Schärft:`-Feld der ADR an die Entscheidung angepasst.
- [x] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `docs/plan/adr/0008-dotnet-kompatibilitaetstests.md` | update | Entscheidung, Status, Geschichte |
| `docs/plan/adr/README.md` | update | Indexzeile |
| Spike (temporär, nicht im Repo behalten) | neu, danach löschen | Ergebnisse gehen in die ADR |
| `spec/spezifikation.md` | update | Stellen aus `Schärft:` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Zwischenstand der Messung** (Spike per Dockerfile ohne Mount; Option B gebaut und gemessen, Option A aus den Ergebnissen des Transport-Spikes ([ADR-0003](../../adr/0003-transportframework.md)) bewertet, Option C und D nicht gemessen; Testprojekt xUnit 2.9.3 mit `Microsoft.Data.SqlClient` 7.1.1 auf `mcr.microsoft.com/dotnet/sdk:10.0.403` (Digest `sha256:de4fe90a…`, derselbe wie das lokal vorhandene `sdk:10.0`), Docker 29.9.2 mit BuildKit auf einem Host mit 20 CPUs, Basis-Image lokal vorhanden; Zahlen aus einem einzelnen Lauf je Zeile, bislang nicht wiederholt). Der Stub-Server beantwortet PRELOGIN (`ENCRYPT_NOT_SUP`), LOGIN7 (LOGINACK, DONE) und ein `SELECT 1` (COLMETADATA, ROW, DONE); die Tests prüfen `Connection.Open` und `ExecuteScalar` mit `Encrypt=false`. TLS, TDS 8.0, Parameter und Cancel sind nicht Teil des Spikes.

| Messung | A nur JVM-Protokolltests | B .NET-Referenztests im Container |
|---|---|---|
| Aufbau Kaltbau (`--no-cache`, Basis-Image lokal, NuGet aus dem Netz) | nicht gemessen; der Test-Stage fehlt im Dockerfile des Repos | 21,6 s (Restore 7,0 s, Build 4,3 s, Test 1,9 s, Image-Export 6,7 s) |
| Aufbau warm | nicht gemessen | vollständig gecacht 0,9 s; nur Testquelltext geändert 9,5 s; `.csproj` geändert (Restore läuft neu) 23,8 s |
| Image-Größe | nicht gemessen | 1,59 GB auf der Platte, 464 MB Inhalt; die Basis `sdk:10.0.403` allein 1,31 GB, 359 MB Inhalt |
| Netzbedarf | keiner über den Gradle-Build des Repos hinaus | Basis-Image (Pull nicht gemessen, da lokal vorhanden) und NuGet-Restore von `api.nuget.org` bei jedem Lauf mit geänderter `.csproj` oder `packages.lock.json`; `docker build --network none` scheitert dann mit `NU1301`; ein reiner Quelltextlauf braucht kein Netz, solange der Restore-Layer im Cache liegt (offline nicht belegt, weil eine andere Netzwerk-Einstellung den Cache-Schlüssel ändert) |
| Host-Voraussetzung | Docker und `make` | Docker und `make`; auf dem Host liegt kein `dotnet` |
| Szenario `Open` + `SELECT 1`, ein Container (Stub im Testprozess), `make` → `docker run` | — | 2,9 s gesamt, 3/3 Tests, `dotnet test` meldet 226 ms |
| Dasselbe, zwei Container in eigenem Docker-Netzwerk (Server per `docker run -d`, Client per `docker run`, kein Mount, kein veröffentlichter Port) | — | 3,6 s gesamt, 3/3 Tests |
| 4 Läufe gleichzeitig | — | ein Container 4,8 s, zwei Container 10,5 s; alle 4 grün, keine Reste |
| Abbruch (Signal an die Prozessgruppe, wie Strg-C) | — | INT und TERM: ein Container und zwei Container räumen vollständig auf (`trap` im Rezept); KILL: es bleiben Client-Container, bei zwei Containern zusätzlich Server-Container und Netzwerk |
| Ergebnis ohne Mount | — | `docker run`: Exit-Code 0 oder 2 (rot) und Zusammenfassung auf stdout; `docker build --target test`: Exit-Code 1 bei rotem Test, Zusammenfassungszeile im Build-Log |
| Reproduzierbarkeit | — | siehe Befunde |
| Was die Option belegt | Framing, TLS-Handshake, Streaming, defekte Eingaben (Transport-Spike), TDS-Tokens aus eigener Lesart der Spezifikation | zusätzlich: der echte Client akzeptiert die Antworten (`Open`, `ExecuteScalar`) |

Befunde:

- Der Restore ist mit `packages.lock.json` und `--locked-mode` gesperrt: eine geänderte Version in der `.csproj` bricht mit `NU1004`, ein verfälschter `contentHash` mit `NU1403`. Die Lock-Datei entsteht in einer Dockerfile-Stage und kommt per `docker build --output` heraus (wie `make deps-lock` für Gradle), nichts wird gemountet. Das Lock des Testprojekts führt 37 `contentHash`-Einträge; es erfasst auch die transitiven Pakete.
- Der Clean-Build läuft nur mit Docker und `make`: Dockerfile-Frontend und Basis-Image sind per Digest festlegbar, die Pakete per Lock und Hash, die Quelle über eine `nuget.config` mit `<clear />` und einer einzigen Quelle samt `packageSourceMapping`. Das Tag `10.0.403` des Clientprofils löst auf denselben Digest auf, den das lokale `sdk:10.0` trägt.
- NuGet Audit fragt beim Restore online nach Schwachstellen. `Microsoft.Data.SqlClient` 7.1.1 zieht `System.Security.Cryptography.Pkcs` 9.0.18 nach, für das zum Messzeitpunkt `NU1902` und `NU1903` gemeldet wurden; mit `-warnaserror` bricht der Build deshalb. Ein Ergebnis, das vom Tag der Abfrage abhängt, ist nicht reproduzierbar; der Spike setzte `NuGetAudit=false`. Ob das Repo die Schwachstellenprüfung anderweitig führen will, ist offen.
- Die Signaturprüfung (`signatureValidationMode=require`) greift unter Linux: ohne `trustedSigners` scheitert der Restore mit `NU3034`. Mit dem Repository-Zertifikat von nuget.org als einzigem Eintrag scheiterten weiter Pakete mit „signed but not by a trusted signer“; eine funktionierende Liste ist nicht gebaut. Das Autorenzertifikat von `Microsoft.Data.SqlClient` 7.1.1 ist Microsoft (SHA-256 `9A1B131B…`), das Repository-Zertifikat `1F4B311D…`.
- Ein Test-Stage im Dockerfile (`RUN dotnet test`) liefert den Exit-Code des Builds, ist aber nach einem grünen Lauf gecacht (0,9 s ohne Ausführung); ein Test, der Netz oder Zeit braucht, braucht `--no-cache-filter` oder ein `docker run`. Im Stage-Build ist nur ein Stub im Testprozess möglich; ein zweiter Container per `docker build --network` ist nicht erprobt.
- Ein Container ist einfacher: kein Netzwerk, kein Bereitschaftstest, kein Aufräumen von Hand. Zwei Container trennen Server und Client als Prozesse, wie es der echte Server und der echte Client später sind (Server-Image des Repos statt Stub), kosten rund 0,7 s je Lauf, brauchen ein Netzwerk je Lauf (Namen mit PID, damit parallele Läufe sich nicht stören), eine Bereitschaftsschleife auf das Server-Log und ein `trap`; bei `SIGKILL` bleiben Container und Netzwerk stehen.
- Der Transport-Spike zeigte, was erst der echte Client findet: der JDK-Server schickt bei TLS 1.3 ein NewSessionTicket, das der Client im TDS-Paket vom Typ `0x12` nicht lesen kann ([ADR-0003](../../adr/0003-transportframework.md)); ein JVM-Test mit einem JDK-Client, der denselben Strom anders liest, würde das voraussichtlich nicht melden (nicht gemessen). Gegenbeispiel aus dem Spike: ein Platzhalter-Server ohne gültige Antwort lässt den Client mit `FormatException` oder `InvalidOperationException` enden, Meldungen, die nur der Client kennt.
- Der Stub antwortete mit einem Minimum an Tokens (LOGINACK ohne ENVCHANGE und ohne FEATUREEXTACK) und der Client akzeptierte es: `Open` und `SELECT 1` liefen beim ersten gebauten Stand grün. Das sagt nichts über die vollen Antworten des Servers.

Nicht gemessen: Option C (manuelle Tests; kein Reproduzierbarkeitstest möglich). Option D: nicht gemessen und kein SQL-Server-Image gezogen oder gestartet, weil die Zustimmung zur Lizenz (EULA) bei einem Menschen liegt; Bezug, Größe, Startzeit, Speicherbedarf und Architektur des Images, die Pflege von Schema und Ausnahmeliste nach [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) und das Gate [SPEC-050](../../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) sind offen. Für B und A: Kaltpull des Basis-Images, Zeit bei kaltem NuGet-Cache auf einem frischen Runner, Zeit des Gradle-Teils von A im Repo (es gibt noch keinen Test-Stage), TLS (7.4 und 8.0), Parameter, RPC, Cancel und Typen mit dem echten Client, ein Server-Image des Repos statt des Stubs, Verhalten bei langsamen oder sich wiederholenden Läufen in der CI, Wiederholbarkeit der Zahlen (ein Lauf je Zeile), Verhalten von zwei Containern ohne Bereitschaftsschleife, eine Laufzeitgrenze für den Server-Container (`docker run` ohne Zeitlimit lässt ihn bis zum Ende des Hosts laufen, wenn der Abbruch `SIGKILL` ist), eine funktionierende Liste der `trustedSigners`. Die Vorgaben [SPEC-025](../../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) und [SPEC-032](../../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) bleiben mit [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) offen.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-adr-clientprofil` liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die CI-Machbarkeit des Referenzclients (Laufzeit, Image-Bezug) ist nicht gegeben.

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

- Die CI-Machbarkeit des Referenzclients (Laufzeit, Image-Bezug) ist nicht gegeben. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt Spec und ADR, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
