# Review-Report: slice-qa-dotnet-lieferkette-haerten, Liefer-Punkt 1 (HEAD d08d891a) — 2026-11-06

**Review-Art:** Code (gegen Plan, ADR, Hard Rules, Reviewer-Skill; nicht gegen die DoD, das ist die Frage des Verifiers)

**Gegenstand:** Zweig `slice-qa-dotnet-lieferkette-haerten`, `git diff 1c9a85c9..d08d891a` (4 Dateien, +82/-7): `referenztest-dotnet/nuget.config`, `docs/messungen/dotnet-signaturpruefung-2026-11-06.md`, `harness/README.md`, `tools/mk/referenztest-dotnet.mk`.

**Skill:** `.harness/skills/reviewer.md` (Stand des Zweigs) · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-06

**Eingangs-Kontext:**

- Plan: `slice-qa-dotnet-lieferkette-haerten` (`in-progress/`), Liefer-Punkt 1 und Risiko aus §6
- ADR: [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) (Accepted)
- Carveout: [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md)
- Lastenheft: [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)
- `AGENTS.md` §3.5, §3.6, §3.7, §3.8
- Gefahren (Docker, Image und Digest wie im Messprotokoll, Kopien von `referenztest-dotnet/` im Scratchpad, Arbeitsbaum unverändert): Grünlauf `docker build --no-cache-filter restore --target restore`, dann `--target test` und `--target lock` (alle Exit 0); Rotläufe a bis f unten; `dotnet nuget verify --all` über alle 36 `.nupkg` des Restore. Nicht gefahren: `make gates`, `make stempel-eingaenge-pruefung`.

**Nachmessung der Aussagen des Messprotokolls:**

| Aussage | Nachgemessen |
|---|---|
| Zertifikatszählung 20/7/1/1/7 Autor, 34/1/1 Repository, 36 Pakete, jedes mit Autoren- und Repository-Signatur | bestätigt (Fingerabdruck-Anfänge `566A3188`, `9A1B131B`, `AA12DA22`, `B8F6A977`, `0D766240`; `1F4B311D`, `0E5F38F5`, `5A2901D6`) |
| Datei im Repo: Exit 0 | bestätigt (Stufen `restore`, `test`, `lock`) |
| `require`, keine Liste: Exit 1, `NU3034` „no trusted signers were specified“ | bestätigt (a) |
| ohne xUnit-Autoren: Exit 0; ohne Repository-Eintrag: Exit 0 | bestätigt (b, c) |
| ohne beides: Exit 1, `NU3034` „signed but not by a trusted signer“ | bestätigt (d), aber acht Pakete, nicht drei (F-2) |
| ohne `signatureValidationMode`, mit Liste: Exit 0 | bestätigt (e) |
| (nicht im Protokoll) nur Repository-Zertifikat `1F4B311D`, keine Autoren | Exit 1, genau zwei Pakete: `Microsoft.SqlServer.Server 1.0.0` (Repository `5A2901D6`) und `xunit.abstractions 2.0.3` (Repository `0E5F38F5`) |

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Der Kommentar der `nuget.config` sagt, ein neues oder aktualisiertes Paket mit anderem Zertifikat breche den Restore. Das Protokoll selbst zeigt das Gegenteil für den Regelfall: ohne die xUnit-Autoren (b) bleibt der Restore grün, weil der Repository-Eintrag jedes von nuget.org gegengezeichnete Paket zulässt; die Autorenzertifikate beschränken solange nichts. Brechen würde der Restore nur bei unsigniertem Paket oder bei Wechsel des Repository-Zertifikats; das Protokoll führt den Satz als „erwartet“, der Kommentar als Tatsache. Wer dem Kommentar glaubt, hält die Autoren für gebunden, obwohl allein `contentHash`/Lock das Paket festhält. Dazu: „Beide Listen zusammen prüfen jedes Paket doppelt“ (Protokoll) steht neben „überflüssig“ im selben Absatz; gemessen ist ODER, nicht UND. | `AGENTS.md` §3.8; Reviewer-Skill „Zusage ohne ihre gemessene Bedingung im selben Satz" | `referenztest-dotnet/nuget.config` · `Ein neues oder aktualisiertes Paket mit anderem`; `docs/messungen/dotnet-signaturpruefung-2026-11-06.md` · `prüfen jedes Paket doppelt` | ja — Kopie der Datei mit Repository-Eintrag, ohne den Autor eines Pakets: Exit 0 (b, c) | Wirkung der Vertrauensliste breiter gesagt als gemessen (ODER statt UND) |
| F-2 | LOW | Die Zeile „ohne xUnit-Autoren und ohne Repository-Eintrag“ nennt als Fehlerfälle `xunit.assert`, `xunit.core`, `xunit.runner.visualstudio`. Es scheitern acht Pakete: zusätzlich `xunit`, `xunit.abstractions`, `xunit.analyzers`, `xunit.extensibility.core`, `xunit.extensibility.execution`. Der Satz zur erwarteten Wirkung bei neuem Paket stützt sich auf diese Zeile. | `AGENTS.md` §3.8 | `docs/messungen/dotnet-signaturpruefung-2026-11-06.md` · `für \`xunit.assert\`, \`xunit.core\`, \`xunit.runner.visualstudio\`` | ja — Lauf d, `NU3034`-Zeilen zählen | Messzeile unvollständig wiedergegeben |
| F-3 | LOW | Der Satz zur ADR erklärt den Befund (Repository-Zertifikat allein scheiterte) als Ursache, aber das Protokoll hat keine Messzeile für genau diese Konfiguration (Repository `1F4B311D` allein). Nachgemessen: richtig, es scheitern genau diese beiden Pakete. Die Erklärung ist damit wahr, aber im Protokoll als Folgerung ohne Zeile belegt. | `AGENTS.md` §3.8 | `docs/messungen/dotnet-signaturpruefung-2026-11-06.md` · `nannte nur dieses; die beiden anderen` | ja — Lauf f | Erklärung ohne eigene Messzeile |
| F-4 | LOW | Der Kommentar der `nuget.config` zeigt als Beleg auf den Slice (`Messung im Slice slice-qa-dotnet-lieferkette-haerten`); die Messzeilen stehen im Messprotokoll. Der Slice wandert bei der Closure in `done/`, das Protokoll bleibt der Ort der Messung. Ein Rang-Zeiger auf das dauerhafte Artefakt fehlt. | `AGENTS.md` §3.7 (Rang-Zeiger) | `referenztest-dotnet/nuget.config` · `Messung im Slice slice-qa-dotnet-lieferkette-haerten` | nein | Zeiger auf den Slice statt auf das Protokoll |
| F-5 | INFO | Widerspruch zu [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) (Accepted, nach `AGENTS.md` §3.5 nicht inhaltlich zu ändern): dort steht „eine funktionierende Liste ist nicht gebaut“ (Messung, Absatz zu `signatureValidationMode`, und Offene Punkte (2)), ebenso in der Begründung von [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md). Der Diff ändert die ADR zu Recht nicht. Das Protokoll sagt nicht, dass es diese Sätze ablöst, und im Slice (§6, Risiko 1) ist der Ausgang „offen bis zur Closure“. Welches Artefakt den Widerspruch löst (Folge-ADR mit `Supersedes`, oder das Messprotokoll als dokumentierter Stand), ist nirgends benannt. Rolle: Architect/Closure, kein Rollenkonflikt, daher keine Konflikt-Sequenz. | `AGENTS.md` §3.5; Slice §6 | `docs/plan/adr/0008-dotnet-kompatibilitaetstests.md` · `eine funktionierende Liste ist nicht gebaut` | nein | Accepted-ADR-Aussage überholt, Ablöser nicht benannt |
| F-6 | INFO | Zertifikatsablauf und -rotation: Das Protokoll führt „abgelaufenes Zertifikat“ als nicht gemessen und die Datei sagt es ebenso; das ist ehrlich. Nicht benannt ist die Wirkung eines Wechsels des Repository-Zertifikats durch nuget.org: der Restore (und damit `make referenztest-dotnet`, `make referenztest-dotnet-lock`, ein Frischlauf von `make gates`) würde ohne Änderung am Repo rot. Zudem lag beim Lauf f gelesen vor: die Zertifikate `AA12DA22` (gültig bis 2023-10-05) und `B8F6A977` (bis 2021-06-03) sind schon abgelaufen, die Pakete mit ihnen bestehen den Grünlauf; das steht nicht im Protokoll, obwohl es dem Satz „nicht gemessen“ nahekommt (die Ursache, etwa Zeitstempel der Signatur, ist nicht gemessen). | `TDS-QA-06`; `AGENTS.md` §3.8 | `docs/messungen/dotnet-signaturpruefung-2026-11-06.md` · `Verhalten bei einem abgelaufenen Zertifikat` | ja — `dotnet nuget verify --all` auf `xunit.abstractions` zeigt die Gültigkeit | Rotationswirkung nicht benannt |

## Prüfpunkte der Beauftragung

1. Belege: Fingerabdrücke, 36 Pakete, 34 von 36, Rotfälle (`NU3034`) nachgemessen, siehe Tabelle oben; Abweichungen F-1 bis F-3.
2. Einordnung der [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md): siehe F-5 (Widerspruch bei [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) Messung und Offene Punkte (2); Ablöser nicht benannt; Ursache selbst stimmt, F-3).
3. Lockerung: ausgeschlossen. Der Diff setzt `signatureValidationMode=require` und fügt Vertrauenseinträge hinzu, `allowUntrustedRoot=false` überall, nichts entfällt, `<clear />` und Quellen-Mapping unverändert. Lock und `contentHash` bleiben. Hinweis: die Strenge, die die Autorenzertifikate beisteuern, ist solange der Repository-Eintrag steht null (F-1); gelockert wird damit nichts, erhöht wird weniger als der Kommentar sagt.
4. Rotation: Wirkung nur teilweise benannt, siehe F-6 und F-1.
5. Kommentare: `tools/mk/referenztest-dotnet.mk` Ist-Zustand und nachgemessen (Stufe `lock` läuft mit `require`, Exit 0); `nuget.config`: bis auf F-1 und F-4 Ist-Zustand, keine verworfene Alternative, kein Abbruch im Satz.
6. Stempel-Eingänge: `tools/stempel/tests.eingaenge` führt `referenztest-dotnet` (rekursiv, deckt `nuget.config`) und `tools/mk/referenztest-dotnet.mk`; `stempel.eingaenge` führt nur das Dockerfile des Verzeichnisses. `docs/messungen/` und `harness/README.md` sind keine Eingänge einer Klasse und müssen es nicht sein (keine `COPY`-Quelle, kein Rezept). Gelesen, die Prüfung selbst nicht gefahren.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `referenztest-dotnet/` (`nuget.config`, Zertifikats-Fingerabdrücke, Zuordnung zu Autor/Repository) | geprüft, Befund F-1, F-4; Fingerabdrücke und Zuordnung ohne Befund |
| `docs/messungen/` | geprüft, Befunde F-1 bis F-3, F-6 |
| `harness/README.md` (Zeile `make referenztest-dotnet`, „Nicht behauptet“) | geprüft, ohne Befund (der Rotfall „einmal von Hand gemessen“ stimmt; „Restore prüft Signaturen“ stimmt) |
| `tools/mk/` | geprüft, ohne Befund |
| `tools/stempel/` | geprüft, ohne Befund |
| `docs/plan/adr/`, `docs/plan/carveouts/` | unverändert im Diff; Widerspruch F-5 |
| Hard Rules §3.1 (Docker-only), §3.2, §3.6 | geprüft, ohne Befund |

## Summary

0 HIGH, 1 MEDIUM, 3 LOW, 2 INFO. Der Rotfall der DoD (Paket, dessen Signierer nicht in der Liste steht) ist reproduzierbar und ehrlich als Einzelmessung gekennzeichnet; er entsteht allerdings erst, wenn auch der Repository-Eintrag fehlt. Die Aussagen zur Wirkung der Liste auf neue Pakete (Kommentar der Datei) sind breiter als die Messung (F-1).
