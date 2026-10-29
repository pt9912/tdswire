# Slice slice-adr-clientprofil: ADR-0001 entscheiden: TDS-Version und .NET-Kompatibilitätsprofil

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-entscheidungen-grundlage

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-02](../../../../spec/lastenheft.md#tds-rb-02--externer-client), [TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)

**Berührte Spec-Stellen:** SPEC-020 · SPEC-021 · SPEC-004 · ARC-010 · ARC-011

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) ist mit einem Nachweis entschieden und `Accepted`; die offene Frage lautet: TDS-Variante, Clientpaket und Version.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode der gewählten Option — er entsteht in einem Slice der funktionalen Welle, weil dieser Slice entscheidet und nicht baut.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Spike durchgeführt; seine Ergebnisse (Messwerte, Matrix, Befund) stehen in der ADR. Der Spike-Code ist temporär und nach der Entscheidung gelöscht.
- [ ] [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) auf `Accepted`, Option gewählt, Geschichte und ADR-Index nachgezogen.
- [ ] Spec-Stellen aus dem `Schärft:`-Feld der ADR an die Entscheidung angepasst.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `docs/plan/adr/0001-tds-version-und-clientprofil.md` | update | Entscheidung, Status, Geschichte |
| `docs/plan/adr/README.md` | update | Indexzeile |
| Spike (temporär, nicht im Repo behalten) | neu, danach löschen | Ergebnisse gehen in die ADR |
| `spec/spezifikation.md` | update | Stellen aus `Schärft:` |

**Zwischenstand der Messung** (Spike per Dockerfile ohne Mount; .NET SDK 10.0.403 und 8.0, Listener im Spike beantwortet PRELOGIN mit ENCRYPT_NOT_SUP und liest LOGIN7):

- `Microsoft.Data.SqlClient` 7.1.1 und `System.Data.SqlClient` 4.9.1 senden in LOGIN7 dieselbe TDS-Version `0x74000004` (TDS 7.4) und Paketgröße 8000, bei `Encrypt=false` wie bei `Encrypt=true`; das Ergebnis ist unter .NET 8 und .NET 10 gleich.
- PRELOGIN: Option 0 (VERSION) `07010001A066` (MDS) bzw. `0484007E5F2B` (SDS), Option 1 (ENCRYPTION) `00` bei `Encrypt=false`, `01` bei `Encrypt=true`, Option 6 (FEDAUTHREQUIRED) `01`. Bei `Encrypt=true` bricht der Client nach `ENCRYPT_NOT_SUP` ab, es folgt kein LOGIN7.
- `Encrypt=Strict` (TDS 8.0): Der erste Byte-Block ist ein TLS-ClientHello (`1603010…`), kein PRELOGIN.
- AppName/CltIntName: `Core Microsoft SqlClient Data Provider` (MDS) bzw. `Core .Net SqlClient Data Provider` (SDS).
- TLS-Abläufe (`Microsoft.Data.SqlClient` 7.1.1, .NET 10, Testserver mit selbstsigniertem Zertifikat im Trust-Store des Containers):
  - TDS 7.4 mit TLS (`Encrypt=true`, oder `Encrypt=false` gegen einen Server, der ENCRYPT_REQ antwortet): Der TLS-Handshake läuft eingepackt in TDS-Pakete vom Typ `0x12` (PRELOGIN); die Nutzlast beginnt mit dem TLS-Record `160301` (ClientHello, 290 Byte).
  - TDS 8.0 (`Encrypt=Strict`): TLS 1.3 ab dem ersten Byte ohne TDS-Hülle, ALPN `tds/8.0`; danach PRELOGIN (ENCRYPTION-Option `02`), danach LOGIN7 mit `TDSVersion=0x08000000`.
  - `Encrypt=Strict` ignoriert `TrustServerCertificate`: Ein selbstsigniertes Zertifikat scheitert mit „Certificate failed chain validation … UntrustedRoot“; der Server braucht ein Zertifikat, dem der Client vertraut, und `HostNameInCertificate` muss passen.
- Offen: Die Wahl zwischen TDS 7.4 und TDS 8.0 im Profil hängt an der TLS-Strategie ([ADR-0002](../../adr/0002-tls-und-verschluesselung.md)); die Messung oben nimmt diesen Slice mit, `slice-adr-tls` entscheidet darauf.

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `make gates` ist auf `main` grün.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Clientpaket und Version lassen sich nicht reproduzierbar festlegen.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Clientpaket und Version lassen sich nicht reproduzierbar festlegen. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt Spec und ADR, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
