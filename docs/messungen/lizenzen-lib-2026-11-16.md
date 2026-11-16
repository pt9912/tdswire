# Lizenzen der Bibliotheken in lib/: Beleg vom 2026-11-16

**Lesung, keine Rechtsauskunft.** Dieser Beleg liest Lizenztexte und Projektangaben und legt die Lizenzdateien ab. Er sagt nicht, ob die Weitergabe im ZIP rechtlich genügt. Die Rechtsfrage aus [ADR-0018](../plan/adr/0018-laufzeit-artefakt.md) (Abschnitt „Offen“, Lizenzhinweise der gelieferten Bibliotheken) besteht weiter; Träger ist das Register [`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`](../plan/planning/observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md).

Messprotokoll zu [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz) und [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (`AGENTS.md` §5 Regel 7), Eingang des Anhangs von `lib/` nach [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Entscheidung 3 und Mess-Pflicht 8; Slice [slice-release-lizenzen-lib-belegen](../plan/planning/in-progress/slice-release-lizenzen-lib-belegen.md). Die Aussagen tragen so weit wie die genannte Fundstelle ([`AGENTS.md`](../../AGENTS.md) §3.8); was nicht gelesen wurde, steht als „nicht gelesen“ da.

## Bedingung der Messung

- **Datum:** 2026-11-16 (alle Abrufe), Host: Linux 6.8.0-147-generic, Einzelmessung.
- **Stand des Repos:** Branch `slice-release-lizenzen-lib-belegen` auf `main` `74eb8ffe`.
- **Liste von lib/:** am gebauten Artefakt gelesen. `docker build --target build-jar` (Stage `build-jar`, Ergebnis aus dem Layer-Cache, kein Eingriff in `make build`); `build/libs` aus dem Container nach `docker cp` auf den Host gezogen, das Image danach entfernt. Gelesen: die Dateien unter `build/libs/lib` und das Manifest von `tdswire.jar`.
- **Abruf:** `curl` auf dem Host von repo1.maven.org und raw.githubusercontent.com (Tags der Projekte), apache.org; Hashes mit `sha256sum`. Jede Datei unten ist so abgelegt, wie sie ankam (`cp`, keine Umformatierung).

## Liste von lib/

Das Manifest von `tdswire.jar` führt `Class-Path: lib/kotlinx-coroutines-core-jvm-1.10.1.jar lib/kotlin-stdlib-2.4.10.jar lib/annotations-23.0.0.jar`. Unter `build/libs/lib` liegen genau diese drei Dateien:

| Datei in lib/ | SHA-256 der Datei | Größe (Byte) |
|---|---|---|
| kotlin-stdlib-2.4.10.jar | `4ec0293bc3751423b203f1d8493251c57c42e73eb6377a6b8560d0974ff0a6df` | 1841933 |
| kotlinx-coroutines-core-jvm-1.10.1.jar | `069c5988633230e074ec0d39321ec3cdaa4547c49e90ba936c63d8fc91c8c00d` | 1475040 |
| annotations-23.0.0.jar | `7b0f19724082cbfcbc66e5abea2b9bc92cf08a1ea11e191933ed43801eb3cd05` | 29371 |

`tdswire.jar` dieses Laufs: SHA-256 `b6c8073cb807e7558662862a0aed88b594fb6b95f79fc7651056a2ec13a9d4ee` (Einzelmessung, kein Beleg für Gleichheit mit anderen Bauten).

**Abgleich mit dem Lockfile.** [`gradle.lockfile`](../../gradle.lockfile) führt für `runtimeClasspath` `org.jetbrains.kotlin:kotlin-stdlib:2.4.10`, `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1` und `org.jetbrains:annotations:23.0.0`: dieselben drei; keine Abweichung. (`kotlinx-coroutines-core:1.10.1` und das `-bom` stehen dort ebenfalls, liegen aber nicht als Datei in lib/.)

**Jars gleich dem Maven-Central-Jar.** Ein Abruf des jeweiligen Jars von repo1.maven.org am 2026-11-16 ergab dieselben drei SHA-256 wie oben (gemessen, `curl | sha256sum`). Die Quell-Jars (`-sources.jar`) wurden ebenfalls abgerufen und auf Lizenzdateien und Copyright-Zeilen durchsucht (unten).

**Lizenzdateien in den Jars:** keine. Das Verzeichnis `META-INF/` der drei Jars führt je nur `MANIFEST.MF` und (Kotlin, Coroutines) `.kotlin_module`, `com.android.tools/`, `proguard/`-Dateien, bei den Annotations `versions/9/` (Ausgabe von `unzip -l`; keine Datei mit „LICENSE“ oder „NOTICE“ im Namen). Die Lizenzdateien stammen deshalb von den Projekten (Quelle je Bibliothek unten).

## Ablage

Die Lizenzdateien liegen unter `lizenzen/lib/`, je Bibliothek ein Verzeichnis `<Gruppe>-<Artefakt>-<Version>` (Version der Datei in lib/):

| Verzeichnis unter lizenzen/lib | Datei | SHA-256 | Quelle (Abruf 2026-11-16) |
|---|---|---|---|
| `org.jetbrains.kotlin-kotlin-stdlib-2.4.10` | LICENSE.txt | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` | <https://raw.githubusercontent.com/JetBrains/kotlin/v2.4.10/license/LICENSE.txt> |
| `org.jetbrains.kotlin-kotlin-stdlib-2.4.10` | NOTICE.txt | `0b09a83d3ef7795c7dec65e815866597c066c53898f2852a76599b8f5552941a` | <https://raw.githubusercontent.com/JetBrains/kotlin/v2.4.10/license/NOTICE.txt> |
| `org.jetbrains.kotlin-kotlin-stdlib-2.4.10/third_party` | boost_LICENSE.txt | `8d8291caf1cee26d23acf3eb67c9f9a2d58f1c681b16a4fbe8cbfb9e3c0b5a9b` | <https://raw.githubusercontent.com/JetBrains/kotlin/v2.4.10/license/third_party/boost_LICENSE.txt> |
| `org.jetbrains.kotlin-kotlin-stdlib-2.4.10/third_party` | threetenbp_license.txt | `d1bc53b493a3ab387b42717ed5c4b1976a5048996f81154278100bff86d39331` | <https://raw.githubusercontent.com/JetBrains/kotlin/v2.4.10/license/third_party/threetenbp_license.txt> |
| `org.jetbrains.kotlinx-kotlinx-coroutines-core-jvm-1.10.1` | LICENSE.txt | `b1febe6399dffb10d19d35e7663ab16300c93cb0476a94115df1cc0097a8ffd8` | <https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.10.1/LICENSE.txt> |
| `org.jetbrains-annotations-23.0.0` | LICENSE.txt | `8c1e966c7855fb54027bcaf6ebe7a43abe4785791e8cf9148c363761d493d097` | <https://raw.githubusercontent.com/JetBrains/java-annotations/23.0.0/LICENSE.txt> |

Die Hashes lassen sich mit `cd lizenzen/lib && find . -type f | sort | xargs sha256sum` neu rechnen; der Review und der Verifier öffnen die Quellen erneut und vergleichen. Ändert sich `lib/` (Lockfile-Wechsel), gilt: neue Version → neues Verzeichnis, Quelle am Tag der neuen Version erneut abrufen, Hash und Anforderungen in einer neuen Fassung dieses Protokolls fortschreiben; der Bau von [slice-release-dateien-bauen](../plan/planning/open/slice-release-dateien-bauen.md) bricht an der fehlenden Datei.

## Beleg je Bibliothek

### kotlin-stdlib 2.4.10

- **Lizenz:** Apache License, Version 2.0. Fundstellen: die POM dieser Version (<https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.10/kotlin-stdlib-2.4.10.pom>) führt `<license><name>Apache-2.0</name><url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>`; der Text der abgelegten LICENSE.txt beginnt mit „Apache License Version 2.0, January 2004“. Die abgelegte Datei ist byte-gleich dem Text von <https://www.apache.org/licenses/LICENSE-2.0.txt> (Abruf 2026-11-16, gleicher SHA-256); der Anhang („APPENDIX“) führt wie dort den Platzhalter „Copyright [yyyy] [name of copyright owner]“, eine projektspezifische Copyright-Zeile steht in dieser Datei nicht.
- **Quelle der Lizenzdatei:** beim Projekt (Tag v2.4.10), URL in der Tabelle; nicht im Jar.
- **Hinweisdatei:** das Projekt führt eine NOTICE.txt (Tag v2.4.10) mit dem Kopf „NOTICE file corresponding to the section 4 d of the Apache License, Version 2.0, in this case for the Kotlin Compiler distribution.“ und den Zeilen „Kotlin Compiler“ und „Copyright 2010-2024 JetBrains s.r.o and respective authors and developers“. Sie ist abgelegt.
- **Dritt-Code:** die Projekt-Datei license/README.md am Tag v2.4.10 (<https://raw.githubusercontent.com/JetBrains/kotlin/v2.4.10/license/README.md>, SHA-256 `fae7d348841a6af205dcc5a99ab4e466e1537c839e423501d469df0c4e85f23e`, nicht abgelegt) sagt: „The Apache 2 license (…) applies to all code in this repository which is copyright by JetBrains. The following sections of the repository contain third-party code, to which different licenses may apply“. Für die Standardbibliothek nennt sie: libraries/stdlib/src/kotlin/collections (Apache 2, abgeleitet von GWT, „(C) 2007-08 Google Inc.“), libraries/stdlib/src/kotlin/time (3-clause BSD, „Copyright (c) 2007-present, Stephen Colebourne & Michael Nascimento Santos“, Datei threetenbp_license.txt), libraries/stdlib/jvm/src/kotlin/util/UnsignedJVM.kt (Apache 2, abgeleitet von Guava, „(C) 2011 The Guava Authors“), libraries/stdlib/jvm/src/kotlin/util/MathJVM.kt (Boost Software License 1.0, „Copyright Eric Ford & Hubert Holin 2001“, Datei boost_LICENSE.txt). Die Lizenztexte von GWT und Guava (third_party/gwt_license.txt, guava_license.txt) haben denselben SHA-256 wie die abgelegte LICENSE.txt (Apache 2; abgerufen, nicht doppelt abgelegt); BSD und Boost liegen unter `third_party`. Gegen das Quell-Jar `kotlin-stdlib-2.4.10-sources.jar` (Maven Central) gelesen: Copyright-Zeilen, die nicht JetBrains nennen, stehen in genau fünf Dateien: commonMain/kotlin/time/Instant.kt (Colebourne/Santos), jvmMain/kotlin/util/UnsignedJVM.kt (Guava Authors), commonMain/kotlin/collections/AbstractList.kt und AbstractMap.kt („Based on GWT … Copyright 2007 Google Inc.“) und jvmMain/kotlin/util/MathJVM.kt (Ford/Holin). Das Jar führt Klassen unter kotlin/time/ (56 Einträge), kotlin/collections/ und kotlin/math/MathKt* (Ausgabe von `unzip -l`); dass genau diese Klassen aus den genannten Quellen übersetzt sind, ist **nicht gelesen** (kein Abgleich Klasse gegen Quelldatei).
- **Anforderungen an die Weitergabe**, wörtlich aus der abgelegten LICENSE.txt, Abschnitt 4 „Redistribution“: „(a) You must give any other recipients of the Work or Derivative Works a copy of this License“; „(b) You must cause any modified files to carry prominent notices stating that You changed the files“; „(c) You must retain, in the Source form of any Derivative Works that You distribute, all copyright, patent, trademark, and attribution notices…“; „(d) If the Work includes a "NOTICE" text file as part of its distribution, then any Derivative Works that You distribute must include a readable copy of the attribution notices contained within such NOTICE file…“. Für die BSD-Datei: „Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution“. Für Boost: „The copyright notices in the Software and this entire statement … must be included in all copies of the Software, in whole or in part, and all derivative works of the Software, unless such copies or derivative works are solely in the form of machine-executable object code generated by a source language processor.“
- **Kann das ZIP sie erfüllen?** Es kann (a) mit der Datei LICENSE.txt, die NOTICE.txt und die BSD- und Boost-Datei mitführen, soweit sie neben `lib/` im Archiv liegen (Inhalt des ZIP wählt [slice-release-dateien-bauen](../plan/planning/open/slice-release-dateien-bauen.md)). Ob das Jar als Binärform des „Work“ oder als Derivative Work gilt, ob (b) und (c) für das ZIP greifen (das Projekt ändert die Jars nicht: Hash gleich Maven Central) und ob die Boost-Ausnahme „machine-executable object code“ für ein Jar gilt, sind Rechtsfragen: **offene Fragen 1 und 2**.
- **Nicht gelesen:** die Kopf-Kommentare jeder Quelldatei (nur der Suchlauf nach „Copyright“ im Quell-Jar); die Lizenzen von Dateien ohne „Copyright“-Zeile; die Klasse-zu-Quelle-Zuordnung.

### kotlinx-coroutines-core-jvm 1.10.1

- **Lizenz:** Apache License, Version 2.0. Fundstellen: die POM (<https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.10.1/kotlinx-coroutines-core-jvm-1.10.1.pom>): `<name>The Apache Software License, Version 2.0</name>`, `<url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>`; der Text der abgelegten LICENSE.txt ist der Apache-2.0-Text, Zeile 190 trägt „Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.“ (Unterschied zu apache.org: nur diese Zeile, gemessen mit `diff`).
- **Quelle der Lizenzdatei:** beim Projekt (Tag 1.10.1), URL in der Tabelle; nicht im Jar.
- **Hinweisdatei:** am Tag 1.10.1 unter NOTICE.txt nicht vorhanden (HTTP 404, 2026-11-16); eine andere Hinweisdatei im Wurzelverzeichnis wurde nicht gesucht (Wurzelverzeichnis des Tags nicht aufgelistet: **nicht gelesen**). Im Quell-Jar `kotlinx-coroutines-core-jvm-1.10.1-sources.jar` steht in keiner Datei das Wort „Copyright“ (Suchlauf).
- **Anforderungen an die Weitergabe:** Abschnitt 4 der Apache License 2.0, Wortlaut wie oben bei kotlin-stdlib; hier mit (d) ohne gefundene NOTICE-Datei.
- **Kann das ZIP sie erfüllen?** Mit der abgelegten LICENSE.txt neben `lib/` ja für (a); (d) hat keinen Gegenstand, soweit das Projekt keine NOTICE führt (nicht ausgeschlossen, siehe „nicht gelesen“). Rechtliche Wertung wie oben: offene Frage 1.

### annotations 23.0.0 (org.jetbrains:annotations)

- **Lizenz:** Apache License, Version 2.0. Fundstellen: die POM (<https://repo1.maven.org/maven2/org/jetbrains/annotations/23.0.0/annotations-23.0.0.pom>): `<name>The Apache Software License, Version 2.0</name>`, `<url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>`, Projekt <https://github.com/JetBrains/java-annotations>; Zeile 190 der abgelegten Datei: „Copyright 2000-2016 JetBrains s.r.o.“ (einzige Abweichung von apache.org, `diff`).
- **Quelle der Lizenzdatei:** beim Projekt (Tag 23.0.0), URL in der Tabelle; nicht im Jar.
- **Hinweisdatei:** am Tag 23.0.0 unter NOTICE.txt nicht vorhanden (HTTP 404); Wurzelverzeichnis nicht aufgelistet (**nicht gelesen**). Im Quell-Jar tragen 30 Dateien eine Zeile „Copyright 2000-2020/2021 JetBrains s.r.o.“; keine nennt einen anderen Rechteinhaber (Suchlauf).
- **Abweichung der Jahreszahlen:** die Lizenzdatei am Tag nennt „2000-2016“, die Quell-Dateien „2000-2020/2021“; das ist ein Befund, keine Deutung (offene Frage 3).
- **Anforderungen an die Weitergabe:** Abschnitt 4 der Apache License 2.0, wie oben.
- **Kann das ZIP sie erfüllen?** Mit LICENSE.txt neben `lib/` ja für (a); Wertung: offene Frage 1.

## Befunde

1. **Keine der drei Dateien in lib/ führt eine Lizenzdatei im Jar.** Die Lizenzen müssen von den Projekten kommen und von Hand (Hash, Quelle) belegt sein; der Beleg ist das hier.
2. **kotlin-stdlib führt Dritt-Code unter anderen Lizenzen** (BSD, Boost, GWT/Guava unter Apache 2) nach der Angabe des Projekts und nach den Copyright-Zeilen im Quell-Jar; die Texte von BSD und Boost sind abgelegt.
3. **Anforderung „Lizenztext mitführen“ ist erfüllbar**, wenn das ZIP die Dateien unter `lizenzen/lib/` mitnimmt; keine gelesene Anforderung verlangt etwas, das das ZIP nach seinem geplanten Inhalt (Jar, `lib/`, `LICENSE`, Lizenzdateien) nicht tragen kann. Das ist eine Lesung, keine Auskunft, ob das genügt.

## Offene Fragen an den Nutzer

1. Genügt es für die Weitergabe der drei Jars im ZIP, die Lizenzdateien (Apache 2.0 je Bibliothek) beizulegen, oder soll die Rechtsfrage aus [ADR-0018](../plan/adr/0018-laufzeit-artefakt.md) vor dem Anhang von `lib/` durch Dritte beantwortet werden (Alternative L3 aus [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md))? Dieser Beleg beantwortet es nicht.
2. Soll das ZIP die Dritt-Lizenzen der Kotlin-Standardbibliothek (BSD für kotlin/time, Boost für MathJVM) und die NOTICE.txt des Kotlin-Projekts mitführen (wie abgelegt vorgeschlagen), und in welcher Form (eine Datei oder das Verzeichnis `lizenzen/lib` im Archiv)? Offen ist auch, ob die Boost-Ausnahme für „machine-executable object code“ ein Jar umfasst; das Protokoll entscheidet das nicht.
3. Die Lizenzdatei von annotations 23.0.0 nennt „Copyright 2000-2016“, die Quell-Dateien „2000-2020/2021“; die Datei ist byte-gleich der Quelle abgelegt und nicht verändert. Ist das für die Weitergabe unerheblich oder soll beim Projekt nachgefragt werden?
4. NOTICE-Dateien: für kotlinx-coroutines 1.10.1 und annotations 23.0.0 wurde keine gefunden (404 am Tag; Wurzelverzeichnis nicht aufgelistet). Soll der Verifier das Wurzelverzeichnis der beiden Tags gegen eine Hinweisdatei mit anderem Namen lesen, bevor `lib/` angehängt wird?

## Nicht belegt

Rechtliche Wirkung der Beilage; ob das ZIP „Derivative Work“ im Sinn der Lizenz ist; Hinweisdateien unter anderem Namen als NOTICE.txt; die Zuordnung der Klassen im Jar zu den Dritt-Quelldateien; Lizenzen von Test-Bibliotheken und des Testservers (außerhalb von lib/, nicht Gegenstand).
