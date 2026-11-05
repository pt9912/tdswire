# Review-Report: slice-prelogin-serverversion (ea528ad..5b1ad85) — 2026-11-05

**Review-Art:** Code (gegen Plan, ADR, Hard Rules; nicht gegen die DoD)

**Gegenstand:** Commits fbd2a44 und 5b1ad85 auf Basis ea528ad (`PreloginNegotiation.kt`, `PreloginNegotiationTest.kt`, `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/prelogin-serverversion-2026-11-05.md`, Kopfzeile des Slice-Plans)

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-05

**Eingangs-Kontext:** Plan `slice-prelogin-serverversion` (§1, §2, §3, §6); [ADR-0001](../plan/adr/0001-tds-version-und-clientprofil.md); [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität); `AGENTS.md` §3.7, §3.8, §5 Regel 7. Der Implementer-Bericht wurde nicht übernommen. Die Mutation des Tests habe ich am Testcode durchgespielt, nicht gefahren; Gates habe ich nicht gefahren.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Spec schreibt „Gemessen am 2026-11-05 mit mssql-jdbc 13.4.0 … lehnt der Treiber die Antwort mit VERSION `0.0.0.0` ab“. Im Repo steht für die Ablehnung kein Messprotokoll; das neue Protokoll belegt nur die Annahme. Die Sonde nennt die Ablehnung „laut Auftrag, nicht neu gemessen“ (Zeile „Anmeldung, Pool“), der Slice-Plan §1 nennt eine Messung auf frischem Klon, und das Register trägt den Fehlertext. Wer die Spec liest, kann die zitierte Messung nicht finden; die Quellen widersprechen sich über ihre Herkunft. | `AGENTS.md` §3.8; Reviewer-Skill (Aussage breiter als ihr Beleg) | `spec/spezifikation.md` · `Gemessen am 2026-11-05 mit mssql-jdbc 13.4.0 (in d-migrate 1.12.0) lehnt der Treiber die Antwort mit VERSION` | nein (Messung außerhalb der Gates) | Gemessen-Behauptung ohne auffindbares Protokoll |
| F-2 | LOW | Das Messprotokoll nennt Datum, Client, Digest, Befehl und Ergebnis, aber nicht die Last, den Stand des Testservers (Commit oder Image) und nur „Linux, Docker“ als Host. Die Lesart „PRELOGIN und LOGIN7 sind angenommen … die Fehlermeldung kommt vom Server“ ist aus Exit 4 und `Failed to initialize pool: Unsupported SQL.` geschlossen; ein Server-Log oder Rohbyte-Beleg steht nicht dabei. Die Folgerung ist plausibel (Sonde Zeile 25 führt Hürden 2 und 3 mit Aufzeichnung), steht aber im Protokoll selbst ohne Beleg. | `AGENTS.md` §5 Regel 7 (Datum, Host, Bedingung) | `docs/messungen/prelogin-serverversion-2026-11-05.md` · `Lesart: Die Meldung` und `Linux, Docker` | nein | Messprotokoll ohne Last und Stand |
| F-3 | LOW | Die Spec-Setzung sagt „Hauptversion 16 wie SQL Server 2022“ und nennt „mit `16.0.1000.6` ging dort die Anmeldung“. Die erste Aussage ist eine Außenaussage ohne Quelle im Repo; die zweite stützt sich nur auf das Protokoll aus F-2. Beide sind als Setzung gekennzeichnet und nicht als Messung am echten Server, tragen also nicht weiter als gesagt. | `AGENTS.md` §3.8 | `spec/spezifikation.md` · `Hauptversion 16 wie SQL Server 2022` | nein | Außenfakt ohne Quelle |
| F-4 | LOW | Der Kommentar an den Konstanten nennt neben der Zusage („Setzung, nicht gemessen“) die verworfene Alternative („mssql-jdbc lehnt 0.0.0.0 ab“). Als Kopplung (Wert hängt am Treiber) und Rang-Zeiger (`spec/spezifikation.md`) lesbar, beschreibt aber zugleich den abwesenden Wert. Die Zeile ist zudem länger als die Nachbarzeilen. | `AGENTS.md` §3.7 | `PreloginNegotiation.kt` · `Setzung (nicht gemessen): 16.0.1000.6; mssql-jdbc lehnt 0.0.0.0 ab` | nein | Kommentar nennt verworfenen Wert |
| F-5 | INFO | Byte-Kodierung geprüft: `PreloginOption.version` schreibt Major, Minor, Build als zwei Bytes Big-Endian, Unter-Build als zwei Bytes Big-Endian. `16, 0, 1000, 6` ergibt `10 00 03 E8 00 06` (1000 = 0x03E8), der Handvektor stimmt. Das Layout (6 Byte, feste Länge) deckt `PreloginCodec`. Die Byteordnung des Unter-Builds bleibt Festlegung und ist in KDoc, Spec und Matrix so benannt. | Maintainability | `PreloginMessage.kt` · `fun version(major: Int, minor: Int, build: Int, subBuild: Int)` | ja, `PreloginNegotiationTest` | — |
| F-6 | INFO | Der Test trägt den Wert: `assertContentEquals` über die ganze Nutzlast mit dem Literal `10 00 03 E8 00 06`. Wert auf 0, Vertauschen von Build und Unter-Build oder der Byteordnung färbt ihn rot. Der Vektor ist dasselbe Literal wie die Setzung (kein unabhängiges Orakel), was KDoc und Spec als Festlegung ausweisen. Keine weitere Stelle im Repo hält `0.0.0.0` als Antwortbytes fest: andere Tests lesen nur die ENCRYPTION-Option, `SessionSupport` und `PreloginCodecTest` betreffen die Anfrage; Clientprofil, `spec/architecture.md`, Integrator und Testserver nennen die Antwort-VERSION nicht. Zwei Zeitdokumente (Beobachtung `festlegung-gilt-nur-fuer-das-clientprofil`, Sonde) nennen `0.0.0.0` als frühere Lage, zulässig. | Maintainability | `PreloginNegotiationTest.kt` · `FF  10 00 03 E8 00 06  03  00  00` | ja, Mutation unter `make test` | — |
| F-7 | INFO | Wirkung auf den .NET-Referenzclient: Die Referenztests drucken `ServerVersion` und prüfen nur auf nicht leer. Die Matrix führt `01.00.0000`, das mit dem `progVersion` des LOGINACK (`LoginAck`, `LoginResponses.kt`) zusammenpasst und nicht mit der PRELOGIN-VERSION; ein Test, der das belegt, gibt es nicht. Ob der Referenzclient `16.0.1000.6` annimmt, ist nicht gemessen, bis `make referenztest-dotnet` oder `make kompatibilitaet` läuft (Plan §2 Punkt 2 und §6 dritter Punkt). Das ist Sache des Verifiers. | Plan §6 | `docs/plan/kompatibilitaetsmatrix.md` · `ServerVersion` `01.00.0000` | ja, `make referenztest-dotnet` | — |
| F-8 | INFO | Referenzrichtung geprüft: Die Spec verweist nicht nach `docs/messungen`; das Protokoll verweist auf Lastenheft, Slice und Sonde. Die Matrix-Zeile nennt „VERSION 16.0.1000.6 … Festlegung ohne Messung“ und bleibt damit hinter dem Beleg, nicht davor. Die Nennung von [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) in den Commit-Messages ist zulässig. | `AGENTS.md` §3.4 | `spec/spezifikation.md` · `Der Wert gehört nicht zum Clientprofil` | ja, `make docs-check` | — |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../protocol/session/PreloginNegotiation.kt` (Konstanten, KDoc) | geprüft; Befunde nur F-4 (LOW) und F-5 (INFO) |
| `src/main/.../protocol/prelogin/` (Kodierung, Längen) | geprüft, ohne Befund |
| `src/test/.../protocol/session/PreloginNegotiationTest.kt` | geprüft, ohne Befund (F-6 INFO) |
| übrige `src/test`, `src/integrator`, `src/testserver`, `tools` (alte VERSION festgehalten?) | geprüft, ohne Befund |
| `referenztest-dotnet` | geprüft, ohne Befund (F-7 INFO) |
| `spec/spezifikation.md` (Setzung) | geprüft; F-1 (MEDIUM), F-3 (LOW) |
| `docs/plan/kompatibilitaetsmatrix.md` | geprüft, ohne Befund |
| `docs/messungen/prelogin-serverversion-2026-11-05.md` | geprüft; F-2 (LOW); keine Aussage über nicht gemessene d-migrate-Funktionen (die offene Anweisung ist als Vermutung gekennzeichnet) |
| Slice-Plan (Kopfzeile `Verantwortlich`) | geprüft, ohne Befund |
| Suppression, Host-statt-Docker, Schichtregeln | geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 3 |
| INFO | 4 |

**Finding-Klassen dieses Laufs:** Gemessen-Behauptung ohne auffindbares Protokoll · Messprotokoll ohne Last und Stand · Außenfakt ohne Quelle · Kommentar nennt verworfenen Wert

## Verdikt

**Merge-blockierend:** ja, wegen F-1 (MEDIUM): Die Spec beruft sich auf eine Messung der Ablehnung, die im Repo kein Protokoll hat und die die Sonde als „nicht neu gemessen“ führt. Entweder liegt das Protokoll vor, oder die Spec trägt die Ablehnung als Auftragsangabe. Das entscheidet der Implementer.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen in die Slice-Closure §7. Der Report ersetzt keine Verifikation; die Referenztests (F-7) und `make gates` prüft der Verifier.
