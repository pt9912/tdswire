# Review-Report: slice-listener-annahmeschleife-bei-erschoepften-dateien — 2026-11-08

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Inhalts-Commit `5400b4e3` (8 Dateien, +52/−33) im Zweig `worktree-agent-a73e777d8804566f6`, dazu die vier `slice-mv`-Commits (letzter `61551df2`); Merge-Probe gegen `main` `66fee6c1`.

**Skill:** `.harness/skills/reviewer.md` @ `5400b4e3` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-08

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice `slice-listener-annahmeschleife-bei-erschoepften-dateien` (Plan §3, DoD, Risiken §6)
- [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) (Accepted, Option A), [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md)
- [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose); `spec/spezifikation.md` Abschnitt 4 (`ACCEPT_FAILED`), `SPEC-048`
- `AGENTS.md` §3.1 bis §3.8; Beobachtung `BEO-TDS/test-naht-im-produktivcode`

---

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| Merge-Probe `git merge-tree --write-tree` Zweig gegen `66fee6c1` | sauber, **keine Konflikte**; `roadmap.md` mergt textlich glatt (auf `main` steht in Zeile 13 `Nichts in Arbeit.`, der Zweig ersetzt sie durch die Zeile zum Slice; dieselbe Form wie in der Historie, etwa `db11f729`) |
| `make gates FORCE=1` | Exit 0, 47 s (Docker-Schichten aus dem Cache; Stempel `werkzeug`/`tests` frisch gesetzt, 118/95/101/67 Fälle der Selbsttests der Stempel, `gate-zuordnung` 16 Gates) |
| `make flaeche` | Exit 0 (1,7 s, Cache) |
| `make docs-check` | 1255 Dateien, 0 Befunde (vor Anlage dieses Reports) |
| `make clean-build`, `make e2e` | nicht gefahren (Gate-Zuschnitt des Auftrags) |
| Ausgangslage der engen Klassen `TelemetryWiringTest` und `TelemetryRedactionTest` (Kopie des Arbeitsbaums, Stage `test` mit `--tests`) | grün, 58 s einschließlich Bau; die Klasse mit dem Kind-JVM-Test braucht grün je etwa 5 s |
| M1: Aufruf `prepareAcceptFailure()` in `Bootstrap.events()` entfällt | **rot** (175 s): `kein ACCEPT_FAILED` in beiden Klassen |
| M2: `Accept.Failed` und `ListenerEvent.AcceptFailed` in `prepareExhaustion` entfallen | **rot** (200 s): `kein ACCEPT_FAILED` in beiden Klassen |
| M3: `ChannelResult`/`ChannelError` in `prepareExhaustion` entfallen | **rot** (396 s): nur `TelemetryWiringTest` (`der Server nimmt nach dem Schließen nicht mehr an`); `TelemetryRedactionTest` bleibt grün, weil er nur `ACCEPT_FAILED` liest |
| M4a (eigene): von `prepareExhaustion` entfällt nur `ChannelResult.Ok`, `Failed`/`ChannelError` bleiben | **rot** (422 s): dieselbe Wiring-Meldung; der Test hält die Klasse `Ok` einzeln |
| M4b (eigene): entfällt nur `Accept.Failed`, `ListenerEvent.AcceptFailed` bleibt | **rot** (183 s): `kein ACCEPT_FAILED` |
| M4c (eigene): entfällt nur `ListenerEvent.AcceptFailed` in `prepareExhaustion` | **grün** (117 s): siehe F-2 |

Die Wirkungen von M1 bis M3 entsprechen der Beschreibung im KDoc von `TelemetryWiringTest` („je einzeln gesehen“). Gesamtlaufzeit des Reviews rund 40 min; größte Posten: die sieben Mutationsläufe (zusammen etwa 25 min, davon M3 und M4a je knapp 7 min, weil der rote Pfad in die Zeitschranken läuft und die Maschine zugleich fremde Docker-Bauten fuhr), dann das Lesen von Slice, ADR und Diff; `make gates FORCE=1` nur 47 s.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | LOW | Der Satz in Abschnitt 4 sagt, der Start bereite „den Lesepfad der Verbindungen“ vor; vorbereitet werden nur `ChannelResult` (`Ok`, `Failed`), `ChannelError` und `ChannelFailure`, und belegt ist nur das Ende gehaltener, vom Client geschlossener Verbindungen. Der Folgesatz („andere Callbacks der NIO-Gruppe“ nicht gemessen) grenzt ein, nennt aber den Lesepfad nicht als nur an dieser Stelle belegt. | `AGENTS.md` §3.8 | `spec/spezifikation.md` · „den Weg bis zum Ereignis und den Lesepfad der Verbindungen vor“ | nein, Lesart des Wortlauts; Gegenprobe wäre ein Test mit laufender Anfrage unter erschöpftem Limit | Aussage über Pfad breiter als die vorbereiteten Klassen |
| F-2 | LOW | `ListenerEvent.AcceptFailed(name)` in `prepareExhaustion` ist doppelt: `Bootstrap.events()` baut es über `prepareAcceptFailure` bereits. Seine Entfernung allein (M4c) lässt beide Testklassen grün; die Aussage „je einzeln gesehen“ im KDoc deckt diese Teilzeile nicht. | Reviewer-Skill (Eigencode, dessen Entfernung keinen Test rot macht) | `TdsListener.kt` · „ListenerEvent.AcceptFailed(name).failure == name“ | ja, Mutation M4c | Vorbereitung doppelt, Teil ohne Träger im Test |
| F-3 | INFO | Prod-Code-Naht: `prepareExhaustion` und `prepareAcceptFailure` sind Produktcode, den heute nur der Kind-JVM-Test begründet (`BEO-TDS/test-naht-im-produktivcode`, 2× offen). [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) nimmt das als Negativ („ein Stück Vorbereitung im Start“) an; Wirkung für Embedder: Verfügbarkeit nach Ressourcenmangel ([TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)), also ein Produktnutzen. Der Eintrag im Register ist bei der Closure fortzuschreiben (Slice §2). | [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) | `TelemetryEvents.kt` · „internal fun prepareAcceptFailure“ | nein | Naht im Produktcode, Planner |
| F-4 | INFO | Die Erweiterung gegenüber dem ADR-Wortlaut (Domänenereignis und Mapping der Runtime; `ChannelResult` im Lesepfad) liegt im Wortlaut „Welche Klassen genügen, bestimmt der Test der Kind-JVM“ und in der Fitness Function (Wiederannehmen als Beleg); eine Schärfung oder Folge-ADR ist nicht nötig. Die Closure-Notiz nennt sie mit Grund (DoD verlangt den Weg dort). Zu eng: der Test belegt nur gehaltene, idle Verbindungen; Klassen von Schreib-Completion, Framing und Handler unter Last sind nicht geprüft, und die Spezifikation führt sie als nicht gemessen. Zu breit ist nichts: jede der vier Gruppen ist einzeln gepinnt (M1, M2, M3, M4a, M4b). Wartbar: eine neue Klasse im Fehlerpfad macht den Test rot, aber die Meldung nennt nur „kein ACCEPT_FAILED“ bzw. „nimmt nicht mehr an“, die fehlende Klasse steht erst in der Ausgabe der Kind-JVM. | [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) | `TdsListener.kt` · „private fun prepareExhaustion“ | nein | Umfang der Vorbereitung durch Messung bestimmt |
| F-5 | INFO | Zeitschranken: grün braucht der Kind-JVM-Test etwa 5 s je Klasse, rot läuft er in die Schranken (183 bis 422 s je Lauf, fremde Docker-Last auf dem Host). Das ist unverändert zum Risiko in Slice §6 (`sh`, `ulimit -n`, 40 s/60 s, unter Last ungemessen); der Ausgang gehört in die Closure. Ein gemessenes Flake unter Last trat in den Läufen nicht auf (Ausgangslage und M4c grün). | `TDS-QA-02` | `AcceptChild.kt` · „PROVOKE_SECONDS = 40L“ | nein, Last nicht herstellbar | Test an Wanduhr und Host-Last gebunden |
| F-6 | INFO | Reihenfolge und Fehlerfall: `Bootstrap.events()` (damit `prepareAcceptFailure`) läuft vor Bindung und Start, `prepareExhaustion` in `launched` nach der Bindung und vor `launchAcceptLoop`; beides vor dem ersten Annehmen. Mehrfacher Start ist unbedenklich (Klassen einmal geladen, Aufrufe billig). Scheitert eine `check` (praktisch unerreichbar), kommt eine `IllegalStateException` aus `start` statt eines `StartResult.Failed`; Kanal und Gruppe werden im `finally` geschlossen. Die Kette `telemetry.emit { … }` im Callback hängt für ihre Lambda-Klassen am Kompiliermodus von Kotlin; der Test fängt eine Änderung. | Maintainability | `TdsListener.kt` · „prepareExhaustion()“ vor „launchAcceptLoop()“ | nein | Startreihenfolge |
| F-7 | INFO | Slice §3, Schlusssatz: „Die Dateien `AcceptChild.kt` und `TelemetryWiringTest.kt` entstehen mit dem Zweig `worktree-agent-a735f10cebdeaf861` und liegen erst nach dessen Merge auf `main`“ beschreibt einen vergangenen Zustand; beide Dateien liegen auf `main` (`66fee6c1`) und im Zweig. Zustandsfeld im Plan, Sache des Planners. | `AGENTS.md` §3.7 | Slice §3 · „liegen erst nach dessen Merge auf `main`“ | nein | Plan beschreibt abwesenden Zustand |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../transport/listener/TdsListener.kt` (Schicht, Imports aus `transport.channel`, Reihenfolge, KDoc im Indikativ) | geprüft, ohne Befund über F-2, F-6 hinaus |
| `src/main/.../runtime/Bootstrap.kt`, `TelemetryEvents.kt` | geprüft, ohne Befund über F-3, F-6 hinaus |
| `src/test/.../runtime/AcceptChild.kt`, `TelemetryWiringTest.kt`, `TelemetryRedactionTest.kt` | geprüft, ohne Befund (KDoc Indikativ ohne Chronik; Wortlaut „je einzeln gesehen“ durch M1 bis M3 bestätigt; 128/160 Dateien stimmen mit `TelemetryRedactionTest` überein) |
| Suppression (`@Suppress` u. ä.), Host statt Docker, Schichtregel, `detekt.yml` | geprüft, ohne Befund; Diff berührt keine Gate-Konfiguration |
| `spec/spezifikation.md` (Schichtregel zur Lastenheft-Ebene, Nicht-gemessen-Liste mit Jar, andere Umgebungen, andere Callbacks, Senke des Embedders) | geprüft, ohne Befund außer F-1 |
| `docs/user/` (alle Fundstellen zu `ACCEPT_FAILED`, Limit, erschöpft, Vorladen) | geprüft, ohne Befund; `referenz-ereignisse.md` „Annehmen scheiterte, der Listener läuft weiter“ stimmt; keine Treffer auf Abwesendes oder Geplantes |
| Zweite Fundstellen von „vorgeladen/Vorladen“ (Grep über `spec/`, `docs/`, `harness/`, `tools/`, `src/`, `testserver/`) | geprüft, ohne Befund: verbleibende Treffer stehen in [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md), im Slice und im Register, dort als Beschreibung des Befunds; die Beobachtung `listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still` ist bei der Closure fortzuschreiben |
| Plan-Tabelle §3 gegen Diff | geprüft, ohne Befund: alle fünf berührten Dateien stehen drin, `roadmap.md` ist Planungsdokument; „Berührte Spec-Stellen“ trifft Abschnitt 4 und `SPEC-048` |
| Haken der DoD | geprüft, ohne Befund: im Diff ist kein `- [x]` gesetzt, der DoD-Wortlaut trägt keinen Rollen-Text |
| `docs/plan/planning/in-progress/roadmap.md` | geprüft, ohne Befund: Zeile unter „Offene Wellen“ und Zeile unter `welle-betrieb-diagnose` nennen Zustand und auflösbaren Anker ohne Chronik; Ruhe-Marker folgt dem Verzeichnis (`in-progress/` trägt den Slice); Merge gegen `main` ohne Konflikt |
| `harness/README.md` (Zellen ≤ 200 Zeichen) | geprüft, ohne Befund: Datei nicht berührt |
| `docs/plan/adr/` | geprüft, ohne Befund: im Zweig keine inhaltliche Änderung an einer ADR ([ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) steht im Zweig noch auf `Proposed`, auf `main` `Accepted`; kein Befund) |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 2 |
| INFO | 5 |

**Finding-Klassen dieses Laufs:** Aussage über Pfad breiter als die vorbereiteten Klassen · Vorbereitung doppelt, Teil ohne Träger im Test · Naht im Produktcode · Test an Wanduhr und Host-Last gebunden · Plan beschreibt abwesenden Zustand

## Verdikt

**Merge-blockierend:** nein. Kein HIGH, kein MEDIUM; Option A von [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) ist umgesetzt, der Rückfall auf B nicht nötig; die Mutationen M1 bis M3 des Implementers und die eigenen M4a und M4b machen die Tests rot, M4c zeigt die eine überzählige Zeile (F-2).

**Übergabe:** Findings F-1 bis F-7 gehen an den Implementer bzw. Planner; die Finding-Klassen gehen in die Slice-Closure §7. Die Verifikation gegen die DoD prüft der Verifier.
