# ADR-0018: Laufzeit-Artefakt: Basis-Image, Betreiberoptionen, Laufzeittest im E2E-Durchgang

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [ADR-0011](0011-pruefwertformat.md), [ADR-0013](0013-backend-grenzen.md), [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)

**Schärft:** [SPEC-042](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Basis-Images per Digest gepinnt; die Spezifikation führt keine Zeile für das Laufzeit-Artefakt, siehe „Offen“), [SPEC-023](../../../spec/spezifikation.md#6-externe-verträge) (Startzeile mit `-Djdk.tls.server.newSessionTicketCount=0`). Ein Gate, dessen Werkzeug festlegt, was es prüft, hat eine Spec-Stelle: die ADR benennt die fehlende. Kein angenommener ADR-Satz wird überstimmt; kein `Supersedes`.

---

## Kontext

Der Slice für Laufzeit-Artefakt und Lizenz liegt in `done/` und hat ein Laufzeit-Artefakt gebaut. Das Basis-Image war dort „Entscheidung des Implementers mit Nutzerbestätigung“; die Bestätigung steht in den Akten nicht (Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`). Der Nutzer hat am 2026-10-31 die Empfehlung zu Punkt 5 des Entscheidungsblatts bestätigt; diese ADR hält Entscheidung und gebauten Stand fest, damit der Slice nicht das einzige Dokument der Wahl bleibt.

**Gebauter Stand (Dockerfile, Stage `runtime`; Closure des Slice, je ein Lauf, Host des Laufs):**

- Basis-Image `gcr.io/distroless/java21-debian12:nonroot`, per Index-Digest `sha256:7e37784d…8077d` gepinnt. Der Digest wurde laut Closure am 2026-10-31 für den Tag `nonroot` gemessen (Review, Negativbefund f); die Verifikation maß ihn nicht erneut.
- Nutzer `65532:65532` (ohne Root-Rechte); Port 1433 deklariert; drei `COPY`-Zeilen: Jar, Bibliotheken (`lib/`, über `Class-Path` im Manifest), `LICENSE`.
- Startzeile: `java -Djdk.tls.server.newSessionTicketCount=0 -jar /app/tdswire.jar`. Konfiguration und Benutzerliste kommen zur Laufzeit herein (Umgebung nach `ConfigKeys`, Dateien im Container); das Image enthält keine Benutzerliste (geprüft nur als Dateinamen unter Betreiber-Verzeichnissen, Namen der Umgebung und `/app`, kein Inhaltsscan).
- Betreiberoptionen der JVM kommen über `JAVA_TOOL_OPTIONS`; gemessen ist die Ankunft an der JVM (Zeile `Picked up JAVA_TOOL_OPTIONS`), nicht die Wirkung von `-XX:+ExitOnOutOfMemoryError` ([ADR-0013](0013-backend-grenzen.md)). Der Schalter ist nicht voreingestellt.
- E2E-Lauf `make laufzeittest` (Stufe von `make e2e`, nicht in `make gates`, [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md)): Container starten, Rohbytes-Login aus einem zweiten Container im selben Netzwerk, Stop (Exit-Code 143 = 128 + SIGTERM, kein Handler des Produkts), Image-Inhalt prüfen. Gemessen, je ein Lauf: Stage `test` ohne Cache 89 bis 102 s; Warmlauf des Targets 24 bis 31 s; Kaltlauf 88 bis 171 s (der höhere Wert liegt über dem der Stage `test`); CI-Lauf mit dem Lauf als Gate 8 min 58 s gegen rund 7 min 43 s davor.
- Daneben besteht die Stage `build` (Distroless `static`, nur das Jar, läuft allein nicht); zwei Stages für ein Artefakt sind eine bekannte Doppelung (`BEO-TDS/zwei-artefakt-stages-nebeneinander`, offen).

## Entscheidung

Wir wählen **Option A**: Das Laufzeit-Artefakt bleibt, wie gebaut.

1. **Basis-Image:** Distroless Java 21, Variante `nonroot`, per Index-Digest gepinnt. Ein neuer Digest ist eine bewusste Änderung des `Dockerfile` und braucht einen grünen `make laufzeittest`.
2. **Nutzer:** ohne Root-Rechte (UID/GID 65532).
3. **Betreiberoptionen:** über `JAVA_TOOL_OPTIONS`, nicht über eine eigene Konfigurationsschnittstelle; der Schalter für `OutOfMemoryError` ist dokumentierbar, nicht voreingestellt ([ADR-0013](0013-backend-grenzen.md) Entscheidung 2). Was `JAVA_TOOL_OPTIONS` mit dem fest gesetzten Flag der Startzeile macht, wenn der Betreiber dieselbe Eigenschaft setzt, ist nicht gemessen.
4. **E2E-Durchgang:** `make laufzeittest` ist ein E2E-Lauf (Stufe 2 von `make e2e`) und steht nicht in `make gates`; die Zuordnung trägt [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md). Den Weg Manifest, Class-Path und Jar-Name prüft im Gate eine Textprüfung im Bau.
5. **Nicht entschieden (siehe „Offen“):** Dateirechte der Benutzerliste im Betrieb, Lizenzhinweise der Bibliotheken.

Die Wahl ist die des gebauten Standes; die Gründe sind: gebaut, per Digest gepinnt, läuft ohne Root, der Laufzeittest grünt; ein Wechsel kostet einen neuen Digest und einen Lauf des Tests. Dass kein anderes Image (JRE-Image) besser wäre, ist nicht gemessen; ein Vergleich wurde nicht angestellt.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A — Distroless Java 21 `nonroot`, Digest, `JAVA_TOOL_OPTIONS` (gewählt)** | gebaut und getestet; kein Root; keine Shell im Image (gelesen aus der Image-Bezeichnung, nicht geprüft); Pin per Digest | Fehlersuche im Container ohne Shell schwerer (abgeleitet); Digest ist an einen Tag-Stand gebunden und wird nicht automatisch aktualisiert |
| B — JRE-Image eines anderen Herstellers (z. B. Temurin JRE) | Shell und Werkzeuge vorhanden | größere Angriffsfläche und Image (nicht gemessen); neuer Digest, neuer Laufzeittest, kein Beleg für Vorteil |
| C — eigenes Basis-Image aus `scratch` mit gebündelter JRE (`jlink`) | kleinstes Image | Bau und Pflege der JRE-Auswahl; widerspricht dem Ziel „kleinste Änderung“ ohne Beleg |
| D — keine Festlegung, Slice bleibt einziges Dokument | kein Dokument | die Bestätigung des Nutzers fehlt in den Akten; eine Lesart, die ohne Entscheidungstext wiederkehrt |

## Konsequenzen

- Positiv: Der Laufzeit-Slice stützt sich auf eine Entscheidung; das Risiko „Nutzerbestätigung fehlt“ ist mit der Annahme entlastet.
- Negativ: Der Digest veraltet still (kein Sensor prüft sein Alter; `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`); der Laufzeittest belegt Start und Login mit dem Rohbytes-Client, nicht die Annahme durch `Microsoft.Data.SqlClient` und nicht das Verhalten unter Last.
- **Folgepflicht (nach `Accepted`):** Spezifikation: eine Zeile für das Laufzeit-Artefakt (Image, Nutzer, Betreiberoptionen, Gate) ist bisher nicht angelegt (Spec-Lücke der Closure V-1); der Nachzug ist eine Entscheidung des Nutzers und gehört in die Planung der Spec-Nachzüge nach Annahme. Betriebstext (Betreiberschalter, Dateirechte): Slice für Release-Doku und Prozess. Der Planner vermerkt die Nutzerbestätigung im Register.
- **Akzeptiertes Negativ (einmalig):** Der Digest-Pin wird nicht automatisch gealtert geprüft; ein Alters-Sensor wäre ein neues Werkzeug mit Netzzugriff und ist nicht angelegt.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Shell-Gate `tools/laufzeittest.sh` | Container startet aus der Stage `runtime`, Rohbytes-Login gelingt, Stop beendet den Prozess mit Exit-Code 143, Jar, `lib/` und `LICENSE` liegen im Image, kein Dateiname unter Betreiber-Verzeichnissen deutet auf ein Geheimnis, `JAVA_TOOL_OPTIONS` erreicht die JVM, Nutzer ist 65532. | `make laufzeittest` (in `make gates`) |

## Offen

- Dateirechte und Besitz der Benutzerliste im Betrieb: wie der Nutzer ohne Root-Rechte die Datei liest, die `AUTH_USERS_FILE` nennt (Mount, Secret, Besitzer, Modus), ist weder gemessen noch entschieden ([ADR-0011](0011-pruefwertformat.md) §Offen; Register `BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen`; Träger des Betriebstexts: Slice für Release-Doku und Prozess). Eine Empfehlung dieser ADR gibt es nicht, weil kein Betriebsmodell des Nutzers vorliegt.
- Lizenzhinweise der gelieferten Bibliotheken: [TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz) verlangt die MIT-Lizenz des Projekts, nicht Hinweise zu Bibliotheken. Ob das Artefakt sie braucht, ist eine Rechtsfrage ohne Prüfung im Repo (Register `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`). Die Lesart des Architect zur Vereinbarkeit von Apache-2.0 mit MIT ist keine Rechtsberatung.
- Zwei Stages (`build` und `runtime`) für ein Artefakt; `SPEC-*`-Zeile für das Artefakt; Zusatzklasse der Sensors-Bindung an eine Lastenheft-Kennung (V-3 der Verifikation).

## Re-Evaluierungs-Trigger

Ein neuer Release des Basis-Images mit Sicherheitsmeldung zum gepinnten Digest; ein Wechsel der Java-Hauptversion der Anwendung; eine Regression im Container-Artefakt, die erst der E2E-Durchgang fand; eine Betreiberanforderung an Shell oder Werkzeuge im Container; der Entschluss, ein Image zu veröffentlichen (Registry, Signierung), den das Lastenheft nicht verlangt.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Proposed – Bestätigung des gebauten Laufzeit-Artefakts zu Punkt 5 des Entscheidungsblatts (Nutzer: „ok deine empfehlungen“); Dateirechte und Lizenzhinweise bleiben offen; Status bleibt Proposed bis Review und Annahme durch den Nutzer | Entscheidungsblatt, Slice für Laufzeit-Artefakt und Lizenz |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
