# Slice slice-qa-vergleichslauf-d-messungen: Vergleichslauf D, Messreihe am Wire, Rest nach dem Mitschnitt-Lauf: Fehler im RPC, Zweitfälle für Ordinal und `curCmd`, ENVCHANGE bei `RESETCONNECTION`, Anmeldung am Draht und SESSIONSTATE

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) (`Withdrawn`), [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Proposed`), [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)

**Berührte Spec-Stellen:** SPEC-030 · SPEC-031 · SPEC-045 · SPEC-050

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Aussagen über die Antworten eines echten SQL Servers, die der Mitschnitt-Lauf nicht gemessen hat, sind je Punkt von *Setzung* auf *gemessen* gehoben oder als Abweichung geführt: Fehler in `sp_executesql` (Abschluss, Statusbits) und Stellung von COLMETADATA hinter RETURNVALUE, zweite Messfälle für das Ordinal der RETURNVALUE und für `curCmd` (Messauftrag von [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md)), ENVCHANGE bei `RESETCONNECTION`, die Anmeldung am Draht mit ENVCHANGE und die Verhandlung der Sitzungswiederaufnahme (SESSIONSTATE, A-18); die Mitschnitte der Messung liegen redigiert im Repo.

**Stand nach dem Mitschnitt-Lauf (2026-11-06).** [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) hat Proxy, Redaktion und 17 Mitschnitte je Instanz geliefert (`docs/messungen/referenzabgleich.md`, Abschnitt „Mitschnitt-Lauf“, Ergebnis 1 bis 6). **Gemessen und damit nicht mehr Gegenstand dieses Slice:** Tokenreihenfolge der RPC-Antwort mit Ausgabewert (Ergebnis 1), Statusbits der DONE-Familie bei statement- und batch-abbrechendem Fehler im Batch (Ergebnis 2), Zählung der RETURNVALUE (Ergebnis 3, mit der Grenze „ein Fall je Instanz“), Cancel-Antwort und ATTENTION (Ergebnis 5), ERROR bei Fehler vor und nach Zeilen (Ergebnis 6). **Entscheidung des Nutzers vom 2026-11-06** („deine empfehlungen“): die Aufnahme-Methode (mitschneidender Proxy) ist bestätigt, der Start-Trigger ist erfüllt; die Bau-Seite der gemessenen Konflikte liegt in [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md).

**Zuordnung der Lücken aus dem Mitschnitt-Lauf (Folge-Slices, Register `BEO-TDS/mitschnitt-luecken-ohne-traeger`).** A-10 (`curCmd`) und der RETURNSTATUS-/Ordinal-Teil von A-16 → [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md); A-11 (Länge 17 statt 9 im TYPE_INFO von `decimal(10,2)`) → [slice-codec-datentypen-erweiterung](../open/slice-codec-datentypen-erweiterung.md), die Ursache ist vorher zu untersuchen; A-15 (`WAITFOR`, Abbruch eines wartenden Befehls) → der Slice-Kandidat „erkannte Anweisungsformen erweitern“ (`BEO-TDS/anweisungsformen-des-simulations-backends-enger-als-der-server`; **nicht angelegt**, ohne Kennung, nach der Lesart-Entscheidung des Nutzers dort); A-18 (SESSIONSTATE bei `ConnectRetryCount=1`, nur Instanz 2022) und die Anmeldung am Draht (`BEO-TDS/anmeldung-am-draht-nicht-aufgenommen`) → **dieser Slice**, Liefer-Punkt 3. Ob der Server dieses Repos SESSIONSTATE senden soll, entscheidet der Nutzer nach der Untersuchung.

**Herkunft.** Dieser Slice ist der Träger, den vier Entscheidungen nennen: [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) (`Accepted`: Spec-Lücken 2 und 3, Reihenfolge von RETURNVALUE und RETURNSTATUS, COLMETADATA nach RETURNVALUE), [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) (zur Planung `Proposed`: DONEPROC im Fehlerfall der RPC-Antwort ist Setzung ohne Messung; jetzt `Withdrawn`, die Frage führt [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), `Proposed`), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (`Accepted`: ENVCHANGE bei `RESETCONNECTION` offen, Cancel-Antwort R11 und R12 Vorbild) die Beobachtung `BEO-TDS/messung-am-echten-sql-server-fehlt` und (Nutzerentscheid 2026-11-06, Träger-Vorschlag übernommen) die Beobachtung `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab`: Die Spezifikation legt fest, dass der Sequenzierer bei statement-abbrechenden Fehlern `DONE` ohne `MORE` schreibt; der echte Server sendet nach dem Stand der Spezifikation `DONE` mit `MORE` und `ERR`. Dieser Slice liefert die Messung, nicht den Bau. Er entstand am 2026-11-01 aus der Zerlegung von [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md), das den Container, das Target und die Ausnahmeliste liefert.

**Methode.** Wie die Messreihe R in [ADR-0007](../../adr/0007-fehler-und-attention.md): der Referenzclient spricht über einen mitschneidenden TCP-Proxy im Client-Container mit dem echten Server; der Proxy zerlegt die TDS-Pakete und die Tokens. Das ist die Methode des Mitschnitt-Slice (gebaut als mitschneidender TCP-Proxy, `referenztest-dotnet/Mitschnitt/Relais.cs`); der Nutzer hat sie am 2026-11-06 bestätigt („deine empfehlungen“). Neu ist für Liefer-Punkt 3 eine TLS-beendende Betriebsart: der bisherige Proxy liest ab der ersten Anfrage den Klartext und sieht die verschlüsselte Anmeldung nicht. Anders als beim Spike von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) bleibt der Messcode im Repo, damit die Messung wiederholbar ist.

**Übernimmt (aus dem Vorgänger-Schnitt):** den Wire-Teil des Umfangs von [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) (Antwortseite des Codecs); [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) hatte diesen Teil zuvor für sich übernommen, siehe §6.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Container, Make-Target, Digest-Pin, Ausnahmeliste, Messung von Laufzeit und Bedarf, EULA-Entscheidung, Anmelde-Szenario und TDS-8.0-Tag — [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md); dieser Slice setzt sie voraus (Start-Trigger).
- Das Abspielen von Mitschnitten gegen den Server dieses Repos und der feldweise Vergleich — [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md); hier wird gemessen, was der echte Server sendet, nicht was der Testserver sendet. Das Produkt ist die Bibliothek, der Server ein Testserver ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)); dieser Lauf spricht ihn nicht an.
- Das Ändern von Code, Spezifikation oder `Accepted`-ADRs aufgrund eines Befunds — je Befund ein Slice oder eine Folge-ADR nach der Messung. Insbesondere ändert dieser Slice keine ADR; er liefert die Messung, auf die der Nutzer entscheidet ([ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) ist `Withdrawn`, [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) `Proposed`, Entscheidung beim Nutzer). **Ausnahme (Planner-Entscheid der Closure, Verifikation V-2/V-3):** ein Messvermerk in `spec/spezifikation.md` („am echten Server gemessen: …“, Verweis auf das Messprotokoll) ist zulässig, wenn er die Zusage des Abschnitts nicht berührt; Zusagen ändert der Bau-Slice. Den Bau der gemessenen Konflikte (`curCmd`, `MORE`, RETURNSTATUS, Ordinal) trägt [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md).
- Der Bau der Cancel-Zwei-Nachrichten-Form des echten Servers — [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 1 baut nur `DONE[ATTN]`; hier wird nur gemessen. Der Referenzclient-Lauf gegen den Server dieses Repos gehört zu [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md).
- Der Referenzclient mit `Pooling=true` gegen den Server dieses Repos (Messweg (b) in [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)) — [slice-resetconnection-zuruecksetzen](../done/slice-resetconnection-zuruecksetzen.md); dort steht Messweg (a) als Folge, die dieser Slice liefert.
- Der Bau zur Messung der Statusbits bei statement-abbrechenden Fehlern im Batch (`Failure` um die Klasse erweitern, Sequenzierer angleichen) — [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md); gemessen ist er im Mitschnitt-Lauf (Ergebnis 2).
- A-11 (Länge im TYPE_INFO von `DECIMALN`) und A-15 (`WAITFOR`) — Zuordnung oben; hier wird nichts gemessen, was der Mitschnitt-Lauf schon zeigt.
- Statement-abbrechende Fehler im RPC und ungültig kodierte RPC-Nachrichten am echten Server — [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) nennt sie als nicht gemessen und ohne benannten Verbraucher; sie bleiben im Register (`BEO-TDS/offene-punkte-ohne-traeger`).

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

Liefer-Punkte (drei):

- [x] **Antwortseite im RPC und Zweitfälle:** Mit dem vorhandenen Proxy und weiteren Szenarien in `referenztest-dotnet/Vergleich/MessungWireTests.cs` (Aufnahme, beide Instanzen, redigiert, mit Herkunft; **Abweichung vom Plan-Wortlaut:** der Wortlaut nannte `MitschnittLaufTests.cs`, geliefert ist ein eigenes Messformat unter `Mitschnitt/messung/`, das nicht abgespielt wird) gemessen: (a) **Fehler in `sp_executesql`**: mit welchem Token er endet (DONE oder DONEPROC, Statusbits), je ein Szenario für einen Fehler vor und einen nach Zeilen, und ob der Referenzclient beide Formen verschieden behandelt (**Grenze, nicht gemessen:** der echte Server sendet im RPC kein DONE, die Frage ist nicht messbar; gemessen ist die Wirkung der gesendeten Formen, W-1 bis W-3); (b) **Stellung von COLMETADATA hinter RETURNVALUE** (ein RPC, dessen Ergebnis nach dem Ausgabewert kommt; Spec-Lücke 3 aus [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md); **Grenze, nicht gemessen:** der Fall ist in keinem Szenario beider Instanzen eingetreten, ein erzwingendes Szenario ist nicht gefunden, die Lücke bleibt offen, W-4); (c) **Messauftrag von [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md)**: ein zweiter Fall für das Ordinal der RETURNVALUE mit mehreren Ausgabewerten (`sp_executesql` mit zwei Ausgabeparametern an verschiedenen Stellen der Aufrufliste und einem Wertparameter dazwischen, ein Ausgabeparameter vor einem Wertparameter, `InputOutput` mit NULL; `sp_prepexec` mit zusätzlichem Ausgabeparameter) und die Befehlskennung `curCmd` für Abschlüsse mit Zeilenzahl (INSERT, UPDATE, DELETE im Batch und im RPC) und für DONEINPROC. **Nutzerentscheidungen vom 2026-11-06** („deine Empfehlungen“): (1) `curCmd` für INSERT, UPDATE, DELETE und DONEINPROC bleibt ohne Aussage, bis dieser Punkt gemessen ist; (2) Fehler im RPC, COLMETADATA hinter RETURNVALUE und ENVCHANGE bei `RESETCONNECTION` bleiben in diesem Slice (Liefer-Punkte 1 und 2). Je Punkt steht *gemessen*, *Abweichung* oder *Grenze* im Messprotokoll (Messreihe am Draht, W-1 bis W-9; **Abweichung vom Plan-Wortlaut:** der Wortlaut nannte die Ausnahmeliste, die für abgespielte Mitschnitte gilt, die Messungen werden nicht abgespielt), die Kompatibilitätsmatrix trägt den Stand, und das Messprotokoll nennt die Grenze (eine Aufnahme je Instanz). **Entscheidungs-Konsequenz, nicht Teil dieses Slice:** [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) ist zurückgezogen (`Withdrawn`, Nutzerentscheid 2026-11-06); die Frage führt [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Proposed`, Option A empfohlen), der Nutzer entscheidet noch; die Closure-Notiz nennt die Messzeile (W-1 bis W-3) und diese Entscheidung als offenen Punkt.
- [x] **Zurücksetzen:** ENVCHANGE im ersten Antwortpaket nach `RESETCONNECTION` (Pooling, zweite Nutzung der Verbindung): Typ und Inhalt aus dem Mitschnitt, oder die Begründung, warum der Proxy es nicht liefert ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Messweg (a)); ein Befund „Token unbekannt“ ist kein Ergebnis (der Zerleger kennt das Token nicht ohne Erweiterung). Das Ergebnis steht im Messprotokoll (W-7; Abweichung vom Plan-Wortlaut „in der Ausnahmeliste“, Grund wie in Punkt 1).
- [x] **Anmeldung am Draht und SESSIONSTATE (A-18):** Der Proxy wird um eine TLS-beendende Betriebsart erweitert (Setzung des Planners: Aufnahme von PRELOGIN, LOGIN7 und der Anmeldeantwort mit ENVCHANGE hinter einem eigenen Zertifikat des Proxys; Kennwort und Prüfwert werden redigiert, ein Test belegt die Redaktion mit Eingaben, die beides enthalten). Gemessen für beide Instanzen: Antwort der Anmeldung (LOGINACK, ENVCHANGE-Folge, INFO, DONE), und ob die Verhandlung der Sitzungswiederaufnahme (`ConnectRetryCount=1`, FEATUREEXT) der Grund dafür ist, dass die Instanz 2022 SESSIONSTATE sendet und die Instanz 2025 nicht (A-18); mit `ConnectRetryCount=0` und `1` als Gegenprobe. Das Ergebnis liegt als Messzeile mit Mitschnitt vor (W-8, W-9); ob der Server dieses Repos SESSIONSTATE senden soll, entscheidet der Nutzer danach, es ist kein Lieferinhalt dieses Slice. **Ergebnis zur Frage nach dem Grund:** die Verhandlung ist Voraussetzung (ohne sie sendet keine Instanz SESSIONSTATE), nach `SET` senden beide Instanzen; die Ursache dafür, dass nach dem Abbruch nur 2022 sendet, ist **nicht geklärt** (Lücke, Bedeutung der Zustandskennung 254 nicht festgestellt).

Nachweis-Pflichten für alle drei Punkte:

- [x] Jede Aussage steht an einem Test, einem Mitschnitt oder ist als Festlegung gekennzeichnet; die Zuordnung *Messung | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`); Nachträge in der Spezifikation und in ADRs führt dieser Slice nicht aus, ausgenommen Messvermerke ohne Änderung der Zusage (§1); sie sind je Befund benannte Folge-Arbeit.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Werkzeuge, falls ein Target hinzukommt; Kompatibilitätsmatrix).
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
| Mitschneidender Proxy (`referenztest-dotnet/Mitschnitt/`): `Messung.cs` neu (TLS-beendender Proxy `TlsAufnahme`, Redaktion von LOGIN7, Ablage `MessDatei`), `MessAuswertung.cs` neu, `TokenLeser.cs` update (LOGINACK, FEATUREEXTACK, SESSIONSTATE, ENVCHANGE-Werte); `Relais.cs` und `Redaktion.cs` bleiben unverändert | neu, update | TLS-beendende Betriebsart für die Anmeldung (Punkt 3) als eigene Klasse, weil der bisherige Proxy die verschlüsselte Anmeldung nicht sieht und den Paketstatus (RESETCONNECTION) nicht hält; Tests `MessungEinheitTests.cs` und `MessungBelegTests.cs` neu (Happy/Boundary/Negative, `@spec SPEC-045`) |
| Messungen (`referenztest-dotnet/Mitschnitt/messung/<2022\|2025>/`, statt `aufnahmen/`) | neu | 22 Messungen je Instanz im eigenen Format `.messung` (PRELOGIN, LOGIN7 redigiert, Antworten, Status), mit Herkunft; werden nicht abgespielt, daher getrennt von `aufnahmen/`; außerhalb des Repos gesammelte Ausgaben (`mktemp -d`, `trap`), nur Redigiertes wandert ins Repo |
| `referenztest-dotnet/` | neu, update | `Vergleich/MessungWireTests.cs` neu (statt Änderung in `MitschnittLaufTests.cs`): Szenarien für Fehler im RPC, Ordinal, `curCmd`, Pooling mit zweiter Nutzung, Anmeldung, SESSIONSTATE; `TdsWire.Referenztests.csproj` (Kopierregel für `Mitschnitt/messung/**`); Kommentare in `Vergleich/ReferenzZiel.cs` und `Kompatibilitaet/Ziel.cs` auf den Ist-Zustand; Ziel und Zugangsdaten als Parameter wie in [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) |
| `tools/vergleichslauf-d.sh`, `tools/mk/vergleichslauf-d.mk`, `harness/README.md` | update | Läufe `wire`, `wire-2022`, `wire-2025` und `MESSUNG_NUR` als Parameter des Targets `make vergleichslauf-d` (additiv); `harness/README.md` nennt die Messreihe in der Zeile des Targets; kein Gate, nicht in `make e2e` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) |
| `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` und `docs/messungen/referenzabgleich.md` | update | Messzeilen und Befunde (W-1 bis W-9) stehen im Messprotokoll, nicht in der Ausnahmeliste: die Messungen werden nicht gegen den Server dieses Repos abgespielt, die Liste gilt für abgespielte Mitschnitte; die Datei ändert nur den Grund der fünf Regeln A-18 |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Zeilen, die „nicht am echten Server gemessen“ tragen, bekommen den gemessenen Stand oder bleiben mit Begründung |
| `spec/spezifikation.md` | update | nur der Messvermerk zu `RESETCONNECTION` (Status am Draht gesehen, Antwort des echten Servers beginnt mit ENVCHANGE Typ 18); die Zusage des Abschnitts bleibt unberührt |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive Testdatei-Zeilen mit der Anforderungs-Kennung in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) liegt in `done/` (Container, Target, Ausnahmeliste; erfüllt), die Wahl der Aufnahme-Methode ist entschieden (mitschneidender Proxy; vom Nutzer am 2026-11-06 bestätigt mit „deine empfehlungen“; erfüllt), und ein Verantwortlicher ist im Kopf eingetragen. Die EULA des Developer-Images ist vom Nutzer bestätigt (2026-11-06); der Lauf ist wie der Mitschnitt-Lauf nur lokal.

**Stand des Start-Triggers:** Proxy, Redaktion und 17 Mitschnitte je Instanz liegen im Repo ([slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md)); die DoD in §2 ist auf die offenen Messpunkte gekürzt (2026-11-06). Dieser Slice ist der Träger des Messauftrags (Zweitfälle) für [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md), dessen Start diese Messung abwartet (Ordinal).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder die TLS-beendende Betriebsart des Proxys sprengt den Rahmen; Schnittvorschlag: Punkt 3 (Anmeldung am Draht und SESSIONSTATE) als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): die Aufnahme ist mit Docker-only und ohne Mount nicht möglich (Rechte für Paketaufnahme), oder der Container aus [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) steht nicht zur Verfügung; der Nutzer entscheidet über eine Ausnahme als Carveout.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, der Review-Report liegt vor, und die Closure-Notiz trägt je Messpunkt (Fehlerabschluss in `sp_executesql` vor und nach Zeilen, Stellung von COLMETADATA hinter RETURNVALUE, Ordinal mit mehreren Ausgabewerten, `curCmd` für Abschlüsse mit Zeilenzahl und DONEINPROC, ENVCHANGE bei `RESETCONNECTION`, Anmeldeantwort mit ENVCHANGE, SESSIONSTATE und Verhandlung) ein Ergebnis (gemessen mit Beleg, Abweichung oder nicht lieferbar mit Grund), die Grenzen (zwei Instanzen, Digests, TDS 7.4 mit TLS-beendendem Proxy oder unverschlüsselt, je nach Aufnahme) und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Doppelte Zuständigkeit mit [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md).** Der Mitschnitt-Slice liegt in `done/` und hat Proxy und Mitschnitte geliefert; dieser Slice nutzt sie. — **Ausgang:** entfallen: der Wire-Teil ist seit der Closure des Mitschnitt-Slice (2026-11-06) auf die offenen Messpunkte geschnitten, die Zuständigkeiten sind in §1 getrennt.
- **Frage an den Nutzer — Aufnahme-Methode.** Ein mitschneidender Proxy verändert den Verkehr möglicherweise. — **Ausgang:** entfallen: vom Nutzer am 2026-11-06 bestätigt („deine empfehlungen“); die Verschiebung um einen Zwischenschritt je Richtung ist im Messprotokoll als Grenze genannt.
- **Die Messung kann ein Ergebnis ergeben, das eine `Accepted`-ADR berührt** (Reihenfolge der Tokens, Cancel-Antwort, ENVCHANGE). Hard Rule 3.5: kein Eingriff in die ADR; der Befund führt zu einer Folge-ADR oder einem Folge-Slice. — **Ausgang:** eingetreten: die Messung widerspricht der Spezifikation (Ordinal, `curCmd`, Fehlerabschluss im RPC) und berührt [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) (Lücke 3 bleibt offen) und [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Messweg (a) gefahren, ENVCHANGE Typ 18 gemessen); keine `Accepted`-ADR ist geändert; der Befund geht als Bau und Spezifikations-Angleichung an [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md), der Fehlerabschluss als Frage an [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md).
- **[ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) war `Proposed`, der Bau weicht von ihr ab.** Die Messung entscheidet nicht; es entscheidet der Nutzer. — **Ausgang:** weiter offen: der Nutzer hat [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) zurückgezogen (`Withdrawn`, 2026-11-06); die Frage führt [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Proposed`, Option A empfohlen, nicht entschieden). Bis zur Entscheidung trägt der Quelltext (`failedDone()`) eine Setzung ohne Messung; der Träger-Eintrag steht im Bau-Slice (Nr 6 und 7, Start-Trigger: [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) `Accepted`).
- **Der Proxy zeigt nur, was er zerlegen kann.** Der Parser der Messreihe R kennt das ENVCHANGE bei `RESETCONNECTION` nicht ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)); ein Befund „Token unbekannt“ ist kein Ergebnis. — **Ausgang:** entfallen: der Zerleger kennt ENVCHANGE Typ 18, LOGINACK, FEATUREEXTACK und SESSIONSTATE (Tests `MessungEinheitTests`); verbleibende Grenze: der RPC-Zerleger der Auswertung kennt nur int und nvarchar als Parametertypen (Messprotokoll, Grenzen).
- Der Slice kann mehr als drei Liefer-Punkte tragen, falls die TLS-beendende Betriebsart des Proxys (Punkt 3) mehr Aufwand ist als die Messungen. — **Ausgang:** entfallen: es blieben drei Liefer-Punkte; die TLS-beendende Betriebsart (`TlsAufnahme`) lag im Rahmen von Punkt 3.
- **Die Anmeldung am Draht liegt hinter TLS.** Ein Proxy, der TLS beendet, ist ein anderer Messaufbau als der bisherige (der Client verbindet mit dem Proxy, nicht mit dem Server; der Proxy braucht ein Zertifikat, das der Client annimmt), und das Zerlegen von FEATUREEXT und SESSIONSTATE kann die Grenzen des Zerlegers (`TokenLeser`) zeigen. — **Ausgang:** eingetreten und aufgelöst: der erste Durchlauf brach im Szenario `reset-ohne-zustand` ab (zweite Open-Phase, `Number 25`); der Proxy lehnt weitere Verbindungen seitdem sofort ab und zählt sie, der Wiederholungslauf der Pooling-Szenarien (6 Läufe, 0 Abbrüche, 0 weitere Verbindungen) ist grün; die Ursache des einen Abbruchs bleibt Hypothese (Messprotokoll, Bedingungen).
- **Der Messauftrag für [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md) blockiert dessen Start (Ordinal).** Liefer-Punkt 1 ist deshalb vor den anderen zu bauen. — **Ausgang:** entfallen: der Messauftrag ist geliefert (Ordinal W-5, `curCmd` W-6); der Start-Trigger des Bau-Slice hat den Messfall, zusätzlich hängen dessen Nr 6 und 7 an [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Accepted`).

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

Closure am 2026-11-06 (Planner). Zuerst der Stand der Rollen, danach die Entscheide und die Closure-Teile.

**Belege der Rollen.** Umsetzung `d50ffd6c`, Wiederholungslauf der Pooling-Szenarien `27a3af4e`, Nachbesserung `073ad171`. Review: [2026-11-06-d50ffd6c-slice-qa-vergleichslauf-d-messungen](../../../reviews/2026-11-06-d50ffd6c-slice-qa-vergleichslauf-d-messungen.md) (0 HIGH, 5 MEDIUM, 2 LOW, 5 INFO). Verifikation: [2026-11-06-073ad17-verifikation-slice-qa-vergleichslauf-d-messungen](../../../reviews/2026-11-06-073ad17-verifikation-slice-qa-vergleichslauf-d-messungen.md): Messdaten, Redaktion (44 Dateien am Inhalt) und Sensoren tragen, drei Redaktionsmutationen je rot; „nicht closure-fähig“ wegen des Slice-Textes (V-1 bis V-7), nicht wegen Code oder Messwerten. Die Nachbesserung `073ad171` hat die Verifikation gelesen und gefahren, einen eigenen Re-Review (Bedingung B-2) gab es nicht; das ist eine Grenze dieser Closure, kein Beleg.

**Nutzerentscheide vom 2026-11-06 (Wortlaut „deine empfehlungen“, Lesart: sie bestätigen die Empfehlungen des Planners).** (1) Methode bestätigt: mitschneidender Proxy, TLS-Beendigung im Proxy, TLS 1.2, `TrustServerCertificate=true` nur auf der Strecke zum Proxy. (2) Wiederholungslauf der Pooling-Szenarien gefahren (6 Läufe, 0 Abbrüche). (3) Alle sechs Konflikte der Messreihe mit dem Zielbild gehen zu [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md). (4) [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) ist zurückgezogen (`Withdrawn`), [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) ist `Proposed` (Option A empfohlen); der Nutzer hat nicht entschieden.

**Entscheide des Planners zu den Findings der Verifikation (Bedingung B-1).**

| Finding | Entscheid | Ausgang im Text |
|---|---|---|
| V-1 (MEDIUM) DoD-Wortlaut gegen umgeschriebenen Plan | Wortlaut an die Lieferung angleichen und die Abweichung kennzeichnen (nicht stillschweigend) | §2 Punkt 1, 2: `MessungWireTests.cs` und Messprotokoll W-1 bis W-9 statt `MitschnittLaufTests.cs` und Ausnahmeliste, je mit „Abweichung vom Plan-Wortlaut“; Grund: die Messungen werden nicht abgespielt, die Ausnahmeliste gilt für abgespielte Mitschnitte |
| V-2 (MEDIUM) Spezifikationsänderung gegen Ausschluss | Ausschluss um den Messvermerk ergänzt; die Zusage bleibt unberührt | §1 (Ausnahme), §2 Nachweis-Pflicht: nur „am echten Server gemessen: …“ mit Verweis auf das Messprotokoll |
| V-3 (MEDIUM) zweite Fundstellen in der Spezifikation | Messvermerke an allen Stellen gezogen (Handle-Verwaltung, `curCmd`, Login-Antwort, „17 aufgenommene Szenarien“, Ordinal in `TDS-FA-10.a`, Absatz zu den Messgrenzen der RPC-Antwort), ohne eine Zusage zu ändern; die Zusagen (Ordinal nach Ereignissen, `curCmd` ist 0) ändert der Bau-Slice | `spec/spezifikation.md`; Verlaufszeile vom 2026-11-01 bleibt (Zeitdokument) |
| V-4 (MEDIUM) Haken Liefer-Punkt 1, zwei Teile nicht gemessen | Haken bleibt für das Gemessene (Fehler im RPC, Ordinal, `curCmd`); die zwei Teile stehen als **Grenze, nicht gemessen** im Text, nicht als gemessen: (i) „DONE gegen DONEPROC verschieden behandelt“ ist nicht messbar, weil der echte Server im RPC kein DONE sendet; (ii) „COLMETADATA hinter RETURNVALUE“ ist in keinem Szenario eingetreten, Lücke 3 aus [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) bleibt offen. A-18: Voraussetzung gemessen, Ursache des Unterschieds nach dem Abbruch nicht | §2 Punkt 1 und 3; Tabelle unten; Register `BEO-TDS/mitschnitt-luecken-ohne-traeger` |
| V-5 (MEDIUM) Träger-Einträge im Bau-Slice fehlen | angelegt: Nr 5 bis 8 (ENVCHANGE bei `RESETCONNECTION`, RETURNSTATUS und RETURNVALUE im Fehlerfall, Fehlerabschluss im RPC, SESSIONSTATE), eingearbeitet in die vorhandenen drei Liefer-Punkte; Start-Trigger für Nr 6 und 7: [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) `Accepted`; SESSIONSTATE ist Nutzerentscheidung („soll der Server es senden?“), nicht Folge der Messung | [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md) §1, §2, §4, §6; [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md)-Verweise dort auf `Withdrawn` und [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) gezogen |
| V-6 (LOW) Stand der [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) in der Slice-Datei | gezogen auf `Withdrawn` und [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) `Proposed` | Kopf (Bezug), §1 Herkunft, §2, §6 |
| V-7 (LOW) Zuordnung Messung / Festlegung | Tabelle unten | dieser Abschnitt |

**Ergebnis je Messpunkt (§5).** Alle Aussagen sind Einzelmessungen: je Szenario und Instanz eine Aufnahme (2022 und 2025, Digests im Kopf jeder Datei und im Messprotokoll), ein Host, `Microsoft.Data.SqlClient` 7.1.1, TDS 7.4 mit `Encrypt=Mandatory`, TLS 1.2, Proxy mit TLS-Beendigung.

| Messpunkt | Ergebnis | Beleg |
|---|---|---|
| Fehler in `sp_executesql` vor und nach Zeilen | gemessen: Abschluss ist DONEPROC, nie DONE; vor dem Ergebnis `ERROR RETURNSTATUS DONEPROC` mit Fehlerbit, nach einem Ergebnis `DONEINPROC MORE\|ERROR` und DONEPROC ohne Fehlerbit; RETURNSTATUS 208, 8134 oder 0, -6 in Prozeduren | W-1 bis W-3, `MessungBelegTests` |
| Referenzclient behandelt DONE und DONEPROC mit Fehlerstatus verschieden | **Grenze, nicht messbar** (kein DONE im RPC); gemessen ist die Wirkung der gesendeten Formen | W-1 bis W-3, Antwort zu [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) im Messprotokoll |
| COLMETADATA hinter RETURNVALUE | **Grenze, nicht eingetreten** in den Szenarien; Lücke 3 bleibt offen | W-4 |
| Ordinal mit mehreren Ausgabewerten | gemessen: Position in der Aufrufliste, zehn Zweitfälle | W-5, `MessungBelegTests` |
| `curCmd` für Abschlüsse mit Zeilenzahl und DONEINPROC | gemessen (Tabelle der Anweisungsarten); Bedeutung darüber hinaus nicht festgestellt | W-6, 15 Szenarien |
| ENVCHANGE bei `RESETCONNECTION` | gemessen: Typ 18, beide Werte leer, erstes Token, jede Wiederverwendung | W-7 |
| Anmeldeantwort mit ENVCHANGE | gemessen: `ENVCHANGE(1) INFO ENVCHANGE(7) ENVCHANGE(2) INFO LOGINACK ENVCHANGE(4) FEATUREEXTACK DONE` | W-8 |
| SESSIONSTATE und Verhandlung (A-18) | gemessen: Verhandlung ist Voraussetzung, nach `SET` senden beide Instanzen; **Ursache** des Unterschieds nach dem Abbruch (nur 2022, Zustandskennung 254) **nicht geklärt**, Lücke ohne Träger | W-9 |

**Zuordnung Messung / Festlegung je Aussage (`BEO-TDS/aussagen-ueber-messung-hinaus`).**

| Aussage | Art | Stelle |
|---|---|---|
| Tokenfolgen, Statusbits, RETURNSTATUS, Ordinal, `curCmd`, ENVCHANGE, Anmeldeantwort, SESSIONSTATE-Folgen | Messung (Einzelmessung je Instanz, ein Host, ein Client) | W-1 bis W-9; Dateien unter `referenztest-dotnet/Mitschnitt/messung/`; `MessungBelegTests` |
| Bedingung des RETURNSTATUS in `sp_executesql` („Fehlernummer, wenn danach keine Anweisung des Aufrufs läuft, sonst 0“) | Messung an vier Aufrufen; als Regel darüber hinaus nicht belegt | W-2; Review F-4 |
| Methode: mitschneidender Proxy, TLS-Beendigung, TLS 1.2, `TrustServerCertificate=true` auf der Proxystrecke | Festlegung (Nutzer, 2026-11-06 bestätigt) | Messprotokoll, Methode |
| Der Proxy verändert den Verkehr nicht | Festlegung der Bauart (Bytes werden durchgereicht und mitgelesen); „verschiebt um einen Zwischenschritt je Richtung“ ist benannt, ein Vergleich mit dem Lauf ohne Proxy ist nicht geführt | Messprotokoll, Methode und Grenzen |
| Ursache des Abbruchs von `reset-ohne-zustand` im ersten Durchlauf | **Hypothese**, nicht belegt; 6 Wiederholungsläufe ohne Abbruch | Messprotokoll, Bedingungen |
| Redaktion (Felder von LOGIN7, Servername, Kennung) | Festlegung, durch Tests belegt (Mutationen 78, 82, 86 je rot, Verifikation) und durch Lesen der 44 Dateien; Grenze: Längen, Anwendungsname, Bibliotheksname, Merkmalsdaten und Zufallskennungen in PRELOGIN bleiben stehen | `MessungEinheitTests`, `MessungBelegTests`, Messprotokoll |
| Zielbild-Konflikte (sechs) und ihre Angleichung | Nutzerentscheid (Spezifikation dem echten Server angleichen); der Fehlerabschluss im RPC und SESSIONSTATE sind nicht entschieden | Messprotokoll, Konflikte; Bau-Slice |
| Messvermerke in `spec/spezifikation.md` | Verweis auf die Messung; Zusagen unverändert | Spezifikation |

**Steering-Loop-Eintrag (benannte Spec-Lücken; nichts verkörpert, daher ohne Zielort-Feld).** (a) *Ursache des Instanzunterschieds bei SESSIONSTATE nach dem Abbruch* und Bedeutung der Zustandskennung 254: Lücke, benannt im Messprotokoll (Fortschreibung von A-18), ohne Träger; ob eine Untersuchung folgt, entscheidet der Nutzer. (b) *COLMETADATA hinter RETURNVALUE:* Lücke 3 aus [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) bleibt offen (kein erzwingendes Szenario gefunden). (c) *`curCmd`:* die Spec-Lücke „Herkunft der Befehlskennung“ in `spec/spezifikation.md` bleibt, ihr Träger ist [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md). Geliefert, aber ohne Herkunfts-Anker in der Datei (daher gezählt, nicht verkörpert): der Redaktionstest mit den Feldern SSPI, Kennwortwechsel und Datenbankdatei (`MessungEinheitTests`, Verifikation: je eine Mutation rot) und die Belegtests der Ablage (`MessungBelegTests`).

**Lerneintrag (Finding-Klassen, nach Klasse im Register gezählt, nichts verkörpert).** Review F-1 bis F-12 und Verifikation V-1 bis V-10 fallen in wenige Klassen: (1) *Der Test der Redaktion füllte nur die Felder, die die Messreihe belegt* (F-1: SSPI, Kennwortwechsel fielen bei Entfernung in keinem Test auf; behoben, drei Mutationen rot) — `BEO-TDS/redaktion-sucht-nur-den-bekannten-wert`. (2) *Plan-Tabelle gegen Diff* (F-2) — `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`. (3) *Fortschreibung lässt die zweite Fundstelle stehen* (F-3, F-6, F-12, V-3: Spezifikation, Kommentare in Tests und Hilfsklassen, Messprotokoll-Sätze des Mitschnitt-Abschnitts) — `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`; dazu die zwei Kopfkommentare, die seit der Landung von `slice-spec-abdeckung-liest-csharp` falsch waren und in dieser Closure berichtigt sind. (4) *Aussage breiter als die Messung* (F-4 RETURNSTATUS-Bedingung, F-7 Prüfzeile, V-7 Zuordnung) — `BEO-TDS/aussagen-ueber-messung-hinaus`. (5) *DoD-Wortlaut über der Bauart* (V-1, V-2) — `BEO-TDS/dod-wortlaut-ueber-der-bauart`. (6) *Haken vor der Verifikation* (V-4) — `BEO-TDS/haken-vor-verifikation-gesetzt` (jetzt 3×, siehe Register). (7) *Plan trägt den Stand einer ADR vor deren Änderung* (V-6) — `BEO-TDS/slice-plan-veraltet-nach-gelieferter-adr`. (8) *Lücken ohne Träger, Träger-Zuordnung ohne Eintrag* (F-11, V-5; neu die Ursache von A-18) — `BEO-TDS/mitschnitt-luecken-ohne-traeger`. Zur Messung mit einem Proxy: ein Proxy, der nur die erste Verbindung annimmt, bricht Pooling-Szenarien einmalig ab (Hypothese, nicht reproduziert); der Wiederholungslauf (6 Läufe, 12 Teilläufe, 0 Abbrüche) ist der Beleg, nicht der eine grüne Lauf. Zur Cache-Falle: die Stage `test` kann aus dem Cache grün zeigen; der Frischbeleg ist `docker build --no-cache-filter test --target test referenztest-dotnet`.

**Gate-Läufe und Frischbeleg (Planner, im Worktree des Zweigs).** Frischbeleg `docker build --no-cache-filter test --target test referenztest-dotnet` auf dem Stand nach den Kommentar-Berichtigungen: `Passed! Failed: 0, Passed: 79, Skipped: 0, Total: 79`. `make spec-abdeckung`: die Matrix `docs/plan/spec-abdeckung.md` ist neu erzeugt (`SPEC-045`: 53 bestandene Tests aus `MessungBelegTests`, `MessungEinheitTests`, `MitschnittEinheitTests`; 3 nicht gelaufen (die zwei Tests des Abspielens und der Lauf der Messreihe, sie laufen nur in `make vergleichslauf-d`); 9 Festlegungen ohne Nachweis). `make gates`: Exit 0, selbst gefahren auf dem Stand nach den Kommentar-Berichtigungen und den Doku-Commits; der erste Lauf war voll (Klassen `werkzeug`, `tests` und `stempel` ohne Beleg an einem Vorfahren gelaufen), der Folgelauf nach den reinen Doku-Commits übersprang die Klassen mit Beleg; `d-check`: 1016 Dateien, 0 Befunde. `make docs-check` grün. Nicht erneut gefahren: `make vergleichslauf-d LAUF=wire` (EULA, Verifikation hat ihn auf `073ad171` gefahren; seither ändert sich kein Werkzeugcode, nur zwei Kopfkommentare der Tests).

- **Was hat funktioniert:** Die Messreihe ist wiederholbar im Repo (`make vergleichslauf-d LAUF=wire`, 66 s für beide Instanzen in der Verifikation); die Redaktion trägt am Dateiinhalt; die Methode (TLS-Beendigung im Proxy) bringt die Anmeldung und SESSIONSTATE ans Licht, die der Mitschnitt-Lauf nicht sah.
- **Was ging anders als geplant:** Die Messungen werden nicht abgespielt und stehen darum im Messprotokoll statt in der Ausnahmeliste (V-1); der Erstlauf brach einmal ab (Proxy); zwei Teile von Punkt 1 sind Grenze statt Messung (V-4); der Plan wurde dem Code angepasst, der DoD-Text blieb stehen (V-1, V-2).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-qa-vergleichslauf-d-messungen.md` ergänzt in `BEO-TDS/mitschnitt-luecken-ohne-traeger`, `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, `BEO-TDS/redaktion-sucht-nur-den-bekannten-wert`, `BEO-TDS/dod-wortlaut-ueber-der-bauart`, `BEO-TDS/haken-vor-verifikation-gesetzt`, `BEO-TDS/slice-plan-veraltet-nach-gelieferter-adr`; Stände fortgeschrieben in `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/anmeldung-am-draht-nicht-aufgenommen`, `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`. Zähler folgen aus den Dateien.
- **Folge-Slices:** [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md) (Bau und Spezifikations-Angleichung, Start-Trigger für Nr 6 und 7: [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) `Accepted`); [slice-codec-datentypen-erweiterung](../open/slice-codec-datentypen-erweiterung.md) (A-11, unverändert); [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) (Name des Handle-RETURNVALUE, Messaufträge zu Handle-Grenze und Speicher). Kein neuer Slice angelegt.
- **Offene Nutzerpunkte:** (1) [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) annehmen oder verwerfen (Option A empfohlen); (2) soll der Server SESSIONSTATE senden; (3) Untersuchung der Ursache des Instanzunterschieds nach dem Abbruch (Zustandskennung 254), sonst bleibt sie Lücke; (4) Re-Review der Nachbesserung `073ad171` (B-2) nachholen oder als Grenze annehmen; (5) `BEO-TDS/haken-vor-verifikation-gesetzt` hat 3 Belege: Regel, Sensor oder Streichung; (6) Messungen, die kein Slice trägt: Antworten des echten Servers auf Massenladen, MERGE und Katalog (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
- **Risiken aus §6:** jedes mit genau einem Ausgang, siehe §6.
- **Drei Paarungen:** geprüft nach dem Move (2026-11-06): *Anker* — kein Steering-Loop-Eintrag trägt ein Zielort-Feld, nichts zu paaren; *Folge-Slice* — [slice-antwort-tokens-an-messung-angleichen](../in-progress/slice-antwort-tokens-an-messung-angleichen.md), [slice-codec-datentypen-erweiterung](../open/slice-codec-datentypen-erweiterung.md) und [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) existieren als Dateien; *Register* — alle zwölf in §7 genannten `BEO-TDS/<slug>` sind Verzeichnisse mit nicht leerem `evidence/`. Rot: keine.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` als einzige Sub-Area; der Slice berührt Testwerkzeug, Docker und Dokumentation, keine dieser Strukturen trägt eine eigene Strukturregel oder Inventur-Linie neben dem Repo insgesamt (eine von drei Achsen, unter der Schwelle). `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des Worktrees auf `main`, 2026-11-01); alle tragen die Sub-Area `*`. Treffer:

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 25×, Stand *geplant*; dieser Slice ist neben [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) der Träger der Messung selbst (Evidenz-Risiko: die Beobachtung wurde bisher in 25 Vorgängen als Setzung gezählt und nie durch einen Lauf gelöst).
- `BEO-TDS/mitschnitt-luecken-ohne-traeger`, `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server` und `BEO-TDS/anmeldung-am-draht-nicht-aufgenommen` — Träger-Zuordnung vom 2026-11-06 (§1): dieser Slice trägt A-18 und die Anmeldung am Draht sowie den Messauftrag der Zweitfälle.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — Stand *verkörpert* (`AGENTS.md` §3.8): jede Aussage der Closure nennt Instanz, Digest und Szenario.
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — Redaktion der Mitschnitte: Kennwort und Prüfwert erscheinen nirgends in der Ablage.
- `BEO-TDS/ablage-im-repo-bricht-docs-check` — Ausgaben außerhalb des Repos sammeln (`mktemp -d`, `trap`).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — Tests des Proxys variieren die Eingabe (verschiedene Tokens, nicht ein Fall).
- `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab` — Träger der Messung der DONE-Statusbits bei statement-abbrechenden Fehlern im Batch (Nutzerentscheid 2026-11-06); der Bau ist Folge-Slice.
- `BEO-TDS/offene-punkte-ohne-traeger` — statement-abbrechende Fehler im RPC bleiben dort (Ausschluss in §1).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — Slice und Closure-Notiz nennen Kennungen als Link.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
