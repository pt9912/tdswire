# Review-Report: slice-testserver-manuell-doku (Commit 76c72d8) — 2026-11-05

**Review-Art:** Doku (gegen Plan, ADR, Hard Rules; nicht gegen die DoD)

**Gegenstand:** Commit 76c72d8 (`docs/user/testserver-manuell.md`, `README.md`, Kopf von `docs/plan/planning/done/slice-testserver-manuell-doku.md`)

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-05

**Eingangs-Kontext:** Plan `slice-testserver-manuell-doku` (§1, §2, §3); [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md); [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md); geschlossene Vorstufe [slice-testserver-up-down](../plan/planning/done/slice-testserver-up-down.md) (§7 Grenzen); `SPEC-010`, `SPEC-035`, `SPEC-036` in [spec/spezifikation.md](../../spec/spezifikation.md); `AGENTS.md` §3.7, §3.8. Der Implementer-Bericht wurde nicht übernommen. Ausgaben und Meldungen habe ich wörtlich gegen `tools/testserver.sh` und `tools/mk/testserver.mk` gelesen; **gefahren habe ich nichts** (kein Container, kein Selbsttest). Die Befunde stützen sich auf Lesen von Skript, Spec und Quelltext.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Der Abschnitt „Verschlüsselung `REQUIRED`“ sagt, `REQUIRED` verlange zusätzlich Schlüsselspeicher und Passwortdatei. Er verschweigt, dass die Voreinstellung der Benutzerliste das Verfahren `plain` nutzt (`printf 'alice\t{plain}secret\n'`) und `AUTH_PLAIN_TESTMODE=true` nur bei `TEST_UNENCRYPTED` gesetzt wird. Nach `SPEC-051` ist `plain` ohne Testmodus ein Startabbruch. Failure-Szenario: Wer der Anleitung folgt und `REQUIRED` mit Speicher und Passwortdatei setzt, bekommt nach Lesen von Skript und Spec „der Server wurde nicht bereit“ und weiß nicht warum. Auch der Abschnitt „Benutzerliste“ nennt `{plain}` ohne den Hinweis, dass es nur im Klartext-Testmodus gilt. Der Ausfall ist aus Skript und Spec abgeleitet, nicht gefahren. | `AGENTS.md` §3.8; Plan §2 Punkt 1 (Verschlüsselungsrichtlinie) | `docs/user/testserver-manuell.md` · `verlangt zusätzlich` | ja (Verifier: Start mit `REQUIRED` im Handlauf) | Aussage unvollständig gegenüber Skript und Spec |
| F-2 | MEDIUM | „Alles andere (Verbund, Aggregat, `ORDER BY`, …) endet mit `TDS_UNSUPPORTED_SQL`“ und in den Grenzen „die Liste ist abschließend“ stehen ohne Einschränkung. Nach `SPEC-036` ist die Abschließlichkeit eine Setzung („eine Lesart des Lastenhefts, vom Nutzer nicht bestätigt“), die Spec ist „vorläufig“, und die Scanner-Festlegungen sind „nicht am Server gemessen“. Die Einschränkung auf den Handlauf („nicht gemessen“) steht erst im Absatz darunter und nur für zwei Beispiele. Gemessen ist `INSERT` ohne Callback auf Backend-Ebene und ein `SELECT` auf eine Dateitabelle über Microsoft.Data.SqlClient. Ein Verbund oder `ORDER BY` ist weder an der Bibliothek noch am Container als `TDS_UNSUPPORTED_SQL` belegt, soweit ich sehe. | `AGENTS.md` §3.8; Reviewer-Skill (Zusage ohne gemessene Bedingung im selben Satz) | `docs/user/testserver-manuell.md` · `die Liste ist abschließend` und `Alles andere (Verbund, Aggregat` | ja (Verifier: Anfrage mit `ORDER BY` gegen den Handlauf) | Setzung als Tatsache formuliert |
| F-3 | LOW | Die Zeichenfolge in Beispiel 2 ist als „die des Integrationstests“ eingeführt. Der Test hängt `;Command Timeout=120` an (`SimulationBackendTests.cs`, `Verbinden()`); die Anleitung lässt es weg. Host und Port sind dort Parameter. Die Aussage „ist die des Integrationstests“ ist damit nur eine Teilmenge. Alle übrigen Teile (`User ID=alice;Password=secret;Encrypt=false;Pooling=false;ConnectRetryCount=0;Connect Timeout=15`) stimmen wörtlich. | `AGENTS.md` §3.8 | `docs/user/testserver-manuell.md` · `Die Zeichenfolge ist die des Integrationstests` | ja | Beleg schmaler als Zitat |
| F-4 | LOW | Der Ausgabeblock „Lauf am 2026-11-05 mit Port 14333“ ist keine Ausgabe eines Laufs, sondern eine Zusammenstellung: Ausgaben von `up`, `status`, `logs`, Client (zwei Läufe, davon einer mit anderer Umgebung) und `down` stehen in einem Block, mit Anmerkungen in Klammern. Jede Zeile stimmt wörtlich mit den Formaten in `tools/testserver.sh` überein (`bereit auf`, `Benutzer alice, Kennwort secret`, `Testwerte, keine Verschluesselung (TDS 7.4 im Klartext)`, `Stop mit … Log mit …`, `laeuft (…)`, `gestartet, Port 1433`, `gestoppt und entfernt`). Die Überschrift „Lauf“ im Singular behauptet mehr, als der Block ist. | Reviewer-Skill (Zusage ohne Bedingung); `AGENTS.md` §3.8 | `docs/user/testserver-manuell.md` · `Lauf am 2026-11-05 mit Port 14333 (frei):` | nein | Zusammenstellung als ein Lauf |
| F-5 | LOW | „Die Variablen und ihre Prüfungen stehen im Kopf von `tools/testserver.sh`“: der Kopf nennt die Variablen und ihre Voreinstellungen, die Prüfungen und ihre Meldungen stehen im Rumpf von `up`. | `AGENTS.md` §3.7 (Rang-Zeiger) | `docs/user/testserver-manuell.md` · `Die Variablen und ihre Prüfungen stehen im Kopf` | nein | Zeiger trägt nicht den genannten Inhalt |
| F-6 | INFO | Plan §3 verlangt, dass jeder Beispielaufruf vor der Aufnahme gegen einen laufenden `testserver-up` ausgeführt wird und die Ausgabe als Beleg in Slice §7 steht. §7 ist noch „nicht geschrieben“. Der Beleg für die 3,6 s und die Ausgabe liegt also nur in der Anleitung selbst. Beispiel 2 ist bewusst als nicht gemessen gekennzeichnet; die DoD verlangt „je einen Beispielaufruf für zwei Clients“. Ob eine nicht gemessene Zeichenfolge das erfüllt, entscheidet der Verifier, nicht ich. | Plan §3; Plan §6 (Client-Risiko) | `slice-testserver-manuell-doku.md` · `Noch nicht geschrieben; der Planner füllt die Sektion bei der Closure.` | nein | Beleg-Ablage offen |
| F-7 | INFO | `harness/README.md` ist unberührt; der Verweis auf die Anleitung fehlt dort bewusst (DoD-Punkt „Verweis aus `README.md` und `harness/README.md`“ ist offen). Die Zeilen zu `testserver-up`/`-down`/`-logs`/`-status` in §Sensors existieren und verweisen auf den Kopf von `tools/testserver.sh`. `README.md` trägt den neuen Verweis, Linkziel `docs/user/testserver-manuell.md` existiert. | Plan §2 | `harness/README.md` · `make testserver-up` | ja (`make docs-check`) | Offener DoD-Teil |
| F-8 | INFO | Die Anleitung nennt `TESTSERVER_BAUEN` und `TESTSERVER_WARTEN` nicht, beide stehen im Kopf des Skripts. Die Plan-§2-Liste verlangt sie nicht; wer die Bereitschaftsgrenze von 60 s oder das Überspringen des Baus braucht, findet sie nur dort. | Maintainability | `docs/user/testserver-manuell.md` · `Die Variablen und ihre Prüfungen` | nein | Variable ohne Doku-Zeile |

## Gegenprüfungen ohne Befund (Schwerpunkte)

| Bereich | Ergebnis |
|---|---|
| Befehle und Meldungen gegen `tools/testserver.sh`, `tools/mk/testserver.mk` (Wortlaut: `existiert schon`, `zuerst 'make testserver-down'`, `WARNUNG: <Host> ist nicht Loopback; der Server ist im Netz erreichbar`, `nicht gestartet`, `Benutzerliste aus`) | geprüft, ohne Befund |
| Variablennamen `TESTSERVER_*`, Voreinstellungen (`127.0.0.1`, `1433`, `alice`/`secret`, `TEST_UNENCRYPTED`, `tdswire-testserver`, `tdswire-testserver:lokal`), Container-Port immer 1433 | geprüft, ohne Befund |
| `SPEC-010`: Voreinstellung der Bibliothek `REQUIRED`, Testserver-Handlauf `TEST_UNENCRYPTED`; die Gegenüberstellung ist sauber benannt (vorbehaltlich F-1) | geprüft, ohne Befund |
| Beispiel-Tabellen gegen `src/testserver/resources/manuell/sim.yaml` und die Typabbildung in `SPEC-035` (`identifier` Int32 nicht nullbar, `integer`+`required`, `decimal(10,2)`, `date`, `text`) | geprüft, ohne Befund |
| Zitierte Tests: `Leeres_Ergebnis_hat_Metadaten_und_keine_Zeile` (liest `SELECT Id, Name FROM leer`, 2 Spalten, keine Zeile) und `ohne Callback endet die Anweisung mit TDS_UNSUPPORTED_SQL` existieren | geprüft, ohne Befund |
| Rohbytes-Client: `TDSWIRE_ZIEL`, `TDSWIRE_PASSWORT` (Voreinstellung `secret`), Ausgaben `Login ok`/`Login fehlgeschlagen`, Exit 1; `--network host` wie im Selbsttest | geprüft, ohne Befund |
| Benutzerliste (`Benutzername<TAB>{plain}Kennwort`, UTF-8) gegen `SPEC-051`; „nicht gemessen“ für den Login mit eigener Liste stimmt zum Selbsttest (nur Exit 0 und Meldung) | geprüft, ohne Befund |
| Out-of-Scope Windows/Kerberos/Entra gegen Lastenheft; Grenzen gegen §7 der Vorstufe (TLS im Handlauf, eigene Liste, `docker cp`, SIGINT) | geprüft, ohne Befund |
| [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) (kein Gate) und [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) (kein Produkt, lokales Image) | geprüft, ohne Befund |
| Kommentar-/Zustandsregel `AGENTS.md` §3.7: Anleitung nennt Zustand, keine Chronik; „Nicht belegt (Stand: …)“ trägt Datum | geprüft, ohne Befund |
| `README.md` (eine Zeile) und Slice-Kopf `Verantwortlich` | geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 2 |
| LOW | 3 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** Aussage unvollständig gegenüber Skript und Spec · Setzung als Tatsache formuliert · Beleg schmaler als Zitat · Zusammenstellung als ein Lauf · Zeiger trägt nicht den genannten Inhalt · Beleg-Ablage offen · Offener DoD-Teil · Variable ohne Doku-Zeile

## Verdikt

**Merge-blockierend:** nein, nach Nutzerentscheidung. F-1 und F-2 sind MEDIUM ohne HIGH-Auslöser. Der Implementer behebt sie oder begründet in der Closure-Notiz. F-1 betrifft zusätzlich das Verhalten von `tools/testserver.sh` bei `REQUIRED` mit der Voreinstellung der Benutzerliste; ob das Skript oder die Anleitung geändert wird, entscheidet nicht der Reviewer.

**Übergabe:** Findings an den Implementer; Finding-Klassen in die Closure §7. DoD-Konformität (inklusive F-6, F-7) prüft der Verifier.
