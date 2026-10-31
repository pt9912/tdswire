# Welle welle-transport-tls: TCP-Listener und TLS-Schicht des Servers

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

Ein Client erreicht den Server über TCP, und beide Verschlüsselungspfade ([ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)) tragen TDS-Nachrichten über eine eigene `SSLEngine`-Schleife ([ADR-0003](../../adr/0003-transportframework.md)): im TDS-7.4-Pfad läuft der Handshake in Paketen vom Typ `0x12` und schaltet auf rohes TLS um, im TDS-8.0-Pfad läuft TLS ab dem ersten Byte mit ALPN `tds/8.0` ([ADR-0002](../../adr/0002-tls-und-verschluesselung.md)). Der Listener prüft die TLS-Konfiguration vor dem Binden und beendet sich geordnet. Die Aussage steht an einem Loopback-Test, der Listener, Schleife und den Nachrichtenleser über dem Paket-Codec gemeinsam durchläuft ([TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)); sie gilt für die Transportschicht gegen eine JDK-Gegenstelle, nicht für die Kompatibilität mit dem Referenzclient (siehe §6).

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `slice-codec-tds-paket` (Welle [welle-protokollcodec](welle-protokollcodec/welle-protokollcodec.md)) liegt in `done/`: Paket-Codec im Package `transport` und das Test-Gate `make test` stehen auf `main`.
- `make gates` ist auf `main` grün.
- [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) und [ADR-0003](../../adr/0003-transportframework.md) stehen im ADR-Index auf `Accepted`. [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md) (Zustandsautomat, Backend-Vertrag, Fehlersemantik) stehen ebenfalls auf `Accepted`; sie sind kein Start-Trigger, weil diese Welle sie nicht berührt (siehe §6).

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` grün, einschließlich `make test`.
- Das Mehr gegenüber den Slice-DoDs: Der Smoke in `slice-transport-listener-lebenszyklus` durchläuft Listener, `0x12`-Kapselung, TDS-8.0-Pfad und Nachrichtenleser in einem Lauf, und die Closure-Notiz führt die Aussagen des Transports in zwei Spalten, *gemessen* (Test, der besteht) und *Festlegung* (MS-TDS-Lesart oder Entscheidung eines Slice, ohne Referenzclient-Messung). Jeder in §6 ausgeschlossene Gegenstand hat einen Folge-Slice als Datei in `open/` oder in der Notiz den Grund, warum nicht. Ohne diese Zuordnung und den gemeinsamen Smoke wäre die Welle drei einzelne Slices.
- Die Nutzerentscheidungen aus den Slice-Risiken (Version des Coroutinen-Pins, Herkunft des Testzertifikats, Format von Zertifikat und Schlüssel) sind getroffen und in den Notizen der Slices festgehalten.
- Closure-Notiz in `welle-transport-tls-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-transport-coroutines-geruest | Coroutine-Gerüst: `kotlinx-coroutines-core`-Pin, Kanal-Abstraktion, Nachrichtenleser und -schreiber | [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0003](../../adr/0003-transportframework.md) |
| slice-transport-tls-schleife | TLS-Schleife: `SSLEngine` im TDS-7.4-Pfad (gekapselt in `0x12`) und im TDS-8.0-Pfad | [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0003](../../adr/0003-transportframework.md) |
| slice-transport-listener-lebenszyklus | Listener, Verbindungslebenszyklus, TLS-Startprobe, geordnetes Schließen | [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0003](../../adr/0003-transportframework.md) |

Reihenfolge: gerüst vor schleife vor listener-lebenszyklus (jeder Slice nutzt den davor).

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: die funktionalen Wellen für Verbindungsaufbau, Login, SQL_BATCH und RPC (wegen Kanal, verschlüsseltem Strom und Verbindungs-Handler); für Verbindungsaufbau und Login ist es [welle-handshake-login](../welle-handshake-login.md), für SQL_BATCH und RPC [welle-anfrageverarbeitung](../welle-anfrageverarbeitung.md).
- Wird blockiert von: Slice `slice-codec-tds-paket` der Welle [welle-protokollcodec](welle-protokollcodec/welle-protokollcodec.md) (Paket-Codec, Test-Gate); die Welle selbst muss nicht geschlossen sein. [welle-entscheidungen-grundlage](welle-entscheidungen-grundlage/welle-entscheidungen-grundlage.md) blockiert nicht.
- Berührt, blockiert aber nicht: [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md). `slice-qa-stabilitaet` (Fuzz und parallele Verbindungen), `slice-qa-sicherheit` (TLS-Modus) und `slice-qa-kompatibilitaet` (Referenzclient, beide Pfade) bauen auf diesem Transport auf und messen mehr als diese Welle; sie bleiben unverändert (siehe §6).

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Handshake- und Session-Ablauf: kein Verbindungs-Zustandsautomat, keine PRELOGIN-/LOGIN7-Verhandlung als Ablauf, keine Entscheidung über ENCRYPT_REQ, ENCRYPT_ON oder ENCRYPT_NOT_SUP. Die Transportschicht bietet die Schleife und wird mit dem Pfad gerufen; der Zustandsautomat ist [ADR-0004](../../adr/0004-session-zustandsautomat.md) (`Accepted`; Slice [slice-handshake-login](slice-handshake-login.md) der Welle [welle-handshake-login](../welle-handshake-login.md)).
- Backend, Anfrageverarbeitung und Fehlersemantik: kein Backend-Port, kein Dispatch, kein `TDS_SHUTDOWN`, kein Cancel an das Backend ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../../adr/0007-fehler-und-attention.md), beide `Accepted`; Wellen [welle-backend-streaming](../welle-backend-streaming.md), [welle-anfrageverarbeitung](../welle-anfrageverarbeitung.md) und [welle-fehler-abbruch](../welle-fehler-abbruch.md)). Der Listener kennt „laufende Anfrage“ nur als Rückmeldung eines Handlers.
- Authentifizierung ([ADR-0009](../../adr/0009-authentifizierungsmodus.md)): keine Prüfung von Kennung und Passwort.
- Konfigurationsvalidierung (Package `config`), Prozessstart, Composition Root, JVM-Flags des Serverprozesses (`runtime`) und Telemetrie (`observability`).
- Nachweis gegen den Referenzclient und gegen einen echten SQL Server, Last über einige Dutzend Verbindungen und Langzeitläufe: `welle-qualitaet-grundlage` (`slice-qa-kompatibilitaet`, `slice-qa-performance`, `slice-qa-stabilitaet`, `slice-qa-sicherheit`). Die Welle belegt die Transportschicht gegen eine JDK-Gegenstelle.
- MARS, Pooling, Sitzungswiederaufnahme, Neuverhandlung und KeyUpdate in TLS, ECDSA- und Client-Zertifikate.
- Neue Anforderungen oder Änderungen am Lastenheft.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-transport-tls-results.md`
Geschlossen: 2026-10-31 (Belege, Trigger-Audit und Zuordnung *gemessen | Festlegung* stehen in der Ergebnisnotiz)
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: Pfad löst erst nach dem git mv auf) -->
