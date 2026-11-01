# Welle welle-testserver-manuell: Testserver von Hand starten und stoppen: Make-Targets `up`/`down` und Test-Image


**Lifecycle:** Diese Datei entsteht bei der **Eröffnung** der Welle und liegt
flach unter `docs/plan/planning/`; bei Closure wandert sie per `git mv` nach
`done/` (neben ihre `welle-<Kennung>-results.md`). Der Zustand ist die
Verzeichnis-Position — kein Status-Feld. **Geplante Wellen bekommen noch keine
Datei:** Sie stehen in der Roadmap unter *Nächste Wellen* und nirgends sonst —
zwei Positionen, nicht drei.

**Zielmeilenstein:** kein Meilenstein-Bezug.

**Verantwortlich:** pt9912. **Datum:** 2026-11-01.

---

## 1. Welle-Ziel


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht.

Ein Mensch kann den Testserver auf seinem Rechner mit einem Make-Target starten, mit einem eigenen Client (Port und Anmeldung stehen in der Doku) gegen die Bibliothek testen und mit einem Make-Target wieder stoppen; das Image ist ein Test-Image und kein Produkt ([ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md), [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)). Heute startet der Testserver-Container nur in den Läufen `make laufzeittest`, `make simulations-integration` und `make kompatibilitaet`: in einem internen Netzwerk, ohne veröffentlichten Port, mit Stop am Ende des Laufs. Nutzerwunsch vom 2026-11-01: manuelles Testen, auch auf einem anderen Rechner; deshalb steht der Auftrag hier und nicht in einer Sitzungsnotiz.

**Grenzen zu den Nachbarn (nicht doppeln).** Die Stage `testserver` und der Klassenpfad bestehen ([slice-jar-ohne-main-und-image](done/slice-jar-ohne-main-und-image.md)); diese Welle ergänzt Start im Hintergrund, veröffentlichten Port und Stop, ersetzt aber keinen der E2E-Läufe. Nutzer-Doku zur Einbettung der Bibliothek und Release-Schritte liegen bei [slice-release-doku-prozess](open/slice-release-doku-prozess.md) ([welle-referenzabgleich-release](welle-referenzabgleich-release.md)).

**Register-Sichtung (Eröffnung, Schritt 2).** `observations/` durchgegangen: Treffer ist `BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen` (1×; Übergabe der Benutzerliste an den Container, hier für den manuellen Lauf mit einem Testwert berührt, nicht gelöst). Weitere Treffer: keine.

## 2. Trigger (Welle startet)


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün.
- [slice-jar-ohne-main-und-image](done/slice-jar-ohne-main-und-image.md) liegt in `done/` (Stage `testserver`, Testserver nur über die öffentliche Fläche).

## 3. Closure-Trigger (Welle schließt)


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` grün.
- Das Mehr gegenüber den Slice-DoDs: ein Mensch, der den Plan nicht geschrieben hat, startet von einem frischen Klon aus nur nach der Doku den Testserver mit `make testserver-up`, meldet sich mit einem eigenen Client an, liest die Ausgabe der Anfrage, stoppt mit `make testserver-down` und findet danach weder Container noch Netzwerk noch Port belegt. Die Closure-Notiz nennt Rechner, Last und das benutzte Client-Programm. Einzelne Slices belegen nur ihren Teil (Targets mit Rohbytes-Login; Doku ohne Lauf).
- Closure-Notiz in `welle-testserver-manuell-results.md`.

## 4. Slices in dieser Welle


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-testserver-up-down | Make-Targets `testserver-up` und `testserver-down` (dazu `-logs`, `-status`), Test-Image, veröffentlichter Port, Konfiguration, Selbsttest | [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) |
| slice-testserver-manuell-doku | Anleitung zum manuellen Test: Befehle, Port, Anmeldung, TLS, Beispiel-Tabellen, Beispielaufrufe mit Clients, Grenzen | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) |

Reihenfolge: Die Targets zuerst; die Doku beschreibt gebaute Targets und steht danach. Den Nachvollzug (Closure-Trigger) liefert die Welle, nicht ein Slice.

## 5. Abhängigkeiten


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: keine Welle.
- Wird blockiert von: nichts außer den Triggern oben. Berührt, blockiert aber nicht: [welle-betrieb-diagnose](welle-betrieb-diagnose.md) (Log-Adapter im Testserver: [slice-betrieb-log-adapter-testserver](open/slice-betrieb-log-adapter-testserver.md) ändert die Ausgabe, die `testserver-logs` zeigt).

## 6. Out-of-Scope für diese Welle


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Ein Produkt-Image, ein Server-Tool als Produkt, Veröffentlichung in einer Registry, Signierung: [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) schließt ein Produkt-Image aus; das Image dieser Welle wird nur lokal gebaut und gehört nicht zu `make gates`, `make e2e` oder der CI.
- Last, Durchsatz und Dauerbetrieb: [slice-qa-performance](open/slice-qa-performance.md).
- Verschlüsselung für echte Daten und Benutzerverwaltung im Betrieb: der manuelle Lauf nimmt Testwerte (`AUTH_PLAIN_TESTMODE`), kein Betriebsweg; Dateirechte der Benutzerliste im Betrieb bleiben bei `BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen`.
- Der Vergleichslauf D gegen einen echten SQL Server: bleibt ein eigenes Make-Target mit EULA-Entscheidung des Nutzers.
- Änderungen an der Bibliothek oder an `src/main`.

## 7. Closure-Notiz


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-testserver-manuell-results.md`
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
