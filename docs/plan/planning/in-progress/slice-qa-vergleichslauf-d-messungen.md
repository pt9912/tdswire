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

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md), [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)

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

**Ziel:** Die Aussagen über die Antworten eines echten SQL Servers, die der Mitschnitt-Lauf nicht gemessen hat, sind je Punkt von *Setzung* auf *gemessen* gehoben oder als Abweichung geführt: Fehler in `sp_executesql` (Abschluss, Statusbits) und Stellung von COLMETADATA hinter RETURNVALUE, zweite Messfälle für das Ordinal der RETURNVALUE und für `curCmd` (Messauftrag von [slice-antwort-tokens-an-messung-angleichen](../open/slice-antwort-tokens-an-messung-angleichen.md)), ENVCHANGE bei `RESETCONNECTION`, die Anmeldung am Draht mit ENVCHANGE und die Verhandlung der Sitzungswiederaufnahme (SESSIONSTATE, A-18); die Mitschnitte der Messung liegen redigiert im Repo.

**Stand nach dem Mitschnitt-Lauf (2026-11-06).** [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) hat Proxy, Redaktion und 17 Mitschnitte je Instanz geliefert (`docs/messungen/referenzabgleich.md`, Abschnitt „Mitschnitt-Lauf“, Ergebnis 1 bis 6). **Gemessen und damit nicht mehr Gegenstand dieses Slice:** Tokenreihenfolge der RPC-Antwort mit Ausgabewert (Ergebnis 1), Statusbits der DONE-Familie bei statement- und batch-abbrechendem Fehler im Batch (Ergebnis 2), Zählung der RETURNVALUE (Ergebnis 3, mit der Grenze „ein Fall je Instanz“), Cancel-Antwort und ATTENTION (Ergebnis 5), ERROR bei Fehler vor und nach Zeilen (Ergebnis 6). **Entscheidung des Nutzers vom 2026-11-06** („deine empfehlungen“): die Aufnahme-Methode (mitschneidender Proxy) ist bestätigt, der Start-Trigger ist erfüllt; die Bau-Seite der gemessenen Konflikte liegt in [slice-antwort-tokens-an-messung-angleichen](../open/slice-antwort-tokens-an-messung-angleichen.md).

**Zuordnung der Lücken aus dem Mitschnitt-Lauf (Folge-Slices, Register `BEO-TDS/mitschnitt-luecken-ohne-traeger`).** A-10 (`curCmd`) und der RETURNSTATUS-/Ordinal-Teil von A-16 → [slice-antwort-tokens-an-messung-angleichen](../open/slice-antwort-tokens-an-messung-angleichen.md); A-11 (Länge 17 statt 9 im TYPE_INFO von `decimal(10,2)`) → [slice-codec-datentypen-erweiterung](../open/slice-codec-datentypen-erweiterung.md), die Ursache ist vorher zu untersuchen; A-15 (`WAITFOR`, Abbruch eines wartenden Befehls) → der Slice-Kandidat „erkannte Anweisungsformen erweitern“ (`BEO-TDS/anweisungsformen-des-simulations-backends-enger-als-der-server`; **nicht angelegt**, ohne Kennung, nach der Lesart-Entscheidung des Nutzers dort); A-18 (SESSIONSTATE bei `ConnectRetryCount=1`, nur Instanz 2022) und die Anmeldung am Draht (`BEO-TDS/anmeldung-am-draht-nicht-aufgenommen`) → **dieser Slice**, Liefer-Punkt 3. Ob der Server dieses Repos SESSIONSTATE senden soll, entscheidet der Nutzer nach der Untersuchung.

**Herkunft.** Dieser Slice ist der Träger, den vier Entscheidungen nennen: [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) (`Accepted`: Spec-Lücken 2 und 3, Reihenfolge von RETURNVALUE und RETURNSTATUS, COLMETADATA nach RETURNVALUE), [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) (`Proposed`: DONEPROC im Fehlerfall der RPC-Antwort ist Setzung ohne Messung), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (`Accepted`: ENVCHANGE bei `RESETCONNECTION` offen, Cancel-Antwort R11 und R12 Vorbild) die Beobachtung `BEO-TDS/messung-am-echten-sql-server-fehlt` und (Nutzerentscheid 2026-11-06, Träger-Vorschlag übernommen) die Beobachtung `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab`: Die Spezifikation legt fest, dass der Sequenzierer bei statement-abbrechenden Fehlern `DONE` ohne `MORE` schreibt; der echte Server sendet nach dem Stand der Spezifikation `DONE` mit `MORE` und `ERR`. Dieser Slice liefert die Messung, nicht den Bau. Er entstand am 2026-11-01 aus der Zerlegung von [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md), das den Container, das Target und die Ausnahmeliste liefert.

**Methode.** Wie die Messreihe R in [ADR-0007](../../adr/0007-fehler-und-attention.md): der Referenzclient spricht über einen mitschneidenden TCP-Proxy im Client-Container mit dem echten Server; der Proxy zerlegt die TDS-Pakete und die Tokens. Das ist die Methode des Mitschnitt-Slice (gebaut als mitschneidender TCP-Proxy, `referenztest-dotnet/Mitschnitt/Relais.cs`); der Nutzer hat sie am 2026-11-06 bestätigt („deine empfehlungen“). Neu ist für Liefer-Punkt 3 eine TLS-beendende Betriebsart: der bisherige Proxy liest ab der ersten Anfrage den Klartext und sieht die verschlüsselte Anmeldung nicht. Anders als beim Spike von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) bleibt der Messcode im Repo, damit die Messung wiederholbar ist.

**Übernimmt (aus dem Vorgänger-Schnitt):** den Wire-Teil des Umfangs von [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) (Antwortseite des Codecs); [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) hatte diesen Teil zuvor für sich übernommen, siehe §6.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Container, Make-Target, Digest-Pin, Ausnahmeliste, Messung von Laufzeit und Bedarf, EULA-Entscheidung, Anmelde-Szenario und TDS-8.0-Tag — [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md); dieser Slice setzt sie voraus (Start-Trigger).
- Das Abspielen von Mitschnitten gegen den Server dieses Repos und der feldweise Vergleich — [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md); hier wird gemessen, was der echte Server sendet, nicht was der Testserver sendet. Das Produkt ist die Bibliothek, der Server ein Testserver ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)); dieser Lauf spricht ihn nicht an.
- Das Ändern von Code, Spezifikation oder `Accepted`-ADRs aufgrund eines Befunds — je Befund ein Slice oder eine Folge-ADR nach der Messung. Insbesondere ändert dieser Slice [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) nicht; er liefert die Messung, auf die der Nutzer entscheidet (DoD, Punkt 1). Den Bau der gemessenen Konflikte (`curCmd`, `MORE`, RETURNSTATUS, Ordinal) trägt [slice-antwort-tokens-an-messung-angleichen](../open/slice-antwort-tokens-an-messung-angleichen.md).
- Der Bau der Cancel-Zwei-Nachrichten-Form des echten Servers — [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 1 baut nur `DONE[ATTN]`; hier wird nur gemessen. Der Referenzclient-Lauf gegen den Server dieses Repos gehört zu [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md).
- Der Referenzclient mit `Pooling=true` gegen den Server dieses Repos (Messweg (b) in [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)) — [slice-resetconnection-zuruecksetzen](../done/slice-resetconnection-zuruecksetzen.md); dort steht Messweg (a) als Folge, die dieser Slice liefert.
- Der Bau zur Messung der Statusbits bei statement-abbrechenden Fehlern im Batch (`Failure` um die Klasse erweitern, Sequenzierer angleichen) — [slice-antwort-tokens-an-messung-angleichen](../open/slice-antwort-tokens-an-messung-angleichen.md); gemessen ist er im Mitschnitt-Lauf (Ergebnis 2).
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

- [x] **Antwortseite im RPC und Zweitfälle:** Mit dem vorhandenen Proxy und weiteren Szenarien in `referenztest-dotnet/Vergleich/MitschnittLaufTests.cs` (Aufnahme, beide Instanzen, redigiert, mit Herkunft) gemessen: (a) **Fehler in `sp_executesql`**: mit welchem Token er endet (DONE oder DONEPROC, Statusbits), je ein Szenario für einen Fehler vor und einen nach Zeilen, und ob der Referenzclient beide Formen verschieden behandelt; (b) **Stellung von COLMETADATA hinter RETURNVALUE** (ein RPC, dessen Ergebnis nach dem Ausgabewert kommt; Spec-Lücke 3 aus [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md)); (c) **Messauftrag von [slice-antwort-tokens-an-messung-angleichen](../open/slice-antwort-tokens-an-messung-angleichen.md)**: ein zweiter Fall für das Ordinal der RETURNVALUE mit mehreren Ausgabewerten (`sp_executesql` mit zwei Ausgabeparametern an verschiedenen Stellen der Aufrufliste und einem Wertparameter dazwischen, ein Ausgabeparameter vor einem Wertparameter, `InputOutput` mit NULL; `sp_prepexec` mit zusätzlichem Ausgabeparameter) und die Befehlskennung `curCmd` für Abschlüsse mit Zeilenzahl (INSERT, UPDATE, DELETE im Batch und im RPC) und für DONEINPROC. **Nutzerentscheidungen vom 2026-11-06** („deine Empfehlungen“): (1) `curCmd` für INSERT, UPDATE, DELETE und DONEINPROC bleibt ohne Aussage, bis dieser Punkt gemessen ist; (2) Fehler im RPC, COLMETADATA hinter RETURNVALUE und ENVCHANGE bei `RESETCONNECTION` bleiben in diesem Slice (Liefer-Punkte 1 und 2). Je Punkt steht *gemessen* oder *Abweichung* in der Ausnahmeliste, die Kompatibilitätsmatrix trägt den Stand, und das Messprotokoll nennt die Grenze (eine Aufnahme je Instanz). **Entscheidungs-Konsequenz für [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) (`Proposed`), nicht Teil dieses Slice:** nach der Messung entscheidet der Nutzer, Option A anzunehmen oder die ADR zurückzuziehen; die Closure-Notiz nennt die Messzeile und diese Entscheidung als offenen Punkt.
- [x] **Zurücksetzen:** ENVCHANGE im ersten Antwortpaket nach `RESETCONNECTION` (Pooling, zweite Nutzung der Verbindung): Typ und Inhalt aus dem Mitschnitt, oder die Begründung, warum der Proxy es nicht liefert ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Messweg (a)); ein Befund „Token unbekannt“ ist kein Ergebnis (der Zerleger kennt das Token nicht ohne Erweiterung). Das Ergebnis steht in der Ausnahmeliste.
- [x] **Anmeldung am Draht und SESSIONSTATE (A-18):** Der Proxy wird um eine TLS-beendende Betriebsart erweitert (Setzung des Planners: Aufnahme von PRELOGIN, LOGIN7 und der Anmeldeantwort mit ENVCHANGE hinter einem eigenen Zertifikat des Proxys; Kennwort und Prüfwert werden redigiert, ein Test belegt die Redaktion mit Eingaben, die beides enthalten). Gemessen für beide Instanzen: Antwort der Anmeldung (LOGINACK, ENVCHANGE-Folge, INFO, DONE), und ob die Verhandlung der Sitzungswiederaufnahme (`ConnectRetryCount=1`, FEATUREEXT) der Grund dafür ist, dass die Instanz 2022 SESSIONSTATE sendet und die Instanz 2025 nicht (A-18); mit `ConnectRetryCount=0` und `1` als Gegenprobe. Das Ergebnis liegt als Messzeile mit Mitschnitt vor; ob der Server dieses Repos SESSIONSTATE senden soll, entscheidet der Nutzer danach, es ist kein Lieferinhalt dieses Slice.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jede Aussage steht an einem Test, einem Mitschnitt oder ist als Festlegung gekennzeichnet; die Zuordnung *Messung | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`); Nachträge in der Spezifikation und in ADRs führt dieser Slice nicht aus; sie sind je Befund benannte Folge-Arbeit.
- [x] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Werkzeuge, falls ein Target hinzukommt; Kompatibilitätsmatrix).
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

**Stand des Start-Triggers:** Proxy, Redaktion und 17 Mitschnitte je Instanz liegen im Repo ([slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md)); die DoD in §2 ist auf die offenen Messpunkte gekürzt (2026-11-06). Dieser Slice ist der Träger des Messauftrags (Zweitfälle) für [slice-antwort-tokens-an-messung-angleichen](../open/slice-antwort-tokens-an-messung-angleichen.md), dessen Start diese Messung abwartet (Ordinal).

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
- **Die Messung kann ein Ergebnis ergeben, das eine `Accepted`-ADR berührt** (Reihenfolge der Tokens, Cancel-Antwort, ENVCHANGE). Hard Rule 3.5: kein Eingriff in die ADR; der Befund führt zu einer Folge-ADR oder einem Folge-Slice. — **Ausgang:** offen bis zur Closure.
- **[ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) ist `Proposed`, der Bau weicht von ihr ab.** Die Messung entscheidet nicht; es entscheidet der Nutzer nach der Messung (Annahme von Option A oder Zurückziehen der ADR). Bis dahin trägt der Quelltext (`failedDone()`) eine Setzung ohne Messung. — **Ausgang:** offen bis zur Closure.
- **Der Proxy zeigt nur, was er zerlegen kann.** Der Parser der Messreihe R kennt das ENVCHANGE bei `RESETCONNECTION` nicht ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)); ein Befund „Token unbekannt“ ist kein Ergebnis. — **Ausgang:** offen bis zur Closure.
- Der Slice kann mehr als drei Liefer-Punkte tragen, falls die TLS-beendende Betriebsart des Proxys (Punkt 3) mehr Aufwand ist als die Messungen. — **Ausgang:** offen bis zur Closure.
- **Die Anmeldung am Draht liegt hinter TLS.** Ein Proxy, der TLS beendet, ist ein anderer Messaufbau als der bisherige (der Client verbindet mit dem Proxy, nicht mit dem Server; der Proxy braucht ein Zertifikat, das der Client annimmt), und das Zerlegen von FEATUREEXT und SESSIONSTATE kann die Grenzen des Zerlegers (`TokenLeser`) zeigen. — **Ausgang:** offen bis zur Closure.
- **Der Messauftrag für [slice-antwort-tokens-an-messung-angleichen](../open/slice-antwort-tokens-an-messung-angleichen.md) blockiert dessen Start (Ordinal).** Liefer-Punkt 1 ist deshalb vor den anderen zu bauen. — **Ausgang:** offen bis zur Closure.

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
