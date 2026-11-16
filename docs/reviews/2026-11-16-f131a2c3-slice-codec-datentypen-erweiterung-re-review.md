# Review-Report: slice-codec-datentypen-erweiterung (Re-Review) — 2026-11-16

**Review-Art:** Code, Re-Review der Nachbesserung (gegen Plan, Spec, ADR und Hard Rules; nicht gegen die DoD)

**Gegenstand:** Branch `slice-codec-datentypen-erweiterung`, `git diff 246f3f8c..f131a2c3` (Commits `dc91934f`, `5974fa68`, `f131a2c3`) und der Gesamtstand gegen [Plan](../plan/planning/in-progress/slice-codec-datentypen-erweiterung.md), Spec und ADR; Vorgänger: [Erst-Review](2026-11-16-246f3f8c-slice-codec-datentypen-erweiterung-review.md)

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-16

**Eingangs-Kontext:** [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md); [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog); `AGENTS.md` 3.7, 3.8.

Selbst gefahren: `make docs-check` (Exit 0, 1510 Dateien, 0 Befunde), `make flaeche` (Exit 0). Nicht gefahren: Testklassen (kein billiger Filter), `make gates`, `clean-build`, `e2e`. Keine künstliche Last; verfügbarer Speicher 12 100 bis 13 000 MiB. Selbst gelesen: `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/2022/katalog-abfragen.messung` (Zeile 64), `.../zusatz-unprepare-curcmd.messung` (2022, 2025), alle Anfrage-Nachrichten der Ablage `Mitschnitt/` auf `E7/A7/A5 FF FF`.

---

## Status je Finding des Erst-Reviews

| Finding | Status | Beleg |
|---|---|---|
| F-1 Test-Naht `limit` | behoben | `PlpValues`-KDoc nennt `limit` „Prüf-Naht (Kopplung)“, sagt, dass nur `TokenPlpNbcRowTest` sie setzt und der Test die Reaktion auf die Grenze belegt, nicht ein Ereignis nahe 2 GiB |
| F-2 zweite Fundstellen | behoben | `RpcBinder`-KDoc (`nvarchar(max)` = `UnicodeText`, `varchar(max)`/`varbinary(max)` gelesen, dann `RpcRejection.TYPE`); A-12 in `referenzabgleich.md`; Register `state.md`; Plan §6 A-11/A-12. Grep `fehlt im Encoder`, `trägt auch A-12`, `PLP-Typangabe`, `Datentyp-Erweiterung` über `spec/ docs/plan docs/messungen docs/user src tools harness referenztest-dotnet`: Treffer nur in `done/slice-rpc-executesql-parameter.md` (abgeschlossener Zeitdokument-Stand, bleibt) und im neuen `ProtocolError`-KDoc (zutreffend) |
| F-3 Spec-Zeiger `SPEC-028` | behoben | Tags: ein Test `@spec SPEC-028` → `@spec SPEC-003`, bei zwei weiteren Tests war `SPEC-003` schon da, `SPEC-028` entfernt; `spec-abdeckung.md`: `SPEC-003` 72 → 73, `SPEC-028` 135 → 132 (−3 Tests), `TokenPlpNbcRowTest` aus der SPEC-028-Liste. Plan-DoD (PLP, NBCROW) und Änderungsliste der Spec (2026-11-16) tragen `SPEC-003` bzw. `SPEC-003`, `SPEC-013`. Grep `SPEC-028` mit PLP/NBCROW/sql_variant/MAX in `spec/`, `docs/` (ohne den Erst-Review), `src/`: kein Treffer. Die übrigen `SPEC-028`-Treffer in `src` (TLS, Framing, Login) sind unberührt und gehören dorthin |
| F-4 Plan-Kopf, §3 | teilweise behoben | Kopf nennt jetzt `SPEC-003 · SPEC-007 · SPEC-013 · SPEC-014 · SPEC-027 · TDS-FA-03` und lässt `SPEC-024`, `ARC-007` fallen (im Diff nicht berührt: stimmt); §3 nennt `spec-abdeckung.md`, `referenzabgleich.md`, Register. Offen: siehe N-2 |
| F-5 (a) bis (e) | behoben | `RequestSequenceTest`, `RequestLoop`, `ProtocolError`-KDoc umgeschrieben; Spec: Klammer an `varchar(max)`/`varbinary(max)` getrennt von `nvarchar(max)` (Setzung 2026-11-16); Matrix Lücke 15 „kein Träger (… liefert `slice-codec-datentypen-erweiterung` nicht; Entscheidung des Nutzers offen)“ |
| F-6 Herkunft der MAX-Typen | Widerspruch aufgelöst, neue Aussage trägt nicht ganz | siehe N-1 |
| F-7 Bitmaps | behoben | Matrix und KDoc: `00 3C` symmetrisch, `00 32` trennt die Bitreihenfolge, `00 3E` im Mitschnitt, nicht getestet. Gegen Zeile 64 gelesen: `D2 00 32`, `D2 00 3E`, zweimal `D2 00 3C` stehen so; `0x3C` ist spiegelsymmetrisch, `0x32` (gespiegelt `0x4C`) und `0x3E` (`0x7C`) nicht; im Test kommt `00 3C` (Zeile 111) und `00 32` (Zeile 361) vor, `00 3E` nicht |
| F-8 Konjunktiv-KDoc | behoben | `RpcPlpTest` („Die Prüfung der Gesamtlänge (`checkTotal`) trennt …“, `checkTotal` existiert), `RpcDispatchTest` im Indikativ |
| F-9, F-10 (INFO) | übernommen | KDoc zu `MAX_BUFFERED_BYTES` (Array-Grenze, `OutOfMemoryError`) und zur Summenprüfung am Terminator |
| F-11 (INFO) | ehrlich benannt | `RpcBinderTest`-KDoc und Matrix Zeile 126: „nicht belegt … nicht getestet“; kein Test oder Verhalten dazu behauptet |
| F-12 (INFO) | übernommen | KDoc am toten Zweig von `ValueWriter` („unerreichbar, Kotlin verlangt ihn“) |
| F-13 (INFO) | unverändert | Planner-Entscheidung, wie beauftragt |

## Prüfung (a): „kein Verhalten geändert“

`git diff 246f3f8c..HEAD -U0 -- src`, jede geänderte Zeile gegen den Filter „beginnt nicht mit `/**`, `*`, `*/`, `//`“: kein Treffer. Geändert sind in `src/main` und `src/test` nur KDoc-Zeilen, Umbrüche darin und die drei `@spec`-Zeilen (zwei entfernt, eine `SPEC-028` → `SPEC-003`). Kein `@Suppress`/`SuppressWarnings` im Diff. Die Behauptung trägt.

## Findings (neu)

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| N-1 | MEDIUM | Die Nachbesserung schreibt in Spec, Matrix und KDoc: „die TYPE_INFO mit Länge `0xFFFF` ist am Referenzclient beobachtet (Spike, laut `SPEC-003`)“, in KDoc und Spec mit der Typenliste `nvarchar(max)`, `varchar(max)`, `varbinary(max)`, `xml`. Der Implementer hat den Mitschnitt nicht gelesen. Gelesen: in allen Anfrage-Nachrichten der Ablage `Mitschnitt/` steht `E7 FF FF` (`nvarchar(max)` mit Kollation `09 04 D0 00 34`) 16-mal, ausschließlich in `bulk-merge-katalog/{2022,2025}/zusatz-unprepare-curcmd.messung`; `A7 FF FF` (`varchar(max)`) und `A5 FF FF` (`varbinary(max)`) kommen in keiner Anfrage vor, `xml` nicht gesucht (keine Typkennung `F1` in der Typliste der Spec belegt). Die „Spike“-Quelle ist `SPEC-003` selbst (Absatz „RPC-Parametertypen“, ältere Fassung, ohne Messzeile). Die Aussage ist damit nur für `nvarchar(max)` belegt, die Typenliste breiter als der Beleg. Der Satz zum Wertkörper „nicht gemessen“ ist richtig. | `AGENTS.md` 3.8; Reviewer-Skill (MEDIUM „Aussage breiter als ihr Beleg“) | `src/test/kotlin/io/github/pt9912/tdswire/protocol/rpc/RpcPlpTest.kt` · `Die TYPE_INFO mit Länge `0xFFFF` ist am Referenzclient beobachtet`; `spec/spezifikation.md` · `die TYPE_INFO mit Länge `0xFFFF` ist am Referenzclient beobachtet, der Wertkörper in`; `docs/plan/kompatibilitaetsmatrix.md` · `die TYPE_INFO mit `FF FF` ist am Client beobachtet` | ja: `grep -rE "^nachricht c 03 " referenztest-dotnet/Mitschnitt \| grep -oE "(e7\|a7\|a5)ffff"` | Beobachtungsaussage breiter als der Mitschnitt |
| N-2 | LOW | §3 des Plans nennt drei berührte Dateien nicht, die der Diff ändert (zwei davon auch schon im Erst-Stand, F-4 nennt nur den Kopf): `src/main/.../protocol/dispatch/RpcBinder.kt` und `.../dispatch/RequestLoop.kt` (die Zeile für `dispatch/` nennt nur `RpcParameters.kt`) und `.../protocol/wire/ProtocolError.kt` (die Zeile für `wire/` nennt nur `ByteReader.kt`). Der Nachbesserungs-Diff ändert in diesen drei Dateien nur KDoc; sie sind aber Teil von `b028ca04..HEAD` (`ProtocolError` mit neuem Wortlaut der Zusage) und gehören in die Tabelle. | Reviewer-Skill (MEDIUM „Plan-Tabelle nennt die berührten Dateien nicht“, hier LOW: Rest von F-4, die Pfade liegen in genannten Verzeichnissen) | `docs/plan/planning/in-progress/slice-codec-datentypen-erweiterung.md` · `` `.../wire/ByteReader.kt` `` | ja: `git diff --stat b028ca04..HEAD` gegen §3 | Plan-Tabelle unvollständig |
| N-3 | INFO | Änderungsliste der Spec: die Zeile 2026-11-16 „MAX-Typen (PLP) und NBCROW im Codec“ nennt „ein MAX-Typ als Wertparameter bleibt `SPEC-013`“ und „`sql_variant` bleibt `TDS_UNSUPPORTED_TYPE`“, die Folgezeile gleichen Datums nimmt beides für `nvarchar(max)` und `sql_variant` (Basistyp `int`) zurück. Das Protokoll ist chronologisch und die zweite Zeile löst es auf; kein Widerspruch im Sinn von 3.7, nur beim Lesen der ersten Zeile allein. | Maintainability | `spec/spezifikation.md` · `ein MAX-Typ als Wertparameter bleibt `SPEC-013`` | nein | Chronologische Teilablösung |

## Zu den Prüfpunkten des Auftrags

- **(b)** SPEC-Kennung: Tags, Änderungsliste, Plan-DoD tragen `SPEC-003`; `spec-abdeckung.md` neu erzeugt, die Zählung stimmt rechnerisch zu den Tags (−3 bei `SPEC-028`, +1 bei `SPEC-003`, weil zwei der drei Tests `SPEC-003` bereits trugen; `TokenPlpNbcRowTest` bleibt in der Liste von `SPEC-003`, fehlt in der von `SPEC-028`).
- **(c)** Register-Muster `fortschreibung-laesst-zweite-fundstelle-stehen`: die Grep-Läufe nach alter Lesart („`nvarchar(max)` abgelehnt“, „NBCROW fehlt“, A-12, „kein Träger“, Matrix Lücken 2, 6, 15, Register `mitschnitt-luecken-ohne-traeger`) finden keine aktive Stelle mehr, die der Entscheidung vom 2026-11-16 widerspricht. Matrix Lücke 2 sagt „kein Träger“ für `varchar(max)`/`varbinary(max)` als Wertparameter und ist mit Lücke 15 gleich gelesen.
- **(d)** siehe N-1.
- **(g)** Plan-Kopf gegen `git diff --stat` und `git diff -- spec`: geänderte `SPEC`-Stellen sind `SPEC-003` (Absatz MAX/NBCROW), `SPEC-013` (Fehlertabelle, Absatz Backend-Vertrag) und `TDS-FA-03.a` Schritt 4; `SPEC-007`, `SPEC-014`, `SPEC-027` sind zitiert, nicht geändert (zulässig als Bezug). §3: N-2.
- **(h)** `make docs-check` Exit 0, `make flaeche` Exit 0.
- **Haken:** der Diff setzt kein `- [x]` in §2 (Plan zeigt weiter `- [ ]`).

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/…` (RequestLoop, RpcBinder, PlpReader, PlpValues, ValueWriter, ProtocolError) | geprüft, ohne Befund: nur KDoc und Umbrüche; kein Kommentar im Konjunktiv über abwesenden Text |
| `src/test/kotlin/…` (RequestSequenceTest, RpcBinderTest, RpcDispatchTest, RpcPlpTest, TokenPlpNbcRowTest) | geprüft, ohne Befund bis auf N-1 (KDoc `RpcPlpTest`) |
| `docs/plan/spec-abdeckung.md` | geprüft, ohne Befund (Zählung konsistent zu den Tags) |
| `docs/messungen/referenzabgleich.md`, Register `mitschnitt-luecken-ohne-traeger` | geprüft, ohne Befund |
| `docs/user/`, `README.md` | vom Re-Review-Diff nicht berührt, ohne Befund |

## Summary

0 HIGH, 1 MEDIUM, 1 LOW, 1 INFO neu. Von den 13 Findings des Erst-Reviews sind F-1 bis F-3, F-5, F-7, F-8 und die INFO-Punkte F-9 bis F-12 behoben beziehungsweise ehrlich benannt, F-4 bis auf N-2, F-6 im Widerspruch behoben, aber die Ersatzaussage trägt nur für `nvarchar(max)` (N-1). Die Nachbesserung ändert in `src/` keine Logik. Nicht gefahren: Testklassen und `make gates` (Verifier).
