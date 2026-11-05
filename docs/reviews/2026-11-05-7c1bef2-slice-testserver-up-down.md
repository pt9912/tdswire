# Review-Report: slice-testserver-up-down (Commit 7c1bef2) — 2026-11-05

**Review-Art:** Code (gegen Plan, ADR, Hard Rules; nicht gegen die DoD)

**Gegenstand:** Commit 7c1bef2 (`tools/testserver.sh`, `tools/testserver-selbsttest.sh`, `tools/mk/testserver.mk`, `src/testserver/resources/manuell/sim.yaml`, `harness/README.md`)

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-05

**Eingangs-Kontext:** Plan `slice-testserver-up-down` (§1, §2, §3); [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md); [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md); [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit); `AGENTS.md` §3.1, §3.2, §3.7, §3.8, §5 Regel 7. Nutzerentscheidungen (Loopback voreingestellt, wählbar mit Warnung; Image `tdswire-testserver:lokal`) als gegeben gelesen. Der Implementer-Bericht wurde nicht übernommen. Die Mutationen habe ich im Kopf gegen den Skriptfluss durchgespielt, nicht gefahren. Einzig `docker create -p localhost:...` habe ich selbst gefahren.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Voreinstellung Loopback hat keinen Test, der sie belegt. Der Selbsttest verbindet sich über `127.0.0.1`, das auch bei einer Bindung an `0.0.0.0` gelingt. Wird die Voreinstellung zu `0.0.0.0` mutiert, bleibt der Selbsttest grün. Das Failure-Szenario: Testwerte `alice`/`secret` ohne Verschlüsselung liegen unbemerkt im Netz. Auch der Zweig `WARNUNG` (nicht Loopback) wird nie durchlaufen. | Maintainability (Eigencode ohne Test); Nutzerentscheidung Loopback | `tools/testserver.sh` · `HOST="${TESTSERVER_HOST:-127.0.0.1}"` und `WARNUNG: $HOST ist nicht Loopback` | ja, per Mutation von `make testserver-selbsttest` | Sicherheitsrelevante Voreinstellung ohne Test |
| F-2 | MEDIUM | Die Fehlerpfade des Aufräumens sind bis auf `port-belegt` unbelegt. Entfernt man `docker rm -f` im Pfad „der Server wurde nicht bereit“, den Wartepfad oder eines der `docker cp ... \|\| { docker rm -f ...; exit 1; }`, bleibt der Selbsttest grün. Nach einem Fehler bliebe ein toter Container zurück, und der nächste `up` meldet „existiert schon“. | Maintainability; Plan §2 Punkt 3 (nennt nur drei Zweige) | `tools/testserver.sh` · `testserver-up: der Server wurde nicht bereit; Log:` und `docker cp "$benutzer" "$NAME:/home/gradle/users.txt"` | ja, per Mutation | Aufräumzweig ohne Test |
| F-3 | MEDIUM | Die Konfigurationszweige sind ungetestet: `TESTSERVER_BESCHREIBUNG`, `TESTSERVER_BENUTZER_DATEI`, `REQUIRED` samt TLS-Dateien und die vier Prüfmeldungen am Anfang von `up`. Der Selbsttest fährt nur die Voreinstellung. Das Skript sagt „Nicht belegt: ... TLS“, schweigt aber zu eigener Beschreibung und eigener Benutzerliste. Auch die Dateirechte nach `docker cp` (Besitzer der Host-Datei, Leserecht des Container-Nutzers) sind nicht gemessen. | `AGENTS.md` §3.8 (Aussage trägt so weit wie ihr Beleg); Plan §2 Punkt 2 | `tools/testserver.sh` · `Nicht belegt: Annahme durch einen Client ausser dem` | ja | Zusage-Umfang nicht benannt |
| F-4 | MEDIUM | Die Mutation `port-belegt` ersetzt nur das `docker rm -f` im Zweig. Der Zweig selbst (`if ! docker start`) ist für den Ausgang redundant. Wird er entfernt, fällt der Lauf in den Pfad „nicht bereit“, der den Container ebenfalls entfernt und mit 1 endet. `port_belegt_ok` prüft nur Exit-Code und „kein Container“, nicht die Meldung „Port ... belegt?“. Die Mutation belegt also das `rm`, nicht den Zweig; die Beschreibung „greift am Zweig“ und die Überschrift „jede färbt genau die Prüfung ihres Zweigs rot“ sind breiter als die Messung. | Reviewer-Skill (Mutationsbeschreibung nennt gedachte Wirkung); `AGENTS.md` §3.8 | `tools/testserver-selbsttest.sh` · `Mutationen: jede faerbt genau die Pruefung ihres Zweigs rot` | ja, `if ! docker start` auf `if false` mutieren, Selbsttest bleibt grün | Mutation greift nicht am Zweig |
| F-5 | LOW | Die Mutation `zweiter-up` ändert zwei Dinge (`exit 1` zu `exit 0` und den Text `existiert schon`). Sie wird durch beide einzeln rot, und keiner der beiden Anteile ist für sich isoliert belegt. | Maintainability | `tools/testserver-selbsttest.sh` · `mutiere zweiter-up 's/exit 1/exit 0/; s/existiert schon/laeuft/'` | ja | Mutation mit zwei Änderungen |
| F-6 | LOW | Der Pfad `localhost` im `case` der Warnung ist toter Code. Docker lehnt `-p localhost:...` ab (`invalid IP address: localhost`, selbst gefahren), `up` endet vorher mit 1. `::1` steht ebenfalls ohne eckige Klammern da. | `AGENTS.md` §3.7 (beschreibt, was da ist) | `tools/testserver.sh` · `127.0.0.1 \| localhost \| ::1) ;;` | ja | Zweig für nicht erreichbaren Wert |
| F-7 | LOW | Der Zeiger in `harness/README.md` („Variablen, Testwerte und Warnung bei anderem Host: Kopf von `tools/testserver.sh`“) trägt nicht ganz. Der Kopf nennt die Variablen, aber weder den Testwert `alice`/`secret` noch die Warnung. Die stehen nur im Rumpf. Ebenso verspricht der Kopf von `tools/mk/testserver.mk` „Aufrufe zum Kopieren: `make help`“, `make help` zeigt aber nur Variablennamen, `TESTSERVER_VERSCHLUESSELUNG` und die TLS-Variablen fehlen. | `AGENTS.md` §3.7 (Rang-Zeiger) | `harness/README.md` · `Kopf von \`tools/testserver.sh\`` | nein | Zeiger trägt nicht den genannten Inhalt |
| F-8 | LOW | Die Kostenangabe „198 s mit Builds (Einzelmessung 2026-11-05)“ nennt weder Cache-Zustand noch Last noch Host. Die Nachbarzeilen unterscheiden „warm“ und „nach Quelländerung“ und nennen die Last. Die Zeilen für `testserver-up` nennen die Bauzeit bei Aufruf ohne Image gar nicht, obwohl `up` standardmäßig baut. | `AGENTS.md` §5 Regel 7 (Datum, Host, Bedingung); Reviewer-Skill (Zusage ohne gemessene Bedingung) | `harness/README.md` · `198 s mit Builds (Einzelmessung 2026-11-05)` | nein | Messung ohne Bedingung |
| F-9 | INFO | Bei SIGINT während `up` (Warteschleife bis 60 s) räumt nur der `EXIT`-Trap das Verzeichnis auf, ein bereits gestarteter Container bleibt. Der nächste `up` meldet dies klar (`make testserver-down`). Es ist keine Abweichung vom Plan. | Maintainability | `tools/testserver.sh` · `trap 'rm -rf "$scratch"' EXIT` | nein | Abbruch nicht abgedeckt |
| F-10 | INFO | Die Warnung bei nicht-Loopback erscheint nach dem Start, nicht davor; der Port ist dann schon veröffentlicht. Das entspricht „wählbar mit Warnung“. Hinweis für den Verifier und den Nutzer: die Entscheidung bleibt beim Nutzer. | Nutzerentscheidung | `tools/testserver.sh` · `WARNUNG: $HOST ist nicht Loopback` | nein | Warnung nach Wirkung |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `tools/mk/testserver.mk` (Targets, `.PHONY`, Variablenübergabe als Umgebung) | geprüft, ohne Befund |
| `tools/testserver-selbsttest.sh` Aufräumen (`trap`, Abbruch nach Build, Name- und Image-Kollisionen mit `tdswire-testserver:lokal`) | geprüft, ohne Befund |
| Docker-only (`AGENTS.md` §3.1) und Suppression-Verbot (`AGENTS.md` §3.2): nur `shellcheck disable=SC2086` im Skript, kein Kotlin berührt | geprüft, ohne Befund |
| [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) (kein Gate, kein `GATE_CHECKS`) | geprüft, ohne Befund |
| [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 4 (lokales Test-Image, kein Produkt-Image) | geprüft, ohne Befund |
| `src/testserver/resources/manuell/sim.yaml` | geprüft, ohne Befund. Der Server liest sie beim Start, der Selbsttest bestätigt den Login. |
| Mutationen `zweiter-up` und `down-ohne-container` (greifen am Zweig, Grundlage vorher grün) | geprüft, ohne Befund außer F-5 |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 4 |
| LOW | 4 |
| INFO | 2 |

**Finding-Klassen dieses Laufs:** Sicherheitsrelevante Voreinstellung ohne Test · Aufräumzweig ohne Test · Zusage-Umfang nicht benannt · Mutation greift nicht am Zweig · Zeiger trägt nicht den genannten Inhalt · Messung ohne Bedingung

## Verdikt

**Merge-blockierend:** nein, nach Nutzerentscheidung. F-1 bis F-4 sind MEDIUM ohne HIGH-Auslöser. Der Plan nennt nur drei Zweige mit Mutation, und diese sind erfüllt. Die Lücken liegen darüber hinaus (Voreinstellung Loopback, Fehlerpfade, Konfigurationszweige). Der Implementer behebt sie oder begründet sie in der Closure-Notiz.

**Übergabe:** Findings an den Implementer; Finding-Klassen in die Closure §7. DoD-Konformität prüft der Verifier.
