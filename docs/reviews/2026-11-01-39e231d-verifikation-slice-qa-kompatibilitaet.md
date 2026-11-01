# Verifikationsbericht: slice-qa-kompatibilitaet (Commit 39e231d)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Branch `worktree-agent-aeffb90db7bc4a9ee`, HEAD `39e231d` (Liefer-Punkte 1 bis 3, Review [b257f85](2026-10-31-b257f85-slice-qa-kompatibilitaet.md) und Fix-Runde durch, nicht gemergt).
* Prüfgrundlage: Plan und DoD `docs/plan/planning/in-progress/slice-qa-kompatibilitaet.md` im Branch, [ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) (Accepted, Zuschnitt A), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), [Kompatibilitätsmatrix](../plan/kompatibilitaetsmatrix.md), Review-Report. Die Zusagen des Implementers sind Eingabe, nicht Beleg.
* Herkunft: Der Verifier-Agent durfte die Datei nicht anlegen; der Inhalt ist sein Bericht, vom Aufrufer verdichtet abgelegt.
* Selbst gefahren (Docker-only, ein Build gleichzeitig, unter Last durch drei weitere Agenten; Laufzeiten daher nicht mit den Zahlen des Implementers vergleichbar): `make gates` im Worktree Exit 0, 7 min 43 s (darin `make skript-selbsttest` mit dem Abbruchfall für Stufe 6 und `make kopplung-zahlen`); `make kompatibilitaet KATEGORIE=Szenario` 13 von 13 bestanden, 112 s (Bau 89 s, Tests 23 s); `KATEGORIE=Anmeldung` 17 von 17, 24 s (Bau 4 s, Tests 20 s); `KATEGORIE=Luecken` 5 von 5, 29 s (Bau 5 s, Tests 24 s). Ein vollständiges `make e2e` war nicht nötig und ist nicht gefahren; die Abbruchlogik zwischen den Stufen belegt nur der Selbsttest im Gate. Danach standen keine Container, Netzwerke oder Images des Laufs mehr.
* Gegen die Matrix: Alle `messung:`-Zeilen der Läufe stimmen mit Abschnitt 7 überein (Fehlernummern, Klassen, Verbindungszustände, PacketSize 8000 und 512, Typkatalog).
* Mutation (Stichprobe): in `protocol/rpc/RpcParameterReader.kt` Zeile 108 den Zuschlag `OFFSET_BYTES` bei `DATETIMEOFFSETN` entfernt, `make kompatibilitaet KATEGORIE=Anmeldung`: 16 von 17 bestanden, rot `Parametertypen_ausserhalb_des_Katalogs…(DateTimeOffset)`, im Log `TDS_PROTOCOL_ERROR`, Exit 2; zurückgesetzt, `git status` und `git diff` leer.

## Gesamturteil

**Closure-fähig: nein, noch nicht, aber ohne HIGH und ohne Befund gegen den Code.** Die Liefer-Punkte 1 bis 3 sind technisch erfüllt oder ehrlich als teilweise gemessen ausgewiesen; das Gate ist grün, die 35 Tests (13, 17, 5) laufen und sind per Mutation rot zu bekommen; nichts Nicht-Gemessenes wird als gemessen behauptet. Es fehlt, was nur der Planner oder die Closure schließt: Entscheidung zu den DoD-Abweichungen V-1 bis V-3, der in der DoD verlangte Eintrag zu [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) in §6 (V-1), die Closure-Pflichten (Closure-Notiz mit Lerneintrag, Register, Ausgang je Risiko aus §6; §7 steht auf „Wird bei der Closure gefüllt“) und der Nachzug des Slice-Textes (§4/§6) auf [ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) `Accepted`. Bedingungen für „ja“: V-1 behoben, V-2 und V-3 vom Planner entschieden, Closure-Pflichten erledigt.

## DoD-Punkte

* **1, Szenarien nach SPEC-025/032 grün; Stufe im Produkt-E2E mit Kennungen; Lücke FA-05/FA-10 in §6 mit Ausgang: teilweise.** Bestätigt: Stufe 5 mit 13 Tests grün (Anmeldung, Ablehnung, Batch-SELECT, Stern, NULL, acht Typen, parametrisierte Abfrage, Einfügen/Ändern/Löschen, Fehler; `SzenarienTests.cs`, Matrix 7a); Stufe in `tools/e2e.sh` Zeile 50 bis 51 mit FA-01 bis FA-06, QA-01, RB-02 deklariert, läuft über die Prozessgrenze (zweiter Container). Nicht bestätigt: der Eintrag zu FA-10 in §6 und die Kennung FA-10 in Stufe 5 (V-1, V-4).
* **2, Anmeldemessungen und RPC-Messungen am Server gemessen und „in den ADRs bzw. im Clientprofil nachgetragen“ oder je Punkt als nicht lieferbar benannt; Codec-Aussage zu TDS 8.0 und Pooling von Festlegung auf gemessen gehoben oder eingeschränkt; E2E-Stufe für TDS 8.0 mit [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit): teilweise.** Einzelergebnisse unten; abweichend sind der Nachtragsort (V-2) und das mehrpaketige LOGIN7 (V-3). Stufe 6 trägt [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit) (`e2e.sh` Zeile 54 bis 55).
* **3, Lücken 3, 4, 14: bestätigt, mit benannter Einschränkung.** Stufe 7 mit 5 Tests grün: xml gibt 50003 und schließt; mehrere RPCs geben 50006 und schließen (`UpdateBatchSize 1` gelingt); Kennwortwechsel gibt 18456 mit Log-Grund `UNSUPPORTED_PASSWORD_CHANGE`; AccessToken gibt 18456 mit Log-Grund `UNSUPPORTED_FEDERATED_AUTHENTICATION`. Das Batch-Flag ist nur aus der Wirkung geschlossen, die FeatureExt `0x02` am Draht nicht gesehen; beides steht so in Matrix, `e2e.sh` und Testkopf.
* **4, `make gates` grün: bestätigt** (eigener Lauf Exit 0; `kompatibilitaet` nicht in `GATE_CHECKS`, [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
* **5, Review mit Report: bestätigt;** Fix-Runde nachgeprüft: M-1, L-1 bis L-4, I-3, I-4 behoben; L-5 nicht im Einzelnen nachgeprüft.
* **6, Doku-Update bei öffentlichem Vertrag: teilweise.** `harness/README.md`: die Zeilen `make e2e` und `make kompatibilitaet` stimmen mit `make help`; die Spec ist nicht nachgezogen (V-5).
* **7 bis 10, Closure-Notiz, Register (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` nicht fortgeschrieben), Ausgang je Risiko aus §6 (alle sechs „offen bis zur Closure“), drei Paarungen: offen bzw. vor der Closure nicht prüfbar.**

### Punkt 2 im Einzelnen

* TDS 8.0 (`Encrypt=Strict`) mit SQL-Authentifizierung, Batch und RPC: bestätigt (Stufe 6; Mutation „Pfadwahl am ersten Byte“ hat der Implementer rot gesehen, nicht wiederholt).
* Namensprüfung mit Gegenprobe: bestätigt (falscher Name scheitert mit −2146893019 in TDS 7.4 und 8.0, richtiger gelingt; kein `TrustServerCertificate`).
* Pooling: teilweise (zweite Nutzung gelingt auf derselben Sitzung, Log-Prüfung im Skript; das RESETCONNECTION-Bit ist nicht beobachtet, der Server wertet es nicht aus; steht so in Matrix und Testkopf).
* `sp_unprepare`: begründet nicht erreichbar (`Prepare` gibt 50002, es entsteht kein Handle).
* `SET`-Texte `SchemaOnly` und `KeyInfo`: bestätigt (Log nennt die Texte wörtlich, Prüfung im Skript); der Schlüssel als Metadatum ist damit nicht beantwortet, die Matrix sagt das.
* `BeginTransaction` (`0x0E`): bestätigt (Class 20, Verbindung danach geschlossen).
* RPC-Typen FLTN, MONEYN, DATETIMN, GUID, Zuschlag bei DATETIMEOFFSETN: bestätigt (8 Fälle gelesen und mit 50002 abgewiesen; die Stichproben-Mutation macht den DATETIMEOFFSETN-Fall rot).
* Paketgröße 512: als „Annahme durch den Client“ bestätigt; die Paketierung der Antworten ist nicht gemessen (ein Server mit fester Größe 8000 ließ den Test grün); steht in KDoc, Matrix und `e2e.sh`.
* Windows-SSPI (NTLM): nicht lieferbar, so benannt ([ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) und Matrix 14: Einschätzung, nicht gemessen). Mehrpaketiges LOGIN7: nicht gemessen (V-3).
* Codec-Aussage zu TDS 8.0: gehoben, nur das KDoc (`ConnectionWiring.kt`, `ConnectionState.kt`: „am Referenzclient belegt“, TLS-Version und Suite „nicht beobachtet“). Pooling: unverändert eingeschränkt (Doku zur Nicht-Auswertung des Bits liegt in `HandshakeEvent.kt`; die Closure-Notiz von `welle-protokollcodec` ist nicht angefasst).

## Findings nach Schwere

Kein HIGH.

* **V-1 (MEDIUM, DoD Punkt 1 und 3.8):** Die DoD verlangt für FA-05 und FA-10 „die Lücke in §6 mit Ausgang“; Matrix Lücke 16 verweist auf „`slice-qa-kompatibilitaet` §6 (Ausgang: weiter offen …)“, §6 des Slice enthält aber keinen Eintrag dazu (`grep` auf „FA-10“ trifft dort nur den DoD-Text). Der Rang-Zeiger der Matrix läuft ins Leere; der Träger der Backend-Seite ist nicht benannt. Planner-Sache.
* **V-2 (MEDIUM, DoD Punkt 2, Planner):** Die Messungen sind in Matrix Abschnitt 7, `e2e-abdeckung.md` und KDoc nachgetragen, nicht „in den ADRs bzw. im Clientprofil“. Mit `AGENTS.md` §3.5 vereinbar (kein Diff an `docs/plan/adr/`). `spec/spezifikation.md` ist unberührt; SPEC-004 nennt aber Pooling, RESETCONNECTION, Transaktionen und SET-Text als „offene Freigabepunkte“ und die Messungen als Bedingung der RPC-Freigabe; SPEC-004 und [ADR-0001](../plan/adr/0001-tds-version-und-clientprofil.md) sind weder fortgeschrieben noch als Folgepflicht benannt. Optionen: (a) DoD-Wortlaut auf „in Matrix und KDoc nachgetragen“; (b) Folge-Schritt (neue ADR oder SPEC-004-Fortschreibung) als Folge-Slice benennen; (c) die Fortschreibung von SPEC-004 in diesen Slice ziehen.
* **V-3 (MEDIUM, DoD Punkt 2, Planner):** „Mehrpaketiges LOGIN7“ ist nicht gemessen; die DoD erlaubt „je Punkt mit Begründung als nicht lieferbar benannt“. Matrix, Testkopf und `e2e.sh` sagen „nicht beobachtet (kein Mitschnitt)“: ehrlich, aber weder als „nicht lieferbar“ markiert noch begründet, warum kein Mitschnitt möglich ist (ob Mitschnitt oder Paketzählung im Testserver machbar wäre, nicht geprüft). Dasselbe gilt für das RESETCONNECTION-Bit (der Server wertet es nicht aus, begründet). Optionen: (a) „nicht beobachtet“ genügt als Benennung; (b) beide Punkte ausdrücklich als „nicht lieferbar“ mit Grund eintragen; (c) einen Messweg verlangen (Paketzahl serverseitig im Testserver, Bit im Log), dann bleibt der Punkt offen.
* **V-4 (LOW, Planner):** Das Ziel des Slice nennt „Abbruch“ unter den Szenarien; weder DoD-Liste noch §1 „Ausdrücklich NICHT“ nennt Cancel. Er fehlt zu Recht ([CO-004](../plan/carveouts/CO-004-cancel-szenario-spec-040-fehlt.md); `e2e.sh` Stufe 5 nennt ihn unter „Nicht belegt“), der Slice sagt es aber nicht. Ebenso fehlt FA-10 in der Kennungenliste von Stufe 5, begründet durch Lücke 16.
* **V-5 (LOW, Spec-Nachzug):** SPEC-025 und SPEC-050 tragen noch „Lauf des E2E-Durchgangs, noch einzurichten“, obwohl die Stufen 5 bis 7 laufen. Die Folgepflichten (2) und (3) von [ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) stehen aus. Slice §4 und §6 nennen [ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) noch `Proposed`; die DoD-Anpassung „zwei Container, SSPI nicht lieferbar“ steht aus.
* **V-6 (LOW, 3.8):** `harness/README.md` nennt „7 Stufen 264 s (2026-10-31, ein Lauf)“ und „je 18 s warm“; Zahlen des Implementers. Die Läufe des Verifiers unter Last: 24 s (Anmeldung) und 29 s (Lücken) einschließlich Bau; kein Widerspruch, aber nicht reproduziert; 264 s nicht nachgemessen.
* **V-7 (INFO):** Die Messwerte der Matrix stammen aus einem Lauf je Zeile und wurden bestätigt; `make matrix-zitate` prüft Abschnitt 7 nicht; `tools/kompatibilitaetstest.sh` hat keinen Selbsttest.
* **V-8 (INFO):** Die Matrix nennt SSPI/NTLM „Einschätzung, nicht gemessen“ und führt die beiden Vorbehalte (FeatureExt `0x02`, Batch-Flag); gegen 3.8 kein Verstoß gefunden.

## Abgrenzung

Als nicht belegt benannt sind SSPI, die Paketzahl eines LOGIN7, die Paketgröße der Antworten, das RESETCONNECTION-Bit, TLS 1.2 und andere Suiten, `sp_unprepare`, die FeatureExt `0x02` am Draht, das Batch-Flag am Draht und ein echter SQL Server. Cancel steht mit Verweis auf [CO-004](../plan/carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) da; [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) (Lücke 16) ist als „nicht lieferbar mit dem Simulations-Backend“ ausgewiesen. Kein Fall gefunden, in dem eine nicht gemessene Aussage als gemessen steht.

## Risiken aus §6 (Vorschläge, Ausgänge setzt die Closure)

1. [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Form C: Texte gemessen; Schlüssel als Metadatum und Identitätsmerkmal bei `KeyInfo` nicht gemessen: teils eingetreten, teils weiter offen.
2. Pflicht-Matrix B: gemessen, Stufen 5 bis 7; Umfang benennbar.
3. TLS-Schleife: TDS 7.4 und 8.0 mit `TLS_AES_256_GCM_SHA384` am Referenzclient belegt; TLS 1.2 und andere Suiten nicht: weiter offen.
4. Listener-Setzungen: (c) für dieses Zertifikat bestätigt; (a) `close_notify` und (b) RSA 2048 Bit ohne Messung: weiter offen.
5. TDS-8.0-Verdrahtung: gemessen.
6. Start-Trigger: [ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) ist `Accepted`, die DoD-Anpassung steht aus (V-5).

## Hard Rules

3.1 erfüllt (alle Läufe über `make`); 3.2 kein `@Suppress`, detekt grün; 3.5 keine Accepted-ADR im Diff geändert; 3.6 kein Gate gelockert, `make kompatibilitaet` nicht in `GATE_CHECKS`; 3.7 und 3.8: Kommentare und Aussagen in Skripten, `e2e.sh`, `KompatibilitaetsServer.kt` und Testköpfen sind Zusage, Kopplung, Abgrenzung oder Grenze im Indikativ; Befund zu 3.8 ist V-1; die Zahlen der README in V-6 sind nicht reproduziert.
