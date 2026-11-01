# Review: slice-qa-kompatibilitaet (Liefer-Punkte 1 bis 3, Commit b257f85)

* Basis: `main`, Diff `main...worktree-agent-aeffb90db7bc4a9ee`, HEAD `b257f85`; Commits `1b81894`, `565c80b`, `b257f85` plus Lifecycle-Commits.
* Prüfgrundlage: Plan, [ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), `AGENTS.md` §3.1 bis §3.8, nach `.harness/skills/reviewer.md`; nicht gegen die DoD.
* Lauf: Diff gelesen, Skripte, Tests, Matrix und KDoc stichprobenartig geprüft; `make e2e` und `make gates` nicht gefahren; Mutationsangaben der Matrix gegen Testcode und Skript gelesen, nicht nachgestellt.
* Herkunft: Reviewer-Rolle, frischer Kontext, ohne eigenen Worktree; der Inhalt ist sein Bericht, vom Aufrufer abgelegt.

## Gesamturteil

Kein HIGH. Eine Aussage ist breiter als ihr Beleg (MEDIUM), dazu fünf LOW und mehrere INFO. Matrix Abschnitt 7 und die Testkopf-KDoc benennen ausdrücklich, was grün blieb (Paketgröße 8000, AccessToken) und was nicht beobachtet ist. Die Schwächen liegen an den Rändern: KDoc des Servers, ein Stand-Absatz, die Hilfezeile und der Selbsttest.

## Findings

* **M-1 (MEDIUM, 3.8, [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität)), `src/test/…/runtime/KompatibilitaetsServer.kt`, KDoc von `breit` und `WIDE_TEXT_CHARS`:** Als Zweck steht „Antworten über mehrere Pakete bei der Paketgröße 512“. Matrix 7b („Rot gesehen“) und `AnmeldungTests` sagen selbst, dass ein Server mit fester Paketgröße 8000 den Test grün ließ; die Breite von 600 Zeichen hat keine beobachtete Wirkung. `e2e_abdeckung` Stufe 6 nennt „Paketgröße 512 mit den längsten Feldern“ und zählt die Antwortpaketgröße nicht unter „Nicht belegt“ (dort steht nur die Paketzahl eines LOGIN7). Die Quellen widersprechen sich. Szenario: ein Leser hält „mehrpaketige Antworten bei 512“ für belegt, eine Regression der Antwortpaketierung bleibt grün.
* **L-1 (LOW), `docs/plan/kompatibilitaetsmatrix.md`, „Seit `68598df` ändern nur KDoc, kein Verhalten: `config/ConfigFiles.kt` und `protocol/dispatch/RequestLoop.kt`“:** Der Branch ändert zusätzlich den KDoc von `protocol/session/ConnectionState.kt` und `runtime/ConnectionWiring.kt`; die Aufzählung nennt sie nicht, die Passung per `git diff --stat 68598df..HEAD -- src/main` stimmt nicht.
* **L-2 (LOW), Matrix, „Simulations-Backend mit `person`, `typen`, `viele`, `kaputt`“:** Der Testserver führt `breit`, nicht `viele`.
* **L-3 (LOW, 3.7), `tools/mk/e2e.mk`, Hilfezeile von `make e2e`:** Sie nennt vier Stufen, der Durchgang hat sieben.
* **L-4 (LOW, [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)), `tools/e2e-selbsttest.sh`:** Der Ersatz-`make` protokolliert nur das Target, nicht `KATEGORIE`; eine Vertauschung der Kategorie in Stufe 5, 6 oder 7 fällt nicht auf. Der Abbruch wird nur für Stufe 1 und 2 geprüft, nicht für eine rote Stufe 5 bis 7.
* **L-5 (LOW, 3.8), `LueckenTests.cs` Messzeile „AccessToken (FeatureExt FEDAUTH): “, `AnmeldungTests.cs` Testname `Pooling_nutzt_die_Verbindung_nach_dem_Zuruecklegen_wieder`:** Messzeile und Testname sind stärker als die Assertion. AccessToken endet mit 18456 auch dann, wenn die FeatureExt nie ankommt; der Pooling-Test prüft nur, dass die zweite Nutzung gelingt. Der Beleg liegt in den Log-Prüfungen des Skripts; ein `dotnet test` außerhalb des Skripts trägt die Aussage nicht.
* **I-1 (Planner):** Der DoD-Wortlaut („in den ADRs bzw. im Clientprofil nachgetragen“; „ein Gate“ gegenüber [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md); Slice-Text „[ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) (`Proposed`)“ in §4 und §6) weicht von der Umsetzung ab. Die Nachträge in Matrix und KDoc sind mit §3.5 vereinbar; der DoD-Text ist anzupassen.
* **I-2 (Kopplung):** `KompatibilitaetsServer.kt` koppelt an `internal`-Typen (`Wiring`, `RequestSource`, `ServerLog`, `runServer`), wie der bestehende `integrationsServer`; mit dem öffentlichen Einstieg ist die Datei als zweiter Nutzer mitzuführen. `RecordingBackend` ist Eigencode ohne Kotlin-Test; sein Logformat tragen nur die Greps im E2E-Skript.
* **I-3:** Die neue Herkunftsstufe „gemessen (Referenzclient, Serverprozess)“ steht in der Legende, wird aber in keiner Zeile als Stufe zitiert.
* **I-4:** Lücke 9 trägt „**gemessen**“ im Fettdruck, obwohl das RESETCONNECTION-Bit nicht beobachtet ist; Lücke 4 ebenso (Batch-Flag aus der Wirkung geschlossen). Die Einschränkung steht jeweils in derselben Zelle.
* **I-5:** `tools/kompatibilitaetstest.sh` ist durch keinen Selbsttest abgedeckt, auch nicht der Exit 2 bei falscher Kategorie.
* **I-6:** Die Messwerte der Matrix stammen aus einem einmaligen Lauf (offen benannt); `make matrix-zitate` prüft Abschnitt 7 nicht.

## Geprüft, ohne Befund

* `tools/kompatibilitaetstest.sh`: Bereitschaftsschleife (Obergrenze 120 s, Abbruch bei beendetem Container), `trap` auf EXIT/INT/TERM, kein `pkill`, Docker-only, Netz `--internal`, nichts gemountet, kein `TrustServerCertificate`, Gegenproben zum Namen in TDS 7.4 und 8.0, Log-Prüfung auf Geheimnisse (QA-03).
* `tools/mk/kompatibilitaet.mk`: nicht in `GATE_CHECKS`; Zeile in `harness/README.md` vorhanden; nur existierende Targets.
* `Dockerfile`-Stage `kompatibilitaetsserver`, `build.gradle.kts`-Task, `referenztest-dotnet/Dockerfile`: die Stage `test` kopiert `Kompatibilitaet` nicht, in `make gates` läuft nichts ohne `TDSWIRE_ZIEL`; die Änderungen lösen die Test-Gates erneut aus.
* `tools/e2e.sh`: Abbruch an der ersten roten Stufe unverändert; Kennungen der Stufen 5 bis 7 tragen ihre Texte; Anker in `e2e-abdeckung.md` stimmen.
* KDoc `ConnectionState`/`ConnectionWiring`: „Festlegung“ bleibt für die Reihenfolge; TLS-Version und Suite als nicht beobachtet benannt.
* Matrix 7a bis 7c: Messaussagen mit Einschränkung begleitet; keine Spec-Schicht erweitert; kein Accepted-ADR im Diff geändert (§3.5); kein `@Suppress`, keine Host-Toolchain.
