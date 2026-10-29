# Slice slice-adr-tls: ADR-0002 entscheiden: TLS-Strategie und Verschlüsselungsmodi

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-entscheidungen-grundlage

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md)

**Berührte Spec-Stellen:** SPEC-043 · SPEC-028 · SPEC-010 · SPEC-023 · ARC-002 · ARC-013

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

**Ziel:** [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) ist mit einem Nachweis entschieden und `Accepted`; die offene Frage lautet: Erlaubte Testmodi und Zertifikatsvorgaben.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode der gewählten Option — er entsteht in einem Slice der funktionalen Welle, weil dieser Slice entscheidet und nicht baut.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Spike durchgeführt; seine Ergebnisse (Messwerte, Matrix, Befund) stehen in der ADR. Der Spike-Code ist temporär und nach der Entscheidung gelöscht.
- [x] [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) auf `Accepted`, Option gewählt, Geschichte und ADR-Index nachgezogen.
- [x] Spec-Stellen aus dem `Schärft:`-Feld der ADR an die Entscheidung angepasst.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `docs/plan/adr/0002-tls-und-verschluesselung.md` | update | Entscheidung, Status, Geschichte |
| `docs/plan/adr/README.md` | update | Indexzeile |
| Spike (temporär, nicht im Repo behalten) | neu, danach löschen | Ergebnisse gehen in die ADR |
| `spec/spezifikation.md` | update | Stellen aus `Schärft:` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Zwischenstand der Messung** (Spike per Dockerfile ohne Mount; `Microsoft.Data.SqlClient` 7.1.1, .NET SDK 10.0.403; Testserver im Client-Container; Zertifikat `CN=localhost`, SAN `localhost`, selbstsigniert, für die „vertraut“-Fälle im Trust-Store des Containers):

| Fall | Beobachtung |
|---|---|
| TDS 7.4, `Encrypt=true`, Server antwortet ENCRYPT_ON | TLS-Handshake in TDS-Paketen vom Typ `0x12` gelingt (TLS 1.3, `TLS_AES_256_GCM_SHA384`); LOGIN7 (Typ `0x10`, `TDSVersion=0x74000004`) kommt danach verschlüsselt im TLS-Tunnel |
| TDS 7.4, Zertifikat nicht vertraut, `TrustServerCertificate=false` | Der Client bricht nach dem Handshake ab: „Certificate failed chain validation … UntrustedRoot“ |
| TDS 7.4, vertrautes Zertifikat, `HostNameInCertificate=falsch.example` | Der Client beanstandet den Namen nicht, LOGIN7 wird erreicht; warum, ist nicht geklärt |
| TDS 7.4, Server antwortet ENCRYPT_NOT_SUP auf `Encrypt=true` | Der Client bricht ab („does not support encryption“), kein LOGIN7 |
| TDS 7.4, Server antwortet ENCRYPT_OFF, Client `Encrypt=true` oder `Encrypt=false` | Der Client beginnt trotzdem den TLS-Handshake (ClientHello in Paketen vom Typ `0x12`): das Login ist auch bei ENCRYPT_OFF verschlüsselt |
| TDS 7.4, Server antwortet ENCRYPT_ON auf `Encrypt=false` | Der Handshake scheitert serverseitig („frame size“), Ursache nicht geklärt, möglicherweise der Testserver |
| TDS 8.0, `Encrypt=Strict`, vertraut, richtiger Name | TLS 1.3, ALPN `tds/8.0`, PRELOGIN, LOGIN7 `TDSVersion=0x08000000` |
| TDS 8.0, `Encrypt=Strict`, falscher Name | Der Server-Handshake gelingt, der Client bricht danach ab |
| TDS 8.0, `Encrypt=Strict`, selbstsigniert nicht vertraut | Abbruch mit `UntrustedRoot`; `TrustServerCertificate` gilt nicht |
| TDS 8.0, `Encrypt=Strict`, vertraut | TLS 1.3 mit `TLS_AES_256_GCM_SHA384` |
| TDS 7.4, Server antwortet ENCRYPT_REQ, Client `Encrypt=true` oder `Encrypt=false` | Der Handshake in TDS-Paketen gelingt (TLS 1.3, `TLS_AES_256_GCM_SHA384`), LOGIN7 kommt verschlüsselt (`TDSVersion=0x74000004`) |
| TDS 7.4, `Encrypt=true;TrustServerCertificate=true`, Zertifikat nicht vertraut | Der Handshake gelingt, LOGIN7 wird erreicht |
| TDS 8.0, `Encrypt=Strict;TrustServerCertificate=true`, Zertifikat nicht vertraut | Abbruch mit `UntrustedRoot` |
| TDS 7.4, `Encrypt=false`, Server antwortet ENCRYPT_NOT_SUP (Messung aus `slice-adr-clientprofil`) | Der Client sendet LOGIN7 unverschlüsselt (`TDSVersion=0x74000004`); mit `Encrypt=true` bricht er ab |


## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-adr-clientprofil` liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der Referenzclient lässt sich nicht gegen die Testmodi verbinden.

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

- Der Referenzclient lässt sich nicht gegen die Testmodi verbinden. — **Ausgang:** entfallen: Er verbindet sich in allen drei Modi (TLS in TDS 7.4, `Encrypt=Strict`, unverschlüsselter Testmodus mit `Encrypt=false`), siehe die Messtabelle in §3.

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

- **Was hat funktioniert:** Der Testserver im Client-Container (Dockerfile, ohne Mount) deckte beide Protokollpfade ab; mit dem eingebauten TDS-Tunnel ließ sich der 7.4-Handshake bis LOGIN7 messen.
- **Was ging anders als geplant:** Review und Verifikation fanden Aussagen in ADR und Sicherheitsprofil ohne Messzeile. Zwei davon waren gemessen und fehlten nur in der Tabelle, zwei wurden nachgemessen (ENCRYPT_REQ, `TrustServerCertificate=true` mit nicht vertrautem Zertifikat). SPEC-028 und der Folgesatz zu den Vertragsdateien in der Spezifikation wurden nachgezogen. Die Messung liegt als Tabelle im Slice, in der ADR als Aufzählung.
- **Steering-Loop-Eintrag:** Aussagen über die Messung hinaus, gezählt 2×, noch nicht verkörpert; Auslöser: `BEO-TDS/aussagen-ueber-messung-hinaus` (slice-adr-clientprofil, slice-adr-tls — 2×).
- **Beobachtungs-Register (`../observations/`):** `BEO-TDS/aussagen-ueber-messung-hinaus/` neu angelegt, Beleg `evidence/slice-adr-tls.md` und `evidence/slice-adr-clientprofil.md`; Zähler steht damit bei 2x. `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten/` ist nicht wieder aufgetreten (Zähler bleibt bei 1x).
- **Folge-Slices:** `slice-adr-transport` und die weiteren Entscheidungs-Slices der Welle — liegen als Dateien in `open/`.
- **Risiken aus §6:** Das eine Risiko ist entfallen, siehe §6.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von `welle-entscheidungen-grundlage`.

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
