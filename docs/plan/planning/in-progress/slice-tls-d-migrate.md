# Slice slice-tls-d-migrate: TLS mit dem Treiber-Standard (encrypt=true): Messung der Ursache, dann Behebung

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0032](../../adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md)

**Berührte Spec-Stellen:** SPEC-023 (TLS-Versionen und Suiten) · SPEC-043 · Protokollteil TLS im TDS-Mantel (TDS 7.4) · [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung)

**Verantwortlich:** pt9912

**Abweichung vom Entwurf (Nutzerentscheidung 2026-11-05):** Die Ursache liegt im Treiber ([Messprotokoll](../../../messungen/tls-d-migrate-2026-11-05.md)); der Nutzer entscheidet, dass der TDS-7.4-Pfad höchstens TLS 1.2 anbietet und der TDS-8.0-Pfad bei TLS 1.3 bleibt (Obergrenze statt einer Behebung am Server oder am Client), und wählt für das JVM-Flag der Session-Tickets den Weg V2 „Flag entfällt“ (Matrix V0 bis V3 im Messprotokoll). Die Liefer-Punkte unten tragen den geänderten Wortlaut; die Entscheidung hält [ADR-0032](../../adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md) fest. **Was sich gegenüber dem ursprünglichen Entwurf ändert, im Klartext:** (1) Der Entwurf verlangte „Behebung“ der Ursache; geliefert ist keine Behebung des Treibers, sondern eine Obergrenze TLS 1.2 im 7.4-Pfad, für die der Entwurf in §1 (Ausschluss „Absenken des TLS-Modus“) und §4 (Rückführung „blockiert“: Ursache im Treiber, Ausgang „dokumentierte Grenze“) ausdrücklich eine ADR und eine Nutzerentscheidung verlangte; beides liegt vor. (2) Das Ziel „ohne dass TLS 1.2 erzwungen werden muss“ gilt für die Verbindungszeichenfolge des Clients; ausgehandelt wird im 7.4-Pfad TLS 1.2, weil der Server nichts anderes mehr anbietet. (3) Das JVM-Flag `jdk.tls.server.newSessionTicketCount=0` entfällt; damit entfällt die öffentliche Konstante `StartRefusal.JVM_FLAG_MISSING` (siehe §7, inkompatible Änderung).

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** `d-migrate` verbindet sich mit dem Standard des Treibers (`encrypt=true`) und führt eine Abfrage aus, ohne dass TLS 1.2 erzwungen werden muss; die Ursache des Abbruchs nach LOGIN7 ist gemessen und behoben (Hürde 13 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Die Sonde sah: PRELOGIN-Antwort `ENCRYPT_ON/REQ`, TLS-Handshake im TDS-Mantel vollständig (TLS_AES_256_GCM_SHA384, TLS 1.3), der Client sendet LOGIN7, der Server schließt ohne Antwort („Premature EOS ... packetNum:0“); mit `-Djdk.tls.client.protocols=TLSv1.2` Exit 0. **Die Ursache ist nicht geklärt;** der Verdacht (Nachhandshake oder Rahmung der ersten Anwendungsdatensätze unter TLS 1.3) ist nicht gemessen. Deshalb ist die Messung Liefer-Punkt 1 und die Behebung Liefer-Punkt 2: ohne Ursache ist kein Plan der Behebung ehrlich.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Eine Behebung vor der Messung: der Plan der Behebung (Liefer-Punkt 2) wird nach der Messung geschrieben und kann den Slice teilen (Rückführung nach `next/`).
- TDS 8.0 (`Encrypt=Strict`) — [slice-tds8-verdrahtung](../done/slice-tds8-verdrahtung.md); der TLS-Mantel von TDS 7.4 ist ein anderer Pfad.
- Ein Absenken des TLS-Modus oder ein Rückfall auf TLS 1.2 als Konfiguration, um den Lauf grün zu machen: eine Abschwächung nach `AGENTS.md` §3.6 und [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit) braucht eine ADR; hier gilt der Standard des Treibers als Soll.
- Zertifikatsvalidierung des Clients (`trustServerCertificate`): der Lauf nimmt den Wert der Sonde; Vertrauensfragen sind nicht Gegenstand.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [x] **Messung:** Ein Mitschnitt oder ein Log des Servers (TLS-Version, Cipher, Richtung der Datensätze, erstes Anwendungspaket nach dem Handshake) nennt, an welcher Stelle der Server nach LOGIN7 schließt; die Ursache steht als Befund mit Rechner, Last, JDK-Version des Treibers und Image-Digest im Slice oder in `docs/messungen/`. Vergleich: derselbe Lauf mit dem .NET-Referenzclient (Version laut Clientprofil) und mit `-Djdk.tls.client.protocols=TLSv1.2`. Beleg: [tls-d-migrate-2026-11-05](../../../messungen/tls-d-migrate-2026-11-05.md).
- [x] **Behebung:** Der TDS-7.4-Pfad (Handschlag in PRELOGIN-gerahmten Paketen) bietet höchstens TLS 1.2 an, der TDS-8.0-Pfad bleibt bei TLS 1.3 (SPEC-023); Tests in der Bibliothek halten beides fest (ein reiner TLS-1.3-Client scheitert im 7.4-Pfad, der 8.0-Pfad handelt TLS 1.3 aus); die Behebung ändert keinen TLS-Default der Konfiguration ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)); die Entscheidung steht in einer ADR (Architect), [ADR-0032](../../adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md). Beleg: die Tests `TlsHandshakeNegativeTest > Ein reiner TLS-1_3-Client scheitert im TDS-7_4-Pfad` und `TlsLoopbackTest > both versions offered negotiate by path and the path decides about ALPN`; zur Behebung gehören außerdem die Startprobe, die das NewSessionTicket des TLS-1.3-Servers verträgt (`TlsStartProbe`, `ProbeClient`), der Wegfall der Prüfung des JVM-Flags im Start (`Bootstrap`, `TdsServer`) und der Wegfall der öffentlichen Konstante `StartRefusal.JVM_FLAG_MISSING` ([ADR-0032](../../adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md) Folgepflicht; berührt die öffentliche Fläche aus `SPEC-059`); der Server startet und läuft ohne JVM-Option (`BootstrapTest > Der Start mit TLS gelingt ohne JVM-Option`, `ServerSmokeTest > Ein Prozess ohne JVM-Option startet mit TLS`).
- [x] **Beleg am fremden Client:** d-migrate 1.12.0 mit `encrypt=true&trustServerCertificate=true` im Treiber-Standard (ohne `sslProtocol`) gegen den Testserver (`REQUIRED`) meldet sich an; `schema reverse` endet dann mit Exit 4 „Unsupported SQL“, der nächsten, fremden Hürde (Hürde 2 des Parallel-Slice). Ausgabe mit Rechner, Last und Image-Digest. Der .NET-Referenzclient (`make kompatibilitaet KATEGORIE=Anmeldung`) bleibt grün. Beleg: Abschnitt „Umsetzung der Entscheidung“ in [tls-d-migrate-2026-11-05](../../../messungen/tls-d-migrate-2026-11-05.md) (d-migrate Exit 4 „Unsupported SQL“ nach gelungener Anmeldung; .NET 19 von 19).

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün (`make gates FORCE=1`, Exit 0, Stand nach dem Commit der Umsetzung).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
      Beleg: [Review](../../../reviews/2026-11-05-d616a77-slice-tls-d-migrate.md) (kein HIGH, 5 MEDIUM, 4 LOW, 5 INFO) und [Verifikation](../../../reviews/2026-11-05-80d221b-verifikation-slice-tls-d-migrate.md) (closure-fähig mit drei Bedingungen).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`SPEC-023`, `SPEC-043`, Sicherheitsprofil, Festlegungen des Einstiegs, Nutzer-Doku, Kompatibilitätsmatrix).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag (§7).
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben (§7).
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen (§7: kein neuer Zielort, kein Folge-Slice, Register geschrieben).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/transport/tls/` (`TlsAcceptor`, `TlsStartProbe`), `.../runtime/` (`Bootstrap`, `TdsServer`), `.../domain/telemetry/` (`StartRefusal`) | update | Obergrenze im 7.4-Pfad, Startprobe mit Ticket, Prüfung des JVM-Flags entfällt ([ADR-0032](../../adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md)) |
| `Dockerfile`, `build.gradle.kts`, `gradle/test-jvm.init.gradle.kts`, `src/testserver/` | update | Startzeilen ohne JVM-Flag |
| Tests unter `src/test/kotlin/` | update, neu | `TlsSessionTicketTest`, Probe mit Ticket, Start ohne JVM-Option, Obergrenze-Tests |
| `spec/spezifikation.md`, `docs/user/testserver-manuell.md`, `docs/plan/kompatibilitaetsmatrix.md` | update | `SPEC-023`, `SPEC-043`, Sicherheitsprofil; Nutzer-Doku und Matrix ohne Flag |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-prelogin-serverversion](../done/slice-prelogin-serverversion.md) liegt in `done/` (ohne sie kommt d-migrate nicht bis LOGIN7); der Slice hängt nicht an der Entscheidung in [slice-adr-testserver-bestand-und-katalog](../open/slice-adr-testserver-bestand-und-katalog.md).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Messung zeigt eine Ursache, deren Behebung mehr als zwei Liefer-Punkte verlangt; Schnittvorschlag: Messung als eigener Slice.
- `in-progress` → `open` (blockiert): die Behebung verlangt eine Änderung der TLS-Defaults (ADR nötig) oder die Ursache liegt im Treiber/JDK und nicht im Server (dann Ausgang als dokumentierte Grenze statt Behebung, Nutzerentscheidung).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, die Ursache ist benannt, der d-migrate-Lauf mit `encrypt=true` liegt vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Die Ursache liegt womöglich nicht beim Server (Treiber, JDK 21, Netty); die Behebung entfällt dann, und die Welle schließt nur mit einer Nutzerentscheidung über diese Grenze (Closure-Trigger „alles funktioniert“). — **Ausgang:** eingetreten. Die Ursache liegt im Treiber (mssql-jdbc 13.4.0 sendet unter TLS 1.3 im 7.4-Pfad sein `Finished` nicht auf den Draht, [Messprotokoll](../../../messungen/tls-d-migrate-2026-11-05.md) M2); der Nutzer entschied am 2026-11-05 für die Obergrenze TLS 1.2 im 7.4-Pfad, [ADR-0032](../../adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md). Der Treiber ist nicht behoben.
- Aufwand der Sonde: M (Diagnose) bis L; die Messung ist kein Code und deshalb ein eigener Liefer-Punkt. — **Ausgang:** entfallen. Der Slice blieb bei drei Liefer-Punkten, die Rückführung „zu groß“ trat nicht ein; ein gemessener Aufwand liegt nicht vor.
- Nebenbefund der Sonde, erwartet laut Spezifikation: Bei `REQUIRED` lehnt der Server `{plain}`-Benutzer und PBKDF2 unter 600000 Iterationen ab; die Benutzerliste des Laufs muss das erfüllen (SPEC-012, [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). — **Ausgang:** eingetreten und gelöst. Die Läufe des Messprotokolls nehmen `alice` mit `{pbkdf2-sha256}` und 600000 Iterationen (ein Testwert); die Benutzerliste ist keine Änderung dieses Slice.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

**Gegenstand:** geliefert, DoD vollständig, mit sichtbaren Abweichungen vom Entwurf (Kopf des Plans). Code: `3de8b98` bis `80d221b` (18 Commits auf dem Zweig `slice-tls-d-migrate`, Basis `6e91aec`): Messung und Rohbytes-Tests (`3de8b98`, `00cfab8`), Obergrenze TLS 1.2 im 7.4-Pfad (`ab531ef`, `78dd86b`), Matrix der JVM-Flags (`a7324a8`), [ADR-0032](../../adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md) (`355d7e6`), Umsetzung ohne JVM-Flag mit Startprobe, die die Gegenstelle bis zum Urteil offen lässt (`198e31a`, `4f86ab3`, `ec62946`, `de9c560`, `1dacddb`, `d616a77`), Fix-Runde des Reviews (`f8017d1`, `be0d56d`, `a37d21a`, `f8b95bb`, `4ef5bfd`), Abdeckungsmatrix (`80d221b`). Berichte: [Review](../../../reviews/2026-11-05-d616a77-slice-tls-d-migrate.md) (kein HIGH, 5 MEDIUM, 4 LOW, 5 INFO; F-1 bis F-9 behoben, F-2 durch Eingrenzung der Aussage), [Verifikation](../../../reviews/2026-11-05-80d221b-verifikation-slice-tls-d-migrate.md) (closure-fähig mit den Bedingungen B-1 bis B-3, kein HIGH, kein MEDIUM). Messprotokoll: [tls-d-migrate-2026-11-05](../../../messungen/tls-d-migrate-2026-11-05.md).

| Bedingung der Verifikation | Ausgang |
|---|---|
| B-1 `make gates` | `make gates FORCE=1` im Zweig `slice-tls-d-migrate` am 2026-11-05 nach dem Verifikationsbericht (Stand `cf70111` mit den Dokuänderungen dieser Closure): Exit 0, 26 s Wanduhr bei Last 3 (Docker-Stages `test`, `detekt` und `build-jar` kamen aus dem Cache, weil sich ihre Eingaben seit `80d221b` nicht ändern; den Frischbeleg der Tests liefert `make clean-build` der Verifikation, 909 Testfälle). Alle vier Selbsttests der Stempel, `docs-check` (761 Dateien, 0 Befunde), `gate-zuordnung` und `stempel-eingaenge` grün. Der Lauf auf `main` folgt nach dem Merge und ist hier nicht belegt. |
| B-2 Closure-Pflichten (Ausgänge in §6, Steering-Loop-Eintrag, Register, Haken) | erledigt in diesem Commit: drei Ausgänge in §6, Eintrag und Register unten, Haken „Review“ und „Closure-Notiz“ gesetzt. Die §8 genannten möglichen Treffer `client-schliesst-bei-class-20-selbst` und `konfiguration-vom-jdk-default-getragen` sind geprüft: kein Auftreten in diesem Slice (kein Class-20-Fall; die Tests der Ticket-Zahl und der Obergrenze tragen ihre Konfiguration selbst, die Mutation „Obergrenze entfernen“ färbt sie rot). |
| B-3 Notiz zur öffentlichen Fläche und zu V-1, V-2 | siehe „Inkompatible Änderung“ und „Grenzen“ unten; V-1 und V-2 stehen im Register (`zeile-oder-zweig-ohne-unterscheidenden-test`). |

**Messungen** (Einzelmessungen, ein Host Linux 6.8 mit 20 Kernen, Last 4 bis 41 je nach fremden Läufen; Zeiten sind keine Leistungsaussage; Details im Messprotokoll):

- Ursache: mssql-jdbc 13.4.0 (JDK 21) sendet unter TLS 1.3 im TDS-7.4-Pfad sein `Finished` nicht auf den Draht und schickt LOGIN7 als rohen TLS-Record (Typ `0x17` ohne TDS-Kopf); der Server erwartet im Handschlag ein Paket `0x12` und trennt (M2, socat-Mitschnitt: 1020 Client-Bytes = 67 PRELOGIN + 430 ClientHello + 523 LOGIN7-Record). Unter TLS 1.2 trägt der Treiber das `Finished` in einem Paket `0x12`, die Anmeldung gelingt (M3).
- Matrix V0 bis V3 der JVM-Flags des Serverprozesses: V0 (mit `newSessionTicketCount=0`) lässt den .NET-Referenzclient im 7.4-Pfad rot (2 von 19 grün, 17 mal `error:0A0000F4 ... unexpected message`), V1 bis V3 grün (19 von 19); die 8.0-Tests und d-migrate sind in allen vier Varianten grün. Daraus die Nutzerentscheidung V2.
- Nach der Umsetzung (Server ohne JVM-Option): `make kompatibilitaet KATEGORIE=Anmeldung` 19 von 19; Szenario 13 von 13; `Luecken` 7 von 7; `Synchronitaet` 4 von 4; `make simulations-integration` 4 von 4; `make smoke` grün; `make clean-build` (Verifikation, `--no-cache`, 6 min 35 s) 909 Testfälle `PASSED`, keiner `FAILED`.
- d-migrate 1.12.0 mit `encrypt=true&trustServerCertificate=true`, ohne `sslProtocol` und ohne JVM-Option, gegen den Testserver (`REQUIRED`): die Anmeldung gelingt, `schema reverse` endet mit Exit 4 „Failed to initialize pool: Unsupported SQL.“, der nächsten Hürde (Hürde 2, [slice-d-migrate-verbindung-und-export](../done/slice-d-migrate-verbindung-und-export.md)).

**Nutzerentscheidungen (2026-11-05):** (1) Obergrenze TLS 1.2 im 7.4-Pfad („Weg 4“), der 8.0-Pfad bleibt bei TLS 1.3, statt einer Behebung am Server oder am Client; (2) für das JVM-Flag der Session-Tickets Weg V2 „Flag entfällt“ (weder `newSessionTicketCount=0` noch `enableSessionTicketExtension=false` verlangt); beides in [ADR-0032](../../adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md).

**Noch offene Nutzerentscheidung:** Ein Wächter gegen ein weiter gesetztes Flag `jdk.tls.server.newSessionTicketCount=0` im Prozess eines Einbettenden. Heute ist das ein **akzeptiertes Negativ**: Der Start prüft das Flag nicht mehr; ein Einbettender, der es setzt, bekommt im 7.4-Pfad TLS 1.2 mit angekündigter, aber nie gesendeter `session_ticket`-Erweiterung, und Clients, die ein Ticket erwarten, brechen ab (gemessen mit dem .NET-Referenzclient, V0). Ob ein Wächter (Prüfung beim Start oder Sensor) nötig ist, ist nicht entschieden und nicht gebaut.

**Grenzen (nicht belegt, `AGENTS.md` §3.8):**

- Der Quelltextbefund zu `IOBuffer.java` (`SSLHandshakeOutputStream` puffert die Handshake-Bytes) stammt vom Zweig `main` von mssql-jdbc, nicht vom Tag 13.4.0, und ist nicht selbst gelesen; die Ursache ist am Draht gemessen, der Mechanismus im Treiber nicht.
- Die Aussage nach Microsoft Learn (ein echter SQL Server verhandelt im TDS-7.x-Pfad höchstens TLS 1.2) steht dort „laut Suchergebnis“ und ist nicht selbst am Server gemessen; ein echter SQL Server wurde nicht befragt.
- Die Ursache der Startprobe ist durch Eingriffe (Ende sofort schließen, Flag in der Test-JVM) erschlossen, kein Trace der einzelnen Schreiboperation; der Speicherkanal der Probe fasst 64 KiB je Richtung, die Probe liest deshalb weiter statt eine Größe zuzusagen; getestet ist das Vierfache der Kanalgröße (V-3, als Setzung gekennzeichnet).
- Nicht gemessen: Wiederaufnahme einer Sitzung mit dem Ticket, andere Treiber und Treiberversionen, ECDSA-Zertifikate, ein echter SQL Server.
- V-1: die Zeile `clientEnd.close()` bei Fehler der Gegenstelle in der Startprobe hat keinen Test (ihr Entfernen färbt keinen Test rot); die Aussage ist darauf eingegrenzt, der Zweig bleibt offen (Test mit kurzer Frist oder Zeile streichen).
- V-2: „Bytes hinter dem letzten Handshake-Paket“ (dichte Packung) ist im 7.4-Pfad nur auf der Ebene des Lesepuffers (`NetInboundTest`) belegt, nicht Ende zu Ende mit der Engine unter TLS 1.2.

**Inkompatible Änderung der öffentlichen Fläche:** `StartRefusal.JVM_FLAG_MISSING` ist entfernt ([SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059), Ereignisarten, Redaktion und Änderungsverlauf nachgezogen). Quell- und Binärinkompatibilität für Einbettende mit erschöpfendem `when` über `StartRefusal`; ein Start, der früher damit abbrach, gelingt jetzt. Es gibt kein veröffentlichtes Artefakt und keine Versionspolitik (Bibliothek vor 1.0); die Verifikation empfiehlt, die Entfernung zu bestätigen (Alternative „Konstante behalten, nie mehr werfen“ ließe einen toten Abbruchgrund in der Fläche). **Kante zu** [slice-oeffentliche-flaeche-verkleinern-und-belegen](../open/slice-oeffentliche-flaeche-verkleinern-und-belegen.md): Die Entfernung gehört in dessen Ausgangsliste der Fläche (Hinweis in dessen §6); den Schnitt nimmt dieser Slice nicht vorweg.

**Welle: Slices, die an diesem Slice hängen (Start-Trigger, nicht geändert):** [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md) startet erst, wenn alle anderen Slices des Solls in `done/` liegen, darunter dieser; die Abnahme ist der einzige Slice mit dieser Kante. [slice-adr-testserver-bestand-und-katalog](../open/slice-adr-testserver-bestand-und-katalog.md) führt diesen Slice in der Liste der Anschlussstellen (Spec-Änderungen an SPEC-059 u. a.), ohne Start-Trigger; die Änderung an SPEC-059 durch diesen Slice ist dort bei der Nachführung der Liste zu lesen. [welle-testserver-manuell](../welle-testserver-manuell.md) nennt den Slice nur nach [slice-prelogin-serverversion](../done/slice-prelogin-serverversion.md); sein Kopf und die Kantenliste der Welle zeigen auf den Pfad `in-progress/`.

**Steering-Loop-Eintrag:** geschärfte Beobachtungen, keine neue Regel, kein Zielort (kein Feld `liegt in`): (1) Nach der Umstellung des 7.4-Pfads auf TLS 1.2 standen Zusagen ohne Test da, weil Tests mit der Umstellung entfielen oder auf einen Pfad beschränkt wurden; der Review fand sie per Mutation. Klasse [BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test](../observations/BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/observation.md) (weiteres Auftreten, Klasse bereits verkörpert in `.harness/skills/reviewer.md`). Für den Slice galt: wer einen Pfad auf eine andere Protokollversion umstellt, listet die Tests, die an der alten Version hingen, und belegt jede Zusage neu oder nennt die Lücke (V-2). (2) KDoc, Spec und Nutzer-Doku sagten mehr als das Messprotokoll (Reihenfolge des Tickets, Verbot weiterer Systemeigenschaften, „nicht gegen Microsoft.Data.SqlClient“ neben Messungen mit ihm); Klasse [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (weiteres Auftreten, verkörpert in `AGENTS.md` §3.8). (3) Kommentare und ein Ist-Abschnitt der Spezifikation nannten das entfallene Flag und den früheren Stand im Präsens; Klasse [BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand](../observations/BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand/observation.md) erreicht damit **3×**.

**Beobachtungs-Register:** drei weitere Dateien: `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/evidence/slice-tls-d-migrate.md`, `BEO-TDS/aussagen-ueber-messung-hinaus/evidence/slice-tls-d-migrate.md`, `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand/evidence/slice-tls-d-migrate.md` (dessen `state.md` ist auf „3×, über der Schwelle“ fortgeschrieben; der Lese-Schritt liegt bei der Closure der Welle). Keine neue Klasse.

**Folge-Slices:** keine neuen. Hürde 2 und die weiteren Hürden liegen bei [slice-d-migrate-verbindung-und-export](../done/slice-d-migrate-verbindung-und-export.md) und den übrigen Slices der Welle.

**Risiken aus §6:** alle drei mit Ausgang (eingetreten und per Nutzerentscheidung und ADR gelöst · entfallen · eingetreten und gelöst).


## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste der Eröffnung, Texte nicht einzeln gelesen); mögliche Treffer: [client-schliesst-bei-class-20-selbst](../observations/BEO-TDS/client-schliesst-bei-class-20-selbst/observation.md), [konfiguration-vom-jdk-default-getragen](../observations/BEO-TDS/konfiguration-vom-jdk-default-getragen/observation.md). Der Implementer prüft den Zusammenhang bei Start.

Alle berührten Sub-Areas GF.
