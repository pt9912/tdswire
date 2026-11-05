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

**Abweichung vom Entwurf (Nutzerentscheidung 2026-11-05):** Die Ursache liegt im Treiber ([Messprotokoll](../../../messungen/tls-d-migrate-2026-11-05.md)); der Nutzer entscheidet, dass der TDS-7.4-Pfad höchstens TLS 1.2 anbietet und der TDS-8.0-Pfad bei TLS 1.3 bleibt. Die Liefer-Punkte unten tragen den geänderten Wortlaut; die Entscheidung hält eine ADR fest.

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
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`SPEC-023`, `SPEC-043`, Sicherheitsprofil, Festlegungen des Einstiegs, Nutzer-Doku, Kompatibilitätsmatrix).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

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

- Die Ursache liegt womöglich nicht beim Server (Treiber, JDK 21, Netty); die Behebung entfällt dann, und die Welle schließt nur mit einer Nutzerentscheidung über diese Grenze (Closure-Trigger „alles funktioniert“). — **Ausgang:** offen bis zur Closure.
- Aufwand der Sonde: M (Diagnose) bis L; die Messung ist kein Code und deshalb ein eigener Liefer-Punkt. — **Ausgang:** offen bis zur Closure.
- Nebenbefund der Sonde, erwartet laut Spezifikation: Bei `REQUIRED` lehnt der Server `{plain}`-Benutzer und PBKDF2 unter 600000 Iterationen ab; die Benutzerliste des Laufs muss das erfüllen (SPEC-012, [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** —
- **Was ging anders als geplant:** —
- **Steering-Loop-Eintrag:** —
- **Beobachtungs-Register (`../observations/`):** —
- **Folge-Slices:** —
- **Risiken aus §6:** —

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste der Eröffnung, Texte nicht einzeln gelesen); mögliche Treffer: [client-schliesst-bei-class-20-selbst](../observations/BEO-TDS/client-schliesst-bei-class-20-selbst/observation.md), [konfiguration-vom-jdk-default-getragen](../observations/BEO-TDS/konfiguration-vom-jdk-default-getragen/observation.md). Der Implementer prüft den Zusammenhang bei Start.

Alle berührten Sub-Areas GF.
