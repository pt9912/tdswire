# Welle welle-handshake-login: PRELOGIN-/LOGIN7-Handshake, Anmeldung mit Auth-Adapter und Laufzeit-Bootstrap

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

Ein aus der Konfiguration gestarteter Serverprozess führt eine Verbindung ohne TLS und ohne Referenzclient von PRELOGIN über LOGIN7 bis zur Anmeldung: Ein Benutzer der festen Benutzerliste erhält die Login-Antwort-Tokens, eine unbekannte Kennung und ein falsches Passwort erhalten byte-gleiche Ablehnungen (ERROR 18456), und nicht unterstützte Authentifizierungsmodi werden ohne Adapteraufruf abgelehnt ([TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)). Die Aussage steht an einem Rohbytes-Smoke vom Prozessstart bis zur Login-Antwort und gilt für diesen Pfad und diesen Rohbytes-Client, nicht für die Annahme durch den Referenzclient `Microsoft.Data.SqlClient` (siehe §6).

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün.
- Der Slice `slice-codec-tds-paket` der Welle [welle-protokollcodec](done/welle-protokollcodec/welle-protokollcodec.md) liegt in `done/`: Paket-Codec, Nachrichten-Codec (PRELOGIN, LOGIN7, Login-Antwort-Tokens) und das Test-Gate `make test` stehen auf `main`.
- [ADR-0001](../adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../adr/0002-tls-und-verschluesselung.md), [ADR-0003](../adr/0003-transportframework.md), [ADR-0005](../adr/0005-rpc-und-prepared-statements.md) und [ADR-0009](../adr/0009-authentifizierungsmodus.md) stehen im ADR-Index auf `Accepted`.

Weitere Voraussetzungen gelten je Slice und stehen in dessen §4 (Zustandsautomat-ADR [ADR-0004](../adr/0004-session-zustandsautomat.md) für den Handshake-Slice, Listener-Slice [slice-transport-listener-lebenszyklus](open/slice-transport-listener-lebenszyklus.md) der Welle [welle-transport-tls](welle-transport-tls.md) für den Bootstrap-Slice, Slice `slice-adr-pruefwertformat` in `done/` (Nutzerentscheidung zu Prüfwertformat und Konfigurationsweg, [ADR-0011](../adr/0011-pruefwertformat.md) `Accepted`; erfüllt) für Auth- und Bootstrap-Slice): Sie verzögern einen Slice, nicht den Start der Welle.

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` grün, einschließlich `make test`.
- Das Mehr gegenüber den Slice-DoDs: Der Rohbytes-Smoke läuft gegen einen Serverprozess, der über den Bootstrap-Pfad (Konfiguration, `main`) gestartet wurde, nicht gegen eine im Test verdrahtete Instanz. Er belegt in einem Lauf: gültiger Login (Login-Antwort-Tokens), byte-gleiche Ablehnung für unbekannte Kennung und falsches Passwort (am Produkt, mit dem Rohbytes-Client gemessen), Ablehnung der nicht unterstützten Modi, mehrpaketiges und zerstückeltes LOGIN7. Einzelne Slices belegen jeweils nur ihren Teil.
- Die Closure-Notiz führt die Aussagen der Welle in zwei Spalten, *gemessen* (Test oder Smoke, der besteht) und *Festlegung* (MS-TDS-Lesart oder Entscheidung eines Slice ohne Referenzclient-Messung; dazu gehört die Wahl des Nutzers in `slice-adr-pruefwertformat`, deren Spike-Messwerte nur Image und Hardware des Spike-Laufs betreffen); jeder in §6 ausgeschlossene Gegenstand ist dort einem Träger zugeordnet, der als Datei existiert, oder trägt den Grund, warum nicht.
- Closure-Notiz in `welle-handshake-login-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-adr-pruefwertformat | Entscheidung: Prüfwertformat der festen Benutzerliste (Verfahrenskennung je Eintrag) und Konfigurationsweg, mit Spikes und Nutzerwahl; Fortschreibung von SPEC-051 und `contracts/auth-adapter.md` oder neue ADR | [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0009](../adr/0009-authentifizierungsmodus.md) |
| slice-auth-adapter-benutzerliste | Auth-Adapter-Schnittstelle und feste Benutzerliste aus der Konfiguration (Annahme/Ablehnung, konstanter Vergleich, Validierung der Liste) | [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0009](../adr/0009-authentifizierungsmodus.md) |
| slice-handshake-login | Handshake-Zustandsautomat PRELOGIN → LOGIN7 → Login-Antwort, Ablehnung 18456, nicht unterstützte Modi, mehrpaketiges LOGIN7 | [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0001](../adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../adr/0002-tls-und-verschluesselung.md), [ADR-0009](../adr/0009-authentifizierungsmodus.md) |
| slice-bootstrap-konfiguration | Konfiguration laden und validieren, `main`, Start/Stop, Verdrahtung und Rohbytes-Smoke vom Prozessstart bis zum Login | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0003](../adr/0003-transportframework.md), [ADR-0009](../adr/0009-authentifizierungsmodus.md) |

Reihenfolge: Der Entscheidungs-Slice `slice-adr-pruefwertformat` steht vor dem Auth- und dem Bootstrap-Slice (deren Start-Trigger nennt ihn); er braucht weder den Codec noch den Listener. Auth-Adapter und Handshake hängen über die Adapter-Schnittstelle zusammen (der Handshake-Slice braucht sie als Typ, nicht die Benutzerliste); der Bootstrap-Slice schließt die Welle, weil er beide und den Listener der Transport-Welle verdrahtet.

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: die funktionale Welle für SQL_BATCH und RPC (sie braucht eine angemeldete Sitzung im Zustand READY; Plan: [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md)). Mittelbar blockiert: der Anmelde-Teil von `slice-qa-kompatibilitaet` (er braucht einen Server, der sich anmelden lässt).
- Wird blockiert von: [welle-protokollcodec](done/welle-protokollcodec/welle-protokollcodec.md) (Slice `slice-codec-tds-paket` in `done/`); der Welle [welle-transport-tls](welle-transport-tls.md) (nur der Bootstrap-Slice: er braucht deren Listener-Slice [slice-transport-listener-lebenszyklus](open/slice-transport-listener-lebenszyklus.md) in `done/`); [welle-entscheidungen-grundlage](welle-entscheidungen-grundlage.md) nicht mehr (nur der Handshake-Slice betraf sie: [ADR-0004](../adr/0004-session-zustandsautomat.md) ist `Accepted`); dem Slice `slice-adr-pruefwertformat` dieser Welle, der die Nutzerentscheidung zu Prüfwertformat und Konfigurationsweg getragen hat und in `done/` liegt (Auth- und Bootstrap-Slice; innerhalb der Welle, keine fremde Voraussetzung).
- Berührt, blockiert aber nicht: `welle-transport-tls`. Transport, TLS und Listener gehören dorthin; diese Welle nimmt vom Transport nur eine Nachrichten-Schnittstelle (vollständige Nachricht hinein, Antwort-Nachrichten und Verbindungsende heraus, TLS-Aushandlung und Fristablauf als Ereignisse). Wer diese Schnittstelle schneidet, ist abzustimmen (siehe Slice `slice-handshake-login` §6).
- Berührt, blockiert aber nicht: [welle-qualitaet-grundlage](welle-qualitaet-grundlage.md). Die Anmelde-Liefer-Punkte der beiden Slices sind hier zugeordnet, nicht dupliziert; die Slices selbst bleiben unverändert:
  - `slice-qa-sicherheit`, *Loginspektion: kein Geheimnis im Log*: Der Test am Anmeldepfad dieser Welle (Passwort und Kennung in keiner Antwort, keinem Log-Eintrag, keinem Ablehnungsgrund) ist Vorleistung im Handshake-Slice; das Gate über das ganze System bleibt bei `slice-qa-sicherheit`.
  - `slice-qa-sicherheit`, *Zeitgleichheit, Drosselung, Prüfwertformat*: Das Prüfwertformat wurde vor dem Auth-Slice gebraucht und deshalb vorgezogen entschieden (Nutzerentscheidung, [ADR-0011](../adr/0011-pruefwertformat.md), Träger war der Slice `slice-adr-pruefwertformat`); der Auth-Slice liefert die Bauart des konstanten Vergleichs mit einem Strukturtest. Die Zeitmessung und die Drosselung bleiben bei `slice-qa-sicherheit`. Die Überschneidung beim Prüfwertformat hat die Closure von `slice-adr-pruefwertformat` aufgelöst: der Punkt in `slice-qa-sicherheit` steht gekürzt auf Zeitgleichheit und Drosselung, die Änderung ist dort begründet.
  - `slice-qa-kompatibilitaet`, *Anmeldemessungen* (Windows-SSPI, TDS 8.0 mit SQL-Authentifizierung, mehrpaketiges LOGIN7, Pooling mit `ResetConnection`) und das Anmelde-Szenario mit dem Referenzclient: bleiben dort. Diese Welle liefert den Server, an dem gemessen wird, und belegt das mehrpaketige LOGIN7 nur mit von Hand aus MS-TDS abgeleiteten Vektoren (Festlegung, nicht gemessen).

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

Träger der ausgeschlossenen Gegenstände (existierende Datei, sonst Zeiger auf die Closure):

- Transport und TLS: Sockets, `AsynchronousSocketChannel`, `SSLEngine`, TLS-Kapselung in Paketen vom Typ `0x12`, Listener, Zertifikats- und Schlüsselkonfiguration, Startprobe und Diagnose für belegten Port (SPEC-043), JVM-Option für TLS 1.3 — Träger: die geplante Welle `welle-transport-tls` (wird von anderer Stelle geschnitten, hier nicht geplant). Diese Welle ruft den Listener und die TLS-Konfiguration, baut sie nicht.
- Verbindungs-Zustandsautomat jenseits des Logins: `READY`-Verarbeitung von SQL_BATCH und RPC, Anfragezustand und ATTENTION (SPEC-001 untere Hälfte, SPEC-029), Dispatch — Träger: [slice-attention-cancel-timeout](open/slice-attention-cancel-timeout.md) und [slice-fehler-zustandsuebergang](open/slice-fehler-zustandsuebergang.md) (Welle [welle-fehler-abbruch](welle-fehler-abbruch.md)) sowie [slice-sql-batch-dispatch](open/slice-sql-batch-dispatch.md) (Welle [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md)).
- Backend: kein Backend-Port, kein Simulations-Backend; es gibt keine Anfragen ([ADR-0006](../adr/0006-backend-vertrag-und-streaming.md); Welle [welle-backend-streaming](welle-backend-streaming.md)).
- Fehler- und ATTENTION-Semantik außerhalb der festen Login-Ablehnung ([ADR-0007](../adr/0007-fehler-und-attention.md); Welle [welle-fehler-abbruch](welle-fehler-abbruch.md)).
- Geordnetes Beenden mit Frist (SPEC-044, `SHUTDOWN_GRACE`): Es gibt keine laufenden Anfragen, die zu Ende laufen könnten; der Stop dieser Welle schließt Listener und Verbindungen ohne Frist-Logik. Träger: noch keiner, die Closure legt einen Folge-Slice an.
- Observability-Adapter (strukturierte Logs, Metriken, Traces, Korrelations-IDs, `SPEC-017`): nur ein minimaler Log-Anschluss für Login-Ablehnungsgründe, Start-Diagnose und die Sichtbarkeit einer Abschwächung der Verschlüsselung (SPEC-010). Träger: noch keiner, die Closure legt einen Folge-Slice an.
- Nachweis gegen den Referenzclient und einen echten SQL Server: Kompatibilitäts-Läufe, Mitschnitte, Windows-SSPI, TDS 8.0 mit SQL-Authentifizierung, Pooling — `slice-qa-kompatibilitaet`, `slice-qa-reproduzierbarkeit` (Vergleichslauf D). Diese Welle belegt Handshake und Login mit Rohbytes, nicht die Annahme durch den Client.
- Zeitgleichheit des Passwortvergleichs als Messung, Drosselung wiederholter Anmeldeversuche, Loginspektion über das Gesamtsystem — `slice-qa-sicherheit`. Stabilitäts- und Fuzz-Gate über ungültige Verbindungen — `slice-qa-stabilitaet`.
- Nicht unterstützte Authentifizierung als Funktion: SSPI/Kerberos, Entra-ID und Passwortwechsel werden erkannt und abgelehnt ([ADR-0009](../adr/0009-authentifizierungsmodus.md)); sie zu unterstützen wäre eine neue Anforderung.
- Eigene Benutzerverwaltung eines Betreibers hinter dem Adapter und jede Adapter-Variante außer der festen Benutzerliste (die Betreiberanforderung ist nicht abnahmebindend, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`).
- Neue Anforderungen oder Änderungen am Lastenheft.
 Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-handshake-login-results.md`
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
