# Review-Report: slice-release-lizenzen-lib-belegen — 2026-11-16

**Review-Art:** Code (Doku und Beleg), gegen Plan, ADR und Hard Rules; nicht gegen die DoD

**Gegenstand:** Branch `slice-release-lizenzen-lib-belegen`, `git diff 74eb8ffe..7559de60` (10 Dateien: Messprotokoll, sechs Lizenzdateien unter `lizenzen/lib/`, Slice §7, Register `state.md` und eine Datei in `evidence/`)

**Skill:** `.harness/skills/reviewer.md` @ `7559de60` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-16

**Eingangs-Kontext:** Slice `slice-release-lizenzen-lib-belegen` (Plan, §2, §3), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) (Entscheidung 3, Mess-Pflicht 8), [ADR-0018](../plan/adr/0018-laufzeit-artefakt.md) (Offen), `AGENTS.md` 3.7, 3.8, 5.

Selbst gefahren (nur lesend): `make docs-check` (Exit 0, 1511 Dateien, 0 Befunde), `sha256sum` über `lizenzen/lib/`, `curl` auf Maven Central, raw.githubusercontent.com, apache.org, api.github.com, `unzip -l` und `grep` über die Jars und Quell-Jars, `diff` gegen den Apache-Text. Nicht gefahren: `make gates`, `make clean-build`, `make e2e`, `make flaeche`, `make kopplung-zahlen`, `docker build` der Stage `build-jar`. Keine künstliche Last; verfügbarer Speicher 10,7 bis 12 GiB.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Das Protokoll sagt für kotlinx-coroutines 1.10.1, am Tag gebe es keine Hinweisdatei („NOTICE.txt nicht vorhanden (HTTP 404)“; „(d) hat keinen Gegenstand, soweit das Projekt keine NOTICE führt“). Das Wurzelverzeichnis des Tags führt ein Verzeichnis `license/` mit `license/NOTICE.txt` („NOTICE file corresponding to the section 4 d of the Apache License, Version 2.0, in this case for the kotlinx.coroutines library.“, „Copyright 2016-2024 JetBrains s.r.o and contributors“; SHA-256 beim Review `421d45acbd92670b4131621a28fcbb41ba0b747d3a336cb13fca38c0685426c4`). Die Datei liegt nicht in `lizenzen/lib/`. Das Protokoll hat den Suchpfad nur als „nicht gelesen“ benannt, aber Befund 3 („erfüllbar“) und die Abschnitte „Kann das ZIP sie erfüllen?“ ruhen auf einer Lücke, die ein einziger Listenabruf geschlossen hätte. Der Bau-Slice legt aus `lizenzen/lib/` ins ZIP, die NOTICE fehlte dort, ohne dass ein Sensor es meldet. | `AGENTS.md` 3.8; [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Entscheidung 3 (Beleg vor Anhang); Slice §2 (Hinweisdatei, „wenn die Lizenz sie verlangt“) | `docs/messungen/lizenzen-lib-2026-11-16.md` · `am Tag 1.10.1 unter NOTICE.txt nicht vorhanden (HTTP 404, 2026-11-16)` | ja: `curl https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.10.1/license/NOTICE.txt` | Aussage breiter als Beleg (Abwesenheit aus einem Pfad geschlossen) |
| F-2 | LOW | Die Beschreibung der `META-INF/`-Einträge ist ungenau: „je nur `MANIFEST.MF` und (Kotlin, Coroutines) `.kotlin_module`, `com.android.tools/`, `proguard/`-Dateien, bei den Annotations `versions/9/`“. Gelesen am Maven-Central-Jar: kotlin-stdlib führt `.kotlin_module` (drei) und `versions/9/module-info.class`, aber kein `com.android.tools/` und kein `proguard/`; Coroutines führt beides sowie `kotlinx_coroutines_core.version` und `versions/9/`. Die Kernaussage „keine LICENSE- oder NOTICE-Datei im Jar“ stimmt für alle drei (nachgeprüft). | `AGENTS.md` 3.8 | `docs/messungen/lizenzen-lib-2026-11-16.md` · `Das Verzeichnis `META-INF/` der drei Jars führt je nur` | ja: `unzip -l` | Aussage über Verzeichnisinhalt breiter als gelesen |
| F-3 | LOW | Die abgelegte NOTICE.txt nennt sich „in this case for the Kotlin Compiler distribution“. Das Protokoll führt sie als „Hinweisdatei“ der Standardbibliothek („Sie ist abgelegt“) und nennt die Kopfzeile, sagt aber nicht, dass die Datei nach ihrem eigenen Wortlaut eine andere Distribution beschreibt als das Jar `kotlin-stdlib`. Ob (d) hier greift, ist damit nicht belegt; Frage 2 fragt nur, ob die Datei mitgeführt werden soll. | `AGENTS.md` 3.8 | `docs/messungen/lizenzen-lib-2026-11-16.md` · `Hinweisdatei:** das Projekt führt eine NOTICE.txt (Tag v2.4.10)` | nein | Zuordnung Hinweisdatei zu Artefakt nicht ausgewiesen |
| F-4 | LOW | Die Plan-Tabelle §3 nennt drei Zeilen. Der Diff berührt außerdem den Slice selbst (§7, Zeile „Ablageort der Lizenzdateien“). Das ist von der DoD gedeckt (der Ablageort steht „im Beleg und in §7“), aber nicht in §3 gelistet. | Reviewer-Skill (Plan-Tabelle nennt die berührten Dateien nicht) | `docs/plan/planning/done/slice-release-lizenzen-lib-belegen.md` · `Ablageort der Lizenzdateien (Eingang für` | ja: `git diff --stat` | Plan-Tabelle unvollständig |
| F-5 | LOW | Zweite Fundstelle: [`observation.md`](../plan/planning/observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md) nennt als gelieferte Bibliotheken „heute Kotlin-Standardbibliothek, Coroutines“; der Beleg liest am Artefakt drei (zusätzlich `org.jetbrains:annotations`). `state.md` ist fortgeschrieben, `observation.md` nicht. Die Aussage ist unvollständig, nicht falsch. | Reviewer-Skill (Fortschreibung lässt zweite Fundstelle stehen) | `docs/plan/planning/observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md` · `heute Kotlin-Standardbibliothek, Coroutines` | ja: Grep | Fortschreibung lässt zweite Fundstelle stehen |
| F-6 | INFO | `state.md` trägt neben dem Zustand den Zusatz „bleiben als frühere Fundstellen“; das ist nahe an Chronik, aber mit auflösbaren Ankern (Beleg, Slice) belegt. Kein Befund nach 3.7, ein Hinweis. | `AGENTS.md` 3.7 | `.../lizenzhinweise-der-bibliotheken-rechtsfrage/state.md` · `bleiben als frühere Fundstellen` | nein | Zustandsfeld mit Anflug von Chronik |
| F-7 | INFO | Die Seite des Artefakts der Behauptung „Hash des Jars am Artefakt gleich Maven Central“ (Hashes der Dateien unter `build/libs/lib` nach `docker build --target build-jar`) habe ich nicht erneut gemessen (Docker-Bau, außerhalb des engsten Sensors). Die Central-Seite stimmt für alle drei Jars (siehe unten). Der Verifier fährt den Bau auf dem End-Stand. | `AGENTS.md` 3.8 | `docs/messungen/lizenzen-lib-2026-11-16.md` · `Jars gleich dem Maven-Central-Jar` | ja: `docker build --target build-jar`, `sha256sum` | nicht erneut gemessen |

## Nachgeprüft, ohne Befund (Fragen 1 bis 3 des Auftrags)

**Byte-Gleichheit der Ablage.** `sha256sum` über `lizenzen/lib/`: alle sechs Hashes gleich der Tabelle „Ablage“. Alle sechs Quellen erneut abgerufen (nicht nur zwei): kotlin `LICENSE.txt` (`cfc7749b…`), `NOTICE.txt` (`0b09a83d…`), `boost_LICENSE.txt` (`8d8291ca…`), `threetenbp_license.txt` (`d1bc53b4…`), coroutines `LICENSE.txt` (`b1febe63…`), annotations `LICENSE.txt` (`8c1e966c…`): jeder Hash gleich. Die Hashes sind je 64 Zeichen lang. Kotlin-`license/README.md` gleich `fae7d348…`. apache.org `LICENSE-2.0.txt` gleich `cfc7749b…`, also byte-gleich der Kotlin-Datei; `gwt_license.txt` und `guava_license.txt` ebenfalls `cfc7749b…`, wie behauptet.

**Jars gegen Maven Central.** `curl | sha256sum` der drei Jars: `4ec0293b…` (stdlib), `069c5988…` (coroutines), `7b0f1972…` (annotations), gleich der Tabelle „Liste von lib/“. `gradle.lockfile` führt die drei Koordinaten in `runtimeClasspath` (Zeilen 69, 75, 85), dazu `kotlinx-coroutines-core:1.10.1` und das `-bom`, wie im Protokoll gesagt. Das Manifest von `tdswire.jar` habe ich nicht erneut gelesen (siehe F-7).

**Lizenz laut POM.** Alle drei POMs abgerufen: stdlib `<name>Apache-2.0</name>` mit URL `http://www.apache.org/licenses/LICENSE-2.0.txt`; coroutines und annotations `The Apache Software License, Version 2.0` mit `https://…`. Wörtlich wie im Protokoll. Die Kopfzeilen 190 der beiden Projektdateien stimmen („2000-2020 … Kotlin Programming Language contributors.“, „2000-2016 JetBrains s.r.o.“); `diff` gegen apache.org: nur diese Zeile.

**Quell-Jar-Zählungen.** stdlib: Zeilen „Copyright“ ohne JetBrains stehen in genau fünf Dateien (Instant.kt, UnsignedJVM.kt, AbstractList.kt, AbstractMap.kt, MathJVM.kt), mit den genannten Rechteinhabern; „Based on GWT“ steht in AbstractList.kt und AbstractMap.kt. coroutines: kein „copyright“ (ohne Beachtung der Schreibweise) im Quell-Jar. annotations: 30 Dateien, eine mit „2000-2020“, 29 mit „2000-2021“, keine ohne JetBrains. Gleich dem Protokoll. Die 56 `kotlin/time/`-Einträge stimmen (zählt das Verzeichnis mit).

**Drittlizenzen der stdlib.** Kotlin `license/README.md` am Tag v2.4.10 führt unter `libraries/stdlib` für die JVM-Teile genau: `src/kotlin/collections` (Apache 2, GWT), `src/kotlin/time` (BSD, Colebourne/Santos), `jvm/src/kotlin/util/UnsignedJVM.kt` (Apache 2, Guava), `jvm/src/kotlin/util/MathJVM.kt` (Boost, Ford/Holin); die übrigen stdlib-Einträge betreffen JS, Wasm, Native. Der wörtliche Einleitungssatz, die Texte „Redistributions in binary form must reproduce …“ (BSD) und „… unless such copies or derivative works are solely in the form of machine-executable object code …“ (Boost) stehen so in den abgelegten Dateien.

**Apache-Abschnitt 4 wörtlich.** Die Zitate (a), (b), (c), (d) stehen in `LICENSE.txt` Zeilen 95 bis 115 mit dem im Protokoll gezeigten Wortlaut (Auslassungen mit „…“ gekennzeichnet).

**Rechtsvorbehalt (Frage 2).** Der Beleg beginnt mit „Lesung, keine Rechtsauskunft“, nennt die Rechtsfrage aus [ADR-0018](../plan/adr/0018-laufzeit-artefakt.md) als bestehend, führt vier Fragen einzeln (Genügen der Beilage und Alternative L3, Dritt-Lizenzen und Form im Archiv samt Boost-Ausnahme, Jahreszahl-Abweichung, NOTICE-Suche) und sagt in Befund 3 „Lesung, keine Auskunft“. Nichts davon entscheidet still; Rechtsfragen (Derivative Work, (b), (c), Boost-Ausnahme) sind als offen benannt. Einschränkung: F-1 und F-3 betreffen die Grundlage von Befund 3, nicht den Vorbehalt.

**Doku-Gate und Eingangslisten (Frage 4).** `make docs-check` Exit 0, 0 Befunde (der neue Pfad `lizenzen/`, die relativen Links des Protokolls, des Registers und des Slice lösen auf; die Pfade im Register stimmen: `evidence/` sechs Ebenen, `state.md` fünf Ebenen zu `docs/`). `lizenzen/` kommt in `Dockerfile`, `tools/stempel/*.eingaenge` und `.d-check.yml` nicht vor: kein `COPY`, keine Eingangsliste, kein Stempel betroffen; der Eintrag dort ist Aufgabe von [slice-release-dateien-bauen](../plan/planning/in-progress/slice-release-dateien-bauen.md) (Slice §2 sagt es).

**Register (Frage 5).** `evidence/slice-release-lizenzen-lib-belegen.md` in der Form der Nachbardatei (`Vorgang:` und `Fund:`, zwei Zeilen), `evidence/` nicht leer (zwei Dateien), kein Zähler gesetzt (kein `zaehler`-Feld im Diff; Slice §2 verlangt es). `state.md` nennt Zustand und Beleg als auflösbaren Anker.

**Haken (Reviewer-Skill).** Im Diff steht kein `- [x]`; die DoD des Slice ist unverändert.

**Plan-Tabelle §3 gegen `git diff --stat` (Frage 6).** Messprotokoll, Ablage (sechs Dateien unter `lizenzen/lib/`) und Register (`state.md`, eine Datei in `evidence/`) stehen in der Tabelle; zusätzlich der Slice selbst (F-4). Keine weitere Datei im Diff; `Dockerfile`, `tools/`, `releasing.md`, `harness/README.md`, `LICENSE`, `README.md`, `docs/user/` sind nicht berührt, wie Slice §1 und §2 sagen. Zweite Fundstelle der Kernaussagen („Artefakt führt keine Hinweise“ in `releasing.md` Schritt 7, [ADR-0018](../plan/adr/0018-laufzeit-artefakt.md) Offen, [slice-release-dateien-bauen](../plan/planning/in-progress/slice-release-dateien-bauen.md)): Der Slice lässt sie nach Plan stehen (`releasing.md` wahr bis zum Bau; die ADR ist `Accepted` und bleibt); einzige Lücke F-5.

## Negativbefunde

- `lizenzen/lib/` (sechs Dateien): geprüft, ohne Befund (Hash, Byte-Gleichheit zur Quelle, Namensform `<Gruppe>-<Artefakt>-<Version>` gleich Plan).
- `docs/messungen/lizenzen-lib-2026-11-16.md`: Befunde F-1 bis F-3, F-7; alle übrigen nachgeprüften Aussagen tragen.
- Register `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/`: geprüft, Befunde F-5, F-6.
- `docs/plan/planning/done/slice-release-lizenzen-lib-belegen.md`: geprüft; F-4; keine Haken gesetzt.
- Hard Rules 3.1 (Docker-only), 3.2, 3.5 (keine ADR geändert), 3.6, 3.7 (im Protokoll keine Konjunktive über verworfene Alternativen), Spec-Straten (keine Spec-Datei berührt): geprüft, ohne Befund.
- Nicht lesbar für mich: der Lauf der Stage `build-jar` und seine `lib/`-Hashes (F-7); die Rechtsfragen selbst (nicht Gegenstand des Reviews).

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 4 |
| INFO | 2 |

## Verdikt

Kein HIGH. Ein MEDIUM (F-1): `kotlinx.coroutines` führt am Tag 1.10.1 eine `license/NOTICE.txt`; das Protokoll hat „keine Hinweisdatei“ aus dem 404 an der Wurzel geschlossen, und die Datei fehlt unter `lizenzen/lib/`. Die Hashes, Quellen, Zitate und Zählungen des Protokolls tragen sonst nach Nachprüfung; der Rechtsvorbehalt ist gehalten.
