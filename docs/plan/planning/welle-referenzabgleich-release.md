# Welle welle-referenzabgleich-release: Fertiger Server: Referenzabgleich mit einer SQL-Server-Instanz, lauffähiges Artefakt, Dokumentation und Release-Schritte

**Lifecycle:** Diese Datei entsteht bei der **Eröffnung** der Welle und liegt
flach unter `docs/plan/planning/`; bei Closure wandert sie per `git mv` nach
`done/` (neben ihre `welle-<Kennung>-results.md`). Der Zustand ist die
Verzeichnis-Position — kein Status-Feld. **Geplante Wellen bekommen noch keine
Datei:** Sie stehen in der Roadmap unter *Nächste Wellen* und nirgends sonst —
zwei Positionen, nicht drei.

**Zielmeilenstein:** kein Meilenstein-Bezug.

**Verantwortlich:** pt9912. **Datum:** 2026-10-30.

---

## 1. Welle-Ziel

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht.

Der Server liegt als lauffähiges Artefakt in Docker vor ([TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)), trägt die MIT-Lizenz ([TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz)), ist mit dem Referenzclient Szenario für Szenario mit einer echten SQL-Server-Instanz verglichen (Live-Lauf und Mitschnitt-Lauf nach SPEC-045 mit dokumentierter Ausnahmeliste, ergänzend zu [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität)), seine Nutzung ist beschrieben, und der Weg zum Release ist ein dokumentierter Ablauf mit je einem benannten Wächter für den detekt-Stabil-Release ([ADR-0010](../adr/0010-detekt-pre-release.md)) und die vendored Baseline. Die Aussage gilt für eine Instanz der Version 2022 und TDS 7.4; TDS 8.0 ist dort nicht abgleichbar.

**Grenzen zu den Nachbarn (nicht doppeln).** Die Szenarien S1 bis S18 des Referenzclients gegen den Server schreibt [slice-qa-kompatibilitaet](open/slice-qa-kompatibilitaet.md); diese Welle führt sie gegen zwei Ziele aus. Make-Target, Container und Referenz-Seite des Vergleichslaufs D, die EULA-Prüfung und die Messung seiner Laufzeit liegen bei [slice-qa-reproduzierbarkeit](in-progress/slice-qa-reproduzierbarkeit.md); diese Welle ergänzt die Server-Seite und den Mitschnitt-Lauf. Die Wire-Messung von Tokenreihenfolge, DONE-Statusbits und RETURNVALUE-Zählung trägt hier [slice-referenzabgleich-mitschnitt](open/slice-referenzabgleich-mitschnitt.md); [slice-qa-reproduzierbarkeit](in-progress/slice-qa-reproduzierbarkeit.md) ist dazu mit Begründung und Verweis gekürzt.

**Register-Sichtung (Eröffnung, Schritt 2).** `observations/` durchgegangen: Treffer führen die Slices in §8. `BEO-TDS/messung-am-echten-sql-server-fehlt` (9×, geplant bei [slice-qa-reproduzierbarkeit](in-progress/slice-qa-reproduzierbarkeit.md)) und `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (7×, geplant bei [slice-qa-kompatibilitaet](open/slice-qa-kompatibilitaet.md)) haben ihren Träger; diese Welle schließt keine der beiden, sie liefert einen Teil der Messung (Wire, Server-Seite).

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün.
- Die Welle [welle-simulations-backend](welle-simulations-backend.md) liegt in `done/` (Server-Seite des Live-Laufs), und [slice-qa-reproduzierbarkeit](in-progress/slice-qa-reproduzierbarkeit.md) und [slice-qa-kompatibilitaet](open/slice-qa-kompatibilitaet.md) liegen in `done/` (Vergleichslauf D, Referenzinstanz, Szenarien).
- Weitere Voraussetzungen gelten je Slice und stehen in dessen §4 (für [slice-release-doku-prozess](open/slice-release-doku-prozess.md) die Welle [welle-betrieb-diagnose](welle-betrieb-diagnose.md)): sie verzögern einen Slice, nicht den Start der Welle.

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` grün, einschließlich des Laufzeittests und der Lizenzprüfung; Live-Lauf und Mitschnitt-Lauf grün gegen ihre Ausnahmeliste, oder die Closure-Notiz begründet die Nicht-Aufnahme in `make gates` mit der gemessenen Laufzeit.
- Das Mehr gegenüber den Slice-DoDs: Live-Lauf und Mitschnitt-Lauf laufen gegen das Laufzeit-Image aus [slice-release-artefakt-lizenz](open/slice-release-artefakt-lizenz.md), nicht gegen einen Testprozess, und die Schritte der Nutzer-Doku zum Start im Container sind an diesem Image nachvollzogen. Einzelne Slices belegen nur ihren Teil.
- Die Closure-Notiz führt die Aussagen der Welle in zwei Spalten, *gemessen* und *Festlegung*; jede Regel der Ausnahmeliste trägt ihre Art (*so gewollt* oder *Lücke* mit Verweis auf die offene Arbeit); die Ausgänge der Beobachtungen `messung-am-echten-sql-server-fehlt` und `vergleichslauf-d-ungeklaert` sind nachgeführt; die Nutzerentscheidungen (Aufnahme-Methode, Ablage, Versionsschema, Form des Wächters) sind getroffen und in den Slice-Notizen festgehalten.
- Closure-Notiz in `welle-referenzabgleich-release-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-referenzabgleich-live | Live-Lauf: derselbe Referenzclient gegen Referenzinstanz und Server mit Simulations-Backend, Ausnahmeliste | [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md) |
| slice-referenzabgleich-mitschnitt | Mitschnitt-Lauf: redigierte Paketmitschnitte der Referenzinstanz gegen den Server abspielen, feldweiser Vergleich, Wire-Messung | [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-03](../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0001](../adr/0001-tds-version-und-clientprofil.md) |
| slice-release-artefakt-lizenz | Laufzeit-Image, Laufzeittest und Lizenznachweis | [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| slice-release-doku-prozess | Nutzer- und Maintainer-Doku, Release-Schritte, Wächter für detekt-Stabil-Release und Baseline-Audit | [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0010](../adr/0010-detekt-pre-release.md) |

Reihenfolge: Der Live-Lauf legt Server-Seite, Schema und Ausnahmeliste; der Mitschnitt-Lauf nutzt sie und steht danach. Das Artefakt ist unabhängig von beiden und kann parallel laufen, ist aber die Grundlage für das Mehr der Closure (die Läufe gegen das Image). Die Doku steht zuletzt, weil sie Betrieb, Backend-Anbindung und Start im Container beschreibt, die gebaut sein müssen.

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: keine weitere Welle; sie ist das Ende der Planung dieses Repos bis zum Release.
- Wird blockiert von: [welle-simulations-backend](welle-simulations-backend.md) (Server-Seite), [slice-qa-reproduzierbarkeit](in-progress/slice-qa-reproduzierbarkeit.md) (Vergleichslauf D, Referenzinstanz), [slice-qa-kompatibilitaet](open/slice-qa-kompatibilitaet.md) (Szenarien), [slice-bootstrap-konfiguration](open/slice-bootstrap-konfiguration.md) (`main`), [welle-betrieb-diagnose](welle-betrieb-diagnose.md) (nur [slice-release-doku-prozess](open/slice-release-doku-prozess.md)).
- Berührt, blockiert aber nicht: [welle-qualitaet-grundlage](welle-qualitaet-grundlage.md). Sie schließt auf Basis von [slice-qa-reproduzierbarkeit](in-progress/slice-qa-reproduzierbarkeit.md) und [slice-qa-kompatibilitaet](open/slice-qa-kompatibilitaet.md); ob [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität) ohne den Referenzabgleich dieser Welle als belegt gilt, ist offen, denn das Lastenheft nennt den Abgleich „ergänzend“ und „vorläufig“ (Frage an den Nutzer).

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Die Szenarien S1 bis S18 selbst: [slice-qa-kompatibilitaet](open/slice-qa-kompatibilitaet.md). Make-Target, Container, EULA-Prüfung und Ressourcenmessung des Vergleichslaufs D: [slice-qa-reproduzierbarkeit](in-progress/slice-qa-reproduzierbarkeit.md).
- NuGet-Audit und Signaturprüfung der Abhängigkeiten des Referenztests: [slice-qa-dotnet-lieferkette-haerten](open/slice-qa-dotnet-lieferkette-haerten.md) (Carveouts [CO-001](../carveouts/CO-001-nuget-audit-aus.md), [CO-002](../carveouts/CO-002-nuget-signaturpruefung-fehlt.md)).
- Das Beheben gefundener Abweichungen im Server: je Befund ein Slice nach der Messung; Ausnahmeliste und Closure nennen sie als *Lücke* mit Verweis.
- Der Abgleich von TDS 8.0 und TLS 1.3 an einer Referenzinstanz: Version 2022 bietet beides nicht an ([ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md)); ein anderer Image-Tag ist Gegenstand von [slice-qa-reproduzierbarkeit](in-progress/slice-qa-reproduzierbarkeit.md).
- Veröffentlichung (Registry, Signierung, Software-Stückliste) und Orchestrierung: nicht verlangt; was ein Release davon braucht, entscheidet der Nutzer in [slice-release-doku-prozess](open/slice-release-doku-prozess.md).
- Last und Durchsatz: [slice-qa-performance](open/slice-qa-performance.md). Fuzz und Fragmentierung: [slice-qa-stabilitaet](open/slice-qa-stabilitaet.md).
- Hochverfügbarkeit, Replikation, Windows-Authentifizierung und alles andere aus den globalen Out-of-Scope-Punkten des Lastenhefts.
- Neue Anforderungen oder Änderungen am Lastenheft.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-referenzabgleich-release-results.md`
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
