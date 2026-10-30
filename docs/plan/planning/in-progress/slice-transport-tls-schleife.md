# Slice slice-transport-tls-schleife: TLS-Schleife: SSLEngine in beiden Pfaden (TDS 7.4 gekapselt in 0x12, TDS 8.0 ab dem ersten Byte)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-transport-tls

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0003](../../adr/0003-transportframework.md)

**Berührte Spec-Stellen:** SPEC-010 · SPEC-023 · SPEC-028 · SPEC-046 · ARC-002 · ARC-003 · ARC-013

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Eine eigene `SSLEngine`-Schleife macht aus dem Kanal des Gerüsts einen verschlüsselten Kanal derselben Schnittstelle, in beiden Pfaden: im TDS-7.4-Pfad läuft der Handshake in Paketen vom Typ `0x12` und schaltet am Ergebnis `FINISHED` auf rohes TLS um, im TDS-8.0-Pfad läuft TLS ab dem ersten Byte mit ALPN `tds/8.0`. Die Aussage steht an Loopback-Tests mit einer JDK-`SSLEngine` als Gegenstelle.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Wahl des Pfades und der PRELOGIN-Antwort (ENCRYPT_REQ, ENCRYPT_ON, ENCRYPT_NOT_SUP) sowie der Zeitpunkt, zu dem ein Handshake beginnt — das ist Ablauf im Zustandsautomaten ([ADR-0004](../../adr/0004-session-zustandsautomat.md), `Proposed`); die Schleife wird mit dem Pfad als Parameter gerufen und entscheidet ihn nicht. Der unverschlüsselte Testmodus (ENCRYPT_NOT_SUP) braucht keine Schleife und ist kein Gegenstand.
- Laden von Zertifikat und Schlüssel aus der Konfiguration, die Teile `CERTIFICATE`, `PRIVATE_KEY`, `PAIR` und die Startprobe (SPEC-043) — das ist `slice-transport-listener-lebenszyklus`; die Schleife bekommt einen fertigen `SSLContext` und kennt keine Dateipfade.
- Konfigurationsvalidierung und Auswertung von `ENCRYPTION_POLICY` (SPEC-010, ARC-002): Konfiguration liegt im Package `config`; die Schleife nimmt Protokollversionen und Suiten als Parameter, die Tests nutzen die Werte von SPEC-023.
- Listener, Annahme und Timeouts (`LOGIN_TIMEOUT`) — `slice-transport-listener-lebenszyklus`.
- Nachrichteninhalt (PRELOGIN-Tabelle, LOGIN7): die Tests senden opake Nachrichten; ein Smoke-Test darf den Message-Codec aus `welle-protokollcodec` aufrufen, nicht erweitern.
- ECDSA-Zertifikate, `TLS_CHACHA20_POLY1305_SHA256`, Client-Zertifikate, Wiederaufnahme von Sitzungen und Neuverhandlung — ungemessen laut `contracts/security-profile.md` und für das Clientprofil nicht gefordert.
- Läufe gegen den Referenzclient und gegen einen echten SQL Server — `slice-qa-kompatibilitaet`; dieser Slice belegt die Schleife gegen die JDK-Gegenstelle, nicht die Annahme durch `Microsoft.Data.SqlClient`.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **SSLEngine-Schleife** (SPEC-023, SPEC-028, [ADR-0003](../../adr/0003-transportframework.md)): `wrap`, `unwrap`, ausgelagerte Tasks und alle `NEED_*`-Zustände auf dem Kanal des Gerüsts; Mindestversion TLS 1.2, Suiten nach SPEC-023; geordnetes Schließen (`close_notify`) und Fehler (Handshake-Fehler, abgebrochene Verbindung, Protokollverstoß) als Ergebniswerte mit Kategorie, nie als ungefangene Ausnahme und ohne Zertifikats- oder Schlüsselinhalt in der Meldung. Der Puffer für entschlüsselte Daten ist begrenzt.
- [x] **TDS-7.4-Pfad** (SPEC-028, [ADR-0002](../../adr/0002-tls-und-verschluesselung.md)): Handshake-Bytes der Engine werden in Pakete vom Typ `0x12` verpackt und Bytes der Gegenstelle daraus ausgepackt (über den Paket-Codec, ohne ihn zu duplizieren); mit `FINISHED` schaltet der Kanal auf rohes TLS auf dem Strom um, ohne Bytes zu verlieren, die im Lesepuffer hinter dem letzten Handshake-Paket liegen. Mit `-Djdk.tls.server.newSessionTicketCount=0` gelingt der Handshake auch in TLS 1.3; ein Test belegt, was ohne das Flag geschieht, oder kennzeichnet die Aussage als Festlegung.
- [x] **TDS-8.0-Pfad** (SPEC-028, [ADR-0002](../../adr/0002-tls-und-verschluesselung.md)): TLS ab dem ersten Byte des Stroms, ALPN `tds/8.0` wird angeboten und geprüft; eine Gegenstelle ohne dieses ALPN wird abgewiesen (Festlegung, siehe §6). Nachrichten laufen danach durch denselben Nachrichtenleser und -schreiber des Gerüsts wie im 7.4-Pfad.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [x] Loopback-Tests je Pfad mit einer JDK-`SSLEngine` als Gegenstelle und einem Testzertifikat (siehe §6): Nachrichten in beiden Richtungen, byteweise und zufällig zerstückelt (fester Startwert), kommen gleich an; TLS 1.2 und TLS 1.3, je mit den Suiten aus SPEC-023.
- [x] Negativfälle enden in einem Ergebniswert: abgelaufenes oder nicht passendes Zertifikat, nur TLS 1.1 angeboten, Handshake-Bytes in einem Paket falschen Typs, abgeschnittener Handshake, Klartext statt TLS im 8.0-Pfad, abrupt geschlossener Kanal ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). Kein stiller Rückfall auf Klartext: ein Test belegt, dass nach gescheitertem Handshake kein Anwendungs-Byte im Klartext den Kanal verlässt.
- [x] Die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz; eine Aussage wie „der Referenzclient akzeptiert das“ steht weder in KDoc noch in der Notiz.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`contracts/security-profile.md` nur, wenn eine Festlegung (ALPN-Abweisung, Flag-Prüfung) den Vertrag ändert; sonst bleibt er unverändert).
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
| `src/main/kotlin/io/github/pt9912/tdswire/transport/` (Unterpackage, z. B. `tls`) <!-- d-check:ignore (geplant: neue Datei) --> | neu | SSLEngine-Schleife als Kanal-Dekorierer, Verpackung in `0x12`-Pakete, Umschaltung bei `FINISHED`, ALPN-Prüfung; keine Imports aus `port`, `simulation`, `runtime` (SPEC-041) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Loopback je Pfad gegen JDK-`SSLEngine`, Zerstückelung, Negativfälle; `@spec`-Tag je Test (SPEC-023, SPEC-028) |
| Testzertifikat und Test-Hilfen | neu | Herkunft offen (Frage an den Nutzer, §6); kein Schlüssel im Repo ohne Entscheidung |
| `build.gradle.kts` | unverändert (Ist-Zustand) | Die Test-JVM-Einstellungen (`-Djdk.tls.server.newSessionTicketCount=0`, `tdswire.testkeys`, `tdswire.testclasspath`) stehen im Init-Skript `gradle/test-jvm.init.gradle.kts`, nicht hier: eine Änderung an `build.gradle.kts` würde den Cache der Stage `deps` neu laufen lassen. Ein `gradle test` ohne `-I gradle/test-jvm.init.gradle.kts` setzt sie nicht; `make test` tut es. Das Flag des Serverprozesses setzt der Start (nicht dieser Slice) |

Erweiterung im ersten Lauf (Ist-Zustand gegen den Plan gemessen: `transport/channel`, `transport/messaging` und `transport/framing` liegen, `config/detekt/detekt.yml` sperrt für `transport` nur `port`, `simulation`, `runtime`; Dockerfile-Stages `test` und `spec-abdeckung` führen `gradle test`):

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `transport/tls/` <!-- d-check:ignore (geplant: neues Package) --> | neu | `TlsChannel` (Kanal-Dekorierer), `TlsHandshake`, `TlsRecordIo`, `NetInbound`/`NetOutbound` (Rohstrom oder `0x12`-Pakete), `TlsError`/`TlsResult`, `TlsParameters`/`TlsPath`; nur Imports aus `transport.channel`, `transport.framing`, JDK und Coroutinen |
| `Dockerfile` | update, additiv | Stage `testkeys` erzeugt per `keytool` die PKCS#12-Speicher (gültig, falscher Name, abgelaufen von einer Test-CA signiert); `test` und `spec-abdeckung` kopieren sie nach `build/testkeys` und laden das Init-Skript |
| `gradle/test-jvm.init.gradle.kts` <!-- d-check:ignore (geplant: neue Datei) --> | neu, statt Änderung an `build.gradle.kts` | Test-JVM: `-Djdk.tls.server.newSessionTicketCount=0` und Systemeigenschaften `tdswire.testkeys`, `tdswire.testclasspath`; ein Init-Skript lässt die Eingänge der Stage `deps` unverändert, sodass deren Cache nicht neu läuft |
| `src/test/.../testsupport/TlsTestSupport.kt` und `transport/tls/*Test.kt` <!-- d-check:ignore (geplant: neue Dateien) --> | neu | unabhängige Gegenstelle auf `SSLEngine`-Basis, Zerstückelung, handgebautes TLS-1.1-ClientHello, Loopback je Pfad |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

- Reihenfolge: zuerst die Schleife auf rohem Strom (der 8.0-Pfad ist ihr Grundfall), dann die `0x12`-Kapselung mit Umschaltung, zuletzt ALPN und Negativfälle.
- Die Layout-Quelle ist MS-TDS (SPEC-020, extern) für die `0x12`-Kapselung; wo die Spezifikation offen lässt, nimmt der Implementer die Lesart aus MS-TDS und kennzeichnet sie im KDoc als Festlegung.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-transport-tls` ist eingetreten, `slice-transport-coroutines-geruest` liegt in `done/` (erfüllt: Kanal, Nachrichtenleser und -schreiber stehen), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die `0x12`-Kapselung samt Umschaltung sprengt den Rahmen. Schnittvorschlag: Slice B1 = Schleife und TDS-8.0-Pfad, Slice B2 = TDS-7.4-Kapselung; die Welle bekommt dann vier Slices.
- `in-progress` → `open` (blockiert — Carveout?): die Engine liefert im Test keinen verlustfreien Umschaltpunkt (Bytes hinter dem letzten Handshake-Paket gehen verloren) und eine Lösung braucht eine Entscheidung zum Zustandsautomaten ([ADR-0004](../../adr/0004-session-zustandsautomat.md)); oder das Testzertifikat ist ungeklärt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Umschaltpunkt `FINISHED` ist im Spike nicht einzeln geprüft ([ADR-0003](../../adr/0003-transportframework.md)); Bytes hinter dem letzten Handshake-Paket im Lesepuffer sind für `SSLEngine` ungemessen (für Netty ausdrücklich nicht gemessen). Ein Test mit dicht gepackten Bytes ist Pflicht; ein Fehler dort ist ein Befund dieses Slice. — **Ausgang:** entfallen: nicht eingetreten; der Test mit dicht gepackten Bytes (`bytes behind the last handshake packet reach the data phase`) besteht und ist rot, wenn der Lesepuffer beim Umschalten verworfen wird (Mutation R01 der Verifikation); ein kleiner Rest hinter `FINISHED` scheitert erst in der Datenphase (fail-closed, Review I3).
- TLS 1.3 sendet nach dem Handshake Nachrichten (NewSessionTicket, KeyUpdate), die im 7.4-Pfad nicht in `0x12`-Pakete gehören; das Flag löst nur den Ticket-Fall. KeyUpdate und Neuverhandlung sind nicht behandelt und stehen in der Notiz als Grenze. — **Ausgang:** weiter offen: → `slice-qa-sicherheit` §6 (Neuverhandlung in TLS 1.2). Der Ticket-Fall ist durch das Flag gelöst und mit und ohne Flag getestet, KeyUpdate ist getestet (ein kurzer Record, Inhalt der Antwort unbeobachtet); nur die Neuverhandlung bleibt offen.
- Herkunft des Testzertifikats: Erzeugen zur Laufzeit ohne Fremdbibliothek ist mit der JDK-Standardbibliothek nur über interne Klassen oder `keytool` im Build möglich, ein eingechecktes Schlüsselpaar ist ein Geheimnis-Fund für Scanner und Review. Entschieden (Nutzer, 2026-10-30): Das Testzertifikat entsteht im Build per `keytool`, kein Schlüsselpaar im Repo. — **Ausgang:** entfallen: entschieden (Nutzer, 2026-10-30: `keytool`, PKCS#12) und umgesetzt; die Stage `testkeys` erzeugt die Speicher im Build, die Suche nach Schlüsseldateien im Repo bleibt ohne Treffer (Verifikation §4).
- ALPN-Verhalten bei fehlendem `tds/8.0`: ADR und Sicherheitsprofil messen nur den Erfolgsfall mit ALPN; die Abweisung ist eine Festlegung dieses Slice, kein gemessenes Verhalten des Referenzclients. — **Ausgang:** entfallen: als Festlegung dokumentiert (KDoc, `contracts/security-profile.md` §Festlegungen der TLS-Schleife) und gegen die JDK-Gegenstelle getestet; die Messung am Referenzclient steht im Risiko darunter.
- Das JVM-Flag gilt prozessweit; wer es im Serverprozess setzt und prüft, ist offen (Start gehört zu `runtime`, nicht zum Transport). Dieser Slice setzt es nur für die Test-JVM; die Lücke steht in der Notiz. — **Ausgang:** weiter offen: → `slice-bootstrap-konfiguration` (DoD *Composition Root, `main`, Start und Stop*: Setzen und Prüfen des Flags im Serverprozess, ergänzt mit dieser Closure).
- Die Aussagen gelten gegen die JDK-Gegenstelle desselben Prozesses; der Referenzclient (.NET) und TDS 8.0 am echten Server sind ungemessen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/messung-am-echten-sql-server-fehlt`). — **Ausgang:** weiter offen: → Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (Evidence `slice-transport-tls-schleife`) und `slice-qa-kompatibilitaet` §6.
- Strenge der statischen Analyse: eine Zustandsschleife über `HandshakeStatus` neigt zu `CyclomaticComplexMethod` und `LongMethod`; die Abhilfe ist Zerlegung im Code. — **Ausgang:** entfallen: nicht eingetreten; die Schleife ist in zehn Dateien zerlegt, `make detekt` ist grün, `config/detekt/detekt.yml` unverändert und kein `@Suppress`.

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

- **Was hat funktioniert:** Der Schnitt trug: Schleife, `0x12`-Kapselung und TDS-8.0-Pfad lagen in einem Slice, ohne dass die Rückführung nach §4 nötig wurde. Der Umschaltpunkt `FINISHED` verlor in keinem Lauf Bytes; der Test mit dicht gepackten Bytes ist rot, wenn der Lesepuffer beim Umschalten verworfen wird (Mutation R01 der Verifikation). Eine unabhängig geschriebene Gegenstelle (`JdkTlsPeer`) und die Zertifikate der Stage `testkeys` (per `keytool` im Build, kein Schlüssel im Repo) reichten für alle Negativfälle. Die TLS-Tests sind nach den Befunden 36 (`TlsLoopbackTest` 14, `TlsNegativeTest` 13, `TlsDataPhaseTest` 6, `TlsTicketFlagTest` 3).
- **Was ging anders als geplant:** (a) **Die Leseseite hing an der Schreibsperre** (Review M1): nach jedem Record nahm der Leser die Sperre des Schreibers und wartete, wenn dieser auf einen vollen Sendepuffer wartete; ein ATTENTION-Paket wäre liegen geblieben. Behoben (Commit `43d3958`): `answer()` nimmt die Sperre per `tryLock`, ist der Schreiber im Gang, erledigt dieser die Antwort. Test `a blocked writer does not hold up the reader`. **Grenze:** ist die Sperre frei, schreibt der Leser die Antwort selbst und kann dabei auf einen vollen inneren Kanal warten; der Wettlauf zwischen Vormerken und Loslassen ist nicht belegt. (b) **Die Konfiguration war nicht belegt** (Review M3, Verifikation F1): die Mutationen „Suiten“ und „Protokolle der Konfiguration nicht gesetzt“ blieben grün, weil das JDK TLS 1.1 ohnehin sperrt und beide Seiten dieselbe Konfiguration bekamen; behoben durch die Negativtests `peer without a common version is rejected` und `peer without a common suite is rejected` mit Gegenprobe. (c) **Die KeyUpdate-Aussage trug weiter als der Beleg** (Review M2): der Test prüfte nur, dass die Nachrichten weiterlaufen; er beobachtet jetzt genau einen kurzen Record des Servers, der Inhalt der Antwort bleibt unbeobachtet, und das Sicherheitsprofil sagt es so. (d) Weitere Befunde eingearbeitet: L1 bis L5 (tote Konstante, Schließen bei unerwarteter Ausnahme, Prüfung der Suiten in `TlsParameters`, Fehlerursache der Zertifikatstests, Timeout der Kind-JVM) sowie Tests für das Schließen des inneren Kanals nach Abbruch und nach `closeGracefully` und für das Schreiben nach ausgangsseitigem Schließen (Mutationen N08, N18, N15). (e) **Nicht gedeckt, und das steht fest:** die Zweige für wachsende Puffer und `BUFFER_OVERFLOW` (N02 bis N04) und „Anwendungsdaten im Handshake“ (N01) tragen nur eine Kennzeichnung im KDoc; mit der JDK-Engine sind sie vermutlich nicht erreichbar. Die Antwort auf `close_notify` schon beim Lesen (N17) ist als gleichwertig eingestuft: die JDK-Engine antwortet ohne `jdk.tls.acknowledgeCloseNotify` nicht von selbst, eine Verhaltensänderung wäre eine eigene Entscheidung (Träger unten); getestet ist die Antwort beim anschließenden Schließen. Eine RSA-Austausch-Suite lässt sich nicht testen, weil der Client sie nicht anbietet; ihre Abweisung beruht auf der Suitenliste der Konfiguration.
- **Reports:** [Review](../../../reviews/2026-10-30-1dad1ad-slice-transport-tls-schleife.md) (kein HIGH; M1 bis M3 MEDIUM; L1 bis L5 LOW; I1 bis I4 INFO) und [Verifikation](../../../reviews/2026-10-30-1dad1ad-verifikation-slice-transport-tls-schleife.md) (kein HIGH; F1, F2 MEDIUM; F3 bis F10 LOW; 26 Mutationen), beide gegen den Merge `1dad1ad`. Eingearbeitet im Commit `43d3958` (Merge `08e777f`): M1 bis M3, L1 bis L5, F1, F3 bis F5 (N17 als gleichwertig eingestuft), F7, F8. F2 (N02 bis N04) und F6 (N01) bleiben als im KDoc gekennzeichnete Zweige ohne Test; F9 und F10 sind Grenzen, keine Mängel (siehe Zeile *nicht gemessen*). Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, außerhalb der Tests ruft bisher niemand die Schleife auf.
- **Mutationsstand, ehrlich:** Der Implementer fuhr vor dem Review 17 und nach den Befunden 9 Mutationen. Der Verifier fuhr 26 eigene: 14 rot, 12 grün, davon 2 gleichwertig oder nicht beobachtbar (N10, N19) und 10 Lücken. Nach dem Bericht des Implementers sind sieben dieser zehn seither gedeckt; die Nachprüfung durch den Verifier steht aus, und die Mutationsliste des Implementers liegt nicht im Repo. Die Stabilitätsaussage (7 von 7 Läufen grün unter Last, 27 Tests in rund 8 s, `make gates` Exit 0 in 1 m 52 s) gilt für den Stand `1dad1ad`; die neun danach hinzugekommenen Tests sind nur im Gate-Lauf dieser Closure gelaufen, nicht in einer Wiederholungsreihe.

**Zuordnung Test | Festlegung je Aussage.** Fünf Stärken: *gemessen* heißt gegen eine **JDK-`SSLEngine` als Gegenstelle im selben Prozess** beobachtet (nicht am Referenzclient, nicht an einem SQL Server); *Handvektor* heißt ein von Hand gebautes Byte-Muster; *Selbstkonsistenz* heißt Code gegen Code ohne unabhängige Gegenstelle; *Festlegung ohne Messung* heißt Setzung ohne Messung; *nicht gemessen* nennt, was keine Aussage trägt.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (JDK-Gegenstelle) | Nachrichten laufen in beiden Richtungen, byteweise und zufällig zerstückelt (fester Startwert), durch Schleife und Nachrichtenleser und -schreiber des Gerüsts, in TLS 1.2 und TLS 1.3 je mit den Suiten aus SPEC-023, im TDS-7.4-Pfad (`0x12`) und im TDS-8.0-Pfad | `TlsLoopbackTest` (`@spec SPEC-023`/`SPEC-028`), dazu zwei Läufe über echte Sockets; Mutationen R01 bis R06, N05 bis N07, N09, N14, N20 der Verifikation rot |
| gemessen (JDK-Gegenstelle) | Bytes hinter dem letzten Handshake-Paket gehen beim Umschalten nicht verloren; ein Rest, der nicht in den Lesepuffer passt, ist eine Protokollverletzung | `TlsLoopbackTest` (`bytes behind the last handshake packet reach the data phase`), `TlsNegativeTest` (`rest of a last handshake packet too large …`); R01, R04 rot. Ein kleiner Rest hinter `FINISHED` scheitert erst in der Datenphase mit `RECORD_INVALID` (fail-closed), nicht am Umschaltpunkt (Review I3) |
| gemessen (JDK-Gegenstelle) | Ohne `-Djdk.tls.server.newSessionTicketCount=0` scheitert der TDS-7.4-Pfad in TLS 1.3, mit dem Flag gelingt er | `TlsTicketFlagTest` (Kind-JVM mit und ohne Flag; Test der Test-JVM); Gegenprobe der Verifikation: Flag aus dem Init-Skript entfernt oder auf `1` gesetzt, acht Tests rot |
| gemessen (JDK-Gegenstelle) | Eine Gegenstelle mit TLS 1.1, ohne gemeinsame Version oder ohne gemeinsame Suite wird abgewiesen; Mindestversion in `TlsParameters` | `TlsNegativeTest` (`TLS 1_1 hello is rejected …`, `peer without a common version …`, `peer without a common suite …`, `parameters below TLS 1_2 are refused`); die Mutationen N11 und N12 waren vor den Befunden grün, ihre erneute Prüfung durch den Verifier steht aus |
| gemessen (JDK-Gegenstelle) | Abgelaufenes und auf den falschen Namen ausgestelltes Zertifikat, Handshake-Bytes in einem Paket falschen Typs, abgeschnittener Handshake, Klartext statt TLS im 8.0-Pfad und abrupt geschlossener Kanal enden in einem Ergebniswert; nach gescheitertem Handshake verlässt kein Anwendungs-Byte im Klartext den Kanal | `TlsNegativeTest`, `TlsLoopbackTest` (`payload never appears in plaintext on the wire`); R05, R06, N13, N20 rot. Die Fehlerursache (Ablauf gegen Kettenaufbau, Name) wird nur als `SSLException` beobachtet (Review L4) |
| gemessen (JDK-Gegenstelle) | ALPN `tds/8.0` wird angeboten und nach dem Handshake geprüft | `TlsLoopbackTest`, `TlsNegativeTest`; R02 und N09 rot; N10 ist gleichwertig (der Server bietet nur `tds/8.0`) |
| gemessen (JDK-Gegenstelle) | Der Puffer für entschlüsselte Daten bleibt begrenzt | `TlsLoopbackTest` (`decrypted buffer stays bounded while a mebibyte passes`); R03 rot |
| gemessen (JDK-Gegenstelle) | Die Leseseite wartet nicht auf einen blockierten Schreiber | `TlsDataPhaseTest` (`a blocked writer does not hold up the reader`); Grenzen siehe oben |
| gemessen (JDK-Gegenstelle), eingeschränkt | Ein KeyUpdate der Gegenstelle in TLS 1.3 löst genau einen kurzen Record des Servers aus, die Nachrichten laufen weiter | `TlsLoopbackTest` (`key update requested by the peer …`); der Inhalt der Antwort ist nicht beobachtet |
| gemessen (JDK-Gegenstelle) | `close_notify` der Gegenstelle beendet den Strom sauber (-1), `closeGracefully` danach antwortet mit eigenem `close_notify`; Abbruch ohne `close_notify` ist ein Fehler | `TlsLoopbackTest`, `TlsDataPhaseTest`, `TlsNegativeTest`; R05 rot. Eine Antwort schon beim Lesen, ohne Schließen, ist weder behauptet noch gemessen (N17) |
| Handvektor | Ein TLS-1.1-ClientHello als Byte-Muster | `TlsNegativeTest` (von Hand gebaut); keine Quelle außerhalb des Tests |
| Selbstkonsistenz | Schließen des inneren Kanals nach Abbruch, nach unerwarteter Ausnahme und nach `closeGracefully`; Schreiben nach dem Schließen ist ein Fehler | `TlsDataPhaseTest`, `TlsNegativeTest` (`writing to a closed peer …`) |
| Festlegung ohne Messung | Eine Gegenstelle ohne ALPN `tds/8.0` wird im TDS-8.0-Pfad abgewiesen | KDoc, `contracts/security-profile.md` §Festlegungen der TLS-Schleife; was der Referenzclient bei Abweisung täte, ist nicht gemessen |
| Festlegung ohne Messung | Im TDS-7.4-Pfad ist für Handshake-Bytes der Pakettyp `0x12` Pflicht; Fehlerkategorien je `TlsFailure`; Packet Size 4096; `close_notify`-Pflicht nach dem Handshake; unbegrenzte Sendefolge im 7.4-Handshake (Review I2) | KDoc, Sicherheitsprofil; die Tests belegen das Verhalten am Server, die Wahl folgt keiner Zeile der Spezifikation |
| Festlegung ohne Messung | Der Antwortpfad der Leseseite: `tryLock`, Erledigung durch den Schreiber | KDoc von `TlsWriter`/`TlsReader`; Test nur für den blockierten Schreiber, der Wettlauf Vormerken/Loslassen ist nicht belegt |
| nicht gemessen | Der Referenzclient (`Microsoft.Data.SqlClient`) gegen diese Schleife; TLS 1.2 und andere Suiten als `TLS_AES_256_GCM_SHA384` am Referenzclient; TDS 8.0 am Referenzclient; ein echter SQL Server; Neuverhandlung in TLS 1.2; ältere und neuere JDKs als das der Stage; andere Plattformen; Läufe unter anderer Last als die sieben Wiederholungen; die Zweige N01 bis N04 | Träger unten; Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/aussagen-ueber-messung-hinaus` |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft; jeder Träger existiert als Datei im Planning-Lifecycle und führt den Punkt in seiner DoD oder seinem Risiko):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Neuverhandlung in TLS 1.2 (nicht behandelt, nicht getestet) | `slice-qa-sicherheit` (`open/`) | §6, seit dieser Closure (vorher ohne Träger) |
| Zeitbegrenzung des Handshakes (`LOGIN_TIMEOUT` ab Verbindungsaufbau, keine Frist in der Schleife) | `slice-transport-listener-lebenszyklus` (`open/`) | §6, seit dieser Closure; die DoD nennt die Frist ab Verbindungsaufbau, den Handshake nicht ausdrücklich |
| Antwort auf `close_notify` der Gegenstelle beim geordneten Schließen (Nutzerentscheidung; `jdk.tls.acknowledgeCloseNotify`) | `slice-transport-listener-lebenszyklus` (`open/`) | §6, seit dieser Closure; „Nutzerentscheidung“ allein ist kein Träger |
| JVM-Flag `-Djdk.tls.server.newSessionTicketCount=0` im Serverprozess setzen und prüfen | `slice-bootstrap-konfiguration` (`open/`) | DoD *Composition Root, `main`, Start und Stop*, ergänzt in dieser Closure (vorher nur im Risiko der Überschneidung geführt, nicht in der DoD) |
| TLS 1.2, andere Suiten und TDS 8.0 am Referenzclient | `slice-qa-kompatibilitaet` (`open/`) | §6, seit dieser Closure; die DoD nennt TDS 8.0 schon |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Drei Feststellungen. (1) **Ein Test kann von der Voreinstellung der Laufzeit getragen werden, statt von dem Code, den er belegen soll.** `TLS 1_1 hello is rejected in both paths` blieb grün, als die Zeile mit den Protokollen der Konfiguration entfiel, weil das JDK TLS 1.1 ohnehin sperrt; die Positivtests gaben Server und Gegenstelle dieselbe Konfiguration. Gefunden hat es die Mutation der Verifikation (N11, N12), nicht der Review. Die Antwort an diesem Slice sind Negativtests ohne gemeinsame Version oder Suite mit Gegenprobe; als Prüffrage für den Reviewer („Bleibt der Test grün, wenn die Zeile entfällt, die die Konfiguration setzt?“) und als Mutation des Verifiers ist sie noch keine Regel. Register: `BEO-TDS/konfiguration-vom-jdk-default-getragen` (neu, 1×); nah verwandt, aber anderer Mechanismus: `BEO-TDS/strukturtest-ohne-variation-der-eingabe`. (2) **Das Muster der Aussage über den Beleg hinaus trat trotz der verkörperten Regel wieder auf:** „beantwortet ein KeyUpdate“ trug nur „die Nachrichten laufen weiter“ (Review M2, Verifikation F5). Ein weiteres Auftreten von `BEO-TDS/aussagen-ueber-messung-hinaus`, keine Schärfung; `AGENTS.md` §3.8 war der Maßstab, und der Review fand die Aussage, bevor sie über den Vertrag hinaus Folgen hatte. (3) **Der Eingang der Stage `deps` klein zu halten, bestimmt, wo eine Test-Einstellung wohnt.** Die Einstellungen der Test-JVM (Flag, Pfade der Testschlüssel) stehen im Init-Skript `gradle/test-jvm.init.gradle.kts` statt in `build.gradle.kts`, damit der Cache der Stage `deps` nicht neu läuft (Verifikation §5: `deps` blieb `CACHED`). Der Preis: ein `gradle test` ohne `-I` setzt sie nicht, und der Test der Test-JVM schlägt dann fail-closed fehl; `make test` tut es. Das ist ein Muster, keine Regel und kein Fehlerbefund; es steht hier, damit der nächste Slice mit einer Build-Einstellung die Wahl kennt. **Benannte Spec-Lücke:** `SPEC-028` und `SPEC-044` sagen nicht, ob der Server auf ein `close_notify` der Gegenstelle mit einem eigenen antwortet, und nichts zur Neuverhandlung in TLS 1.2; das Sicherheitsprofil führt beides als Festlegung bzw. als nicht getestet. Die Lücke ist benannt und hat Träger (`slice-transport-listener-lebenszyklus`, `slice-qa-sicherheit`), aber noch keine Zeile in einer versionierten Spec.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-transport-tls-schleife.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (Review M2, Verifikation F5), in `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (TDS 8.0 und TLS 1.2 nur gegen die JDK-Gegenstelle) und in `BEO-TDS/offene-punkte-ohne-traeger` (die Träger-Prüfung fand Neuverhandlung und Antwort auf `close_notify` ohne Träger); eine neue Beobachtung mit dieser Evidence-Datei: `BEO-TDS/konfiguration-vom-jdk-default-getragen` (Review M3, Verifikation F1). Kein neues Auftreten: `BEO-TDS/messung-am-echten-sql-server-fehlt` (der Slice sagt nichts darüber, was ein echter SQL Server sendet; die Lücke am Referenzclient trägt `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`), `BEO-TDS/test-belegt-nur-den-fake` (die Gegenstelle ist eine unabhängig geschriebene Engine, nicht der Fake des geprüften Objekts; der Mechanismus „das JDK trägt den Test“ ist ein anderer und hat eine eigene Beobachtung), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (`make docs-check` ohne Befund) und die übrigen. Die Zähler folgen aus den Dateien; `aussagen-ueber-messung-hinaus` und `offene-punkte-ohne-traeger` stehen schon auf `verkörpert`, ihr `state.md` bleibt unverändert.
- **Risiken aus §6:** alle sieben tragen genau einen Ausgang (siehe §6): vier entfallen, drei weiter offen mit Träger.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: die Träger in der Tabelle oben existieren als Dateien in `open/` und führen die Punkte in ihrem Inhalt. Register: vier Evidence-Dateien dieses Vorgangs, siehe oben.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `transport` ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 6×, Stand *geplant*, Träger `welle-entscheidungen-grundlage`. Betrifft diesen Slice als Implementierungs-Slice: jede Aussage trägt einen Test oder ist als Festlegung gekennzeichnet (§2, §6). Kein weiterer Folge-Slice; die Closure-Notiz hält fest, ob das Muster wieder auftrat.
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 2×, offen. Betrifft den TDS-8.0-Pfad: dort gilt MS-TDS- und ADR-Lesart, nicht Messung am Server dieses Repos.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 2×, offen. Betrifft jede Aussage über das Verhalten gegen einen echten SQL Server oder den Referenzclient: der Slice macht keine (§1, §6).
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 1×, offen. Betrifft den TLS-Modus nur am Rand ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)); Zugangsdaten kommen in diesem Slice nicht vor, der TLS-Modus-Nachweis bleibt bei `slice-qa-sicherheit`.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Betrifft dieses Dokument und die Closure-Notiz: `ADR-*` und `TDS-*` stehen als Link.
- Ohne Treffer: `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`; `adapter-festlegungen-ohne-slice` berührt die Schleife nicht.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
