# ADR-0040: Wirksame Konfiguration im Ereignis CONFIG_EFFECTIVE entsteht aus einem typisierten Wertobjekt

**Status:** Proposed

**Datum:** 2026-11-07

**Autor:** pt9912

**Bezug:** [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0020](0020-logformat-und-observability-schnittstelle.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Schärft:** [TDS-FA-09.a](../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose) (Nr. 3: Ereignisse) und die Redaktionszusage in [Abschnitt 4](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) (Ereignismodell, Felder). Kein `Supersedes`: [ADR-0020](0020-logformat-und-observability-schnittstelle.md) bleibt unverändert gültig; diese ADR legt fest, wie dessen Grundsatz „Redaktion durch Bauart“ für ein Feld gilt, das eine Abbildung von Text auf Text trägt.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

Die Zustandsereignisse tragen ein Ereignis `CONFIG_EFFECTIVE` mit dem öffentlichen Lesefeld `TelemetryEvent.settings: Map<String, String>`. Die Werte füllt die interne Funktion `effectiveSettings` aus der Serverkonfiguration; der Konstruktor des Ereignisses nimmt jede Abbildung von Text auf Text an und kürzt Schlüssel und Werte auf 256 Zeichen. Der Review der Lieferung (Fund F-5) stellt die Frage, ob das noch der Zusage aus [ADR-0020](0020-logformat-und-observability-schnittstelle.md) genügt: Das Modell hat für Passwort, Prüfwert, Pfad, Parameterwert und Ausnahmetext **kein Feld**, und ein Adapter kann nur ausgeben, was das Ereignis trägt; die Modellform ist eine geschlossene Menge mit typisierten Feldern, kein Freitextfeld.

**Gelesener Stand der Lieferung (Zweig der Umsetzung, nicht auf `main`).** `effectiveSettings` nimmt nur Zahlen, Namen von Aufzählungen und Schalter; für den Schlüsselspeicher steht `SET` oder `UNSET`. Die Geheimnisfreiheit hält genau diese Funktion und ein Test, der sie direkt aufruft. Der Redaktionstest über alle Ereignisarten (Typen und Textfelder je Art) erlaubt den Typ `Map` namentlich für `settings` und prüft dessen Inhalt nicht. Gemessen im Review: Die Mutation „Wert von `TLS_KEYSTORE_FILE` ist der Inhalt des Schlüsselspeichers“ macht genau diesen einen Test rot; die Redaktionstests und die Konfigurationsnegativtests bleiben grün.

**Was ADR-0020 schon zulässt.** Zeichenketten sind in vier Feldern erlaubt (Kennung nach angenommener Anmeldung, Adresse beim Bindefehler, Typname der Ausnahme, Schlüsselname der Konfiguration); ihre Quelle ist je ein benannter, unkritischer Wert, und die Länge ist im Modell begrenzt. Ein öffentliches Textfeld ist also zulässig, wenn **die Konstruktion** es auf unkritische Quellen beschränkt. Bei `settings` ist das nicht der Fall: Die Funktion, die die Abbildung füllt, liest ein Objekt, in dessen Reichweite der Inhalt des Schlüsselspeichers, das Passwort und die Benutzerliste liegen, und das Ereignis nimmt jede Abbildung entgegen. Die Bauart besteht dort aus einer Konvention („diese Funktion schreibt nur Unkritisches“), die ein Test prüft.

**Zur öffentlichen Fläche.** Die Bibliothek führt genau eine öffentliche Ereignisklasse, `TelemetryEvent`, mit den Lesefeldern je Art ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md); [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4: jeder neue öffentliche Name steht in der Liste von `SPEC-059`). `make flaeche` vergleicht die Typnamen dieser Liste mit den öffentlichen Typdeklarationen; Mitglieder vergleicht es nicht. Eine Entscheidung, die einen öffentlichen Typnamen anlegt, ändert die Liste und löst die Prüfung aus.

Nicht gemessen: ob ein Embedder die Schlüssel der Abbildung als Vertrag liest; die Zusage der Schlüsselmenge steht in der Spezifikation (Setzung), nicht in einem Typ.

## Entscheidung

Wir wählen **D**: Das Lesefeld `settings` bleibt ein schreibgeschütztes `Map<String, String>`, aber `CONFIG_EFFECTIVE` entsteht nur aus einem **internen typisierten Wertobjekt** (Arbeitsname `EffectiveSettings`). Dessen Felder sind ausschließlich Zahlentypen, Aufzählungen und `Boolean`; der Schlüsselspeicher ist eine Aufzählung mit den Werten `SET` und `UNSET`. Die Abbildung Text auf Text ist die Ausgabeform dieses Objekts (Schlüssel aus `ConfigKeys`, Wert aus `toString` der Zahl, `name` der Aufzählung oder `true`/`false`) und wird beim Bau des Ereignisses daraus abgeleitet. Der Konstruktor des Ereignisses nimmt das Wertobjekt, **nicht** eine Abbildung. Ein Wert wie Pfad, Passwort, Prüfwert, Inhalt des Schlüsselspeichers oder Benutzerliste hat damit keinen Typ, in dem er ankommen könnte; die Mutation „Inhalt des Speichers als Wert“ ist ein Übersetzungsfehler, kein roter Test.

Gründe: Die Zusage aus [ADR-0020](0020-logformat-und-observability-schnittstelle.md) folgt aus den Typen der Felder des Modells; D verlegt den Typ an die Stelle, an der das Ereignis gebaut wird, und lässt die öffentliche Form (ein Lesefeld, ein Java-Getter mit `Map<String, String>`) unverändert. Es entsteht kein neuer öffentlicher Name, `SPEC-059` und `make flaeche` bleiben unberührt. Das ist der Weg mit dem geringsten Zusatzaufwand, der die Zusage an die Bauart bindet statt an einen einzelnen Test.

**Verdikt zum Fund F-5 (Konflikt-Pfad).** Die Entscheidung gilt: [ADR-0020](0020-logformat-und-observability-schnittstelle.md) verlangt Geheimnisfreiheit aus der Bauart, und die Lieferung erfüllt das heute mit Konvention. Die Lockerung des Redaktionstests (Typ `Map` namentlich für `settings`) ist legitim, aber nur mit dem typisierten Wertobjekt dahinter; sie ist mit dieser ADR dokumentiert. Eine Senkung der Prüfstärke ist das nicht, wenn die Folgen unten umgesetzt sind.

**Konsequenzen für den laufenden Zweig (was der Implementer ändert).**

1. Interner Typ `EffectiveSettings` mit Feldern nur aus Zahlen, Aufzählungen, `Boolean`; Aufzählung für den Schlüsselspeicher (`SET`, `UNSET`). Name und Ort sind Setzung des Baus; der Typ ist `internal` und steht nicht in der Liste von `SPEC-059`.
2. `ServerConfig.effectiveSettings()` liefert `EffectiveSettings`; die Ableitung der Abbildung (Schlüssel aus `ConfigKeys`) steht an genau einer Stelle am Wertobjekt.
3. `ConfigEffective` nimmt `EffectiveSettings`; `settings` bleibt öffentlich und gibt eine unveränderliche Abbildung zurück. Die Kürzung auf 256 Zeichen für die Werte entfällt, wo der Typ sie begrenzt (die Schlüssel sind Konstanten).
4. Der Redaktionstest über die Ereignisarten behält die namentliche Erlaubnis für `settings`, prüft aber zusätzlich per Reflexion, dass die Felder von `EffectiveSettings` nur Zahlentypen, Aufzählungen und `Boolean` sind. Der bestehende Test mit Geheimnismarken in Pfad, Passwort und Benutzerliste bleibt als zweite, billige Probe stehen (Marke darf in keinem Wert vorkommen); er ist nicht mehr der Träger der Zusage.
5. Die Mutation „Inhalt des Speichers als Wert“ aus dem Review wird gegen den neuen Stand wiederholt; erwartet ist ein Übersetzungsfehler. Spezifikation Abschnitt 4: ein Satz, dass `settings` aus einem typisierten Wertobjekt entsteht; kein neuer Typname in `SPEC-059`.

## Verglichene Alternativen

| Option | Sicherheit ([TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)) | Fläche | Wartbarkeit | Java-Lesart |
|---|---|---|---|---|
| A — Abbildung bleibt; `effectiveSettings` einzige Quelle mit Positivliste; Marken-Test über alle Werte (Lesart: Bauart ist die einzige Konstruktion) | Ein Leck wird nur gefunden, wenn der Test die Marke trifft; ein neuer Eintrag mit einer Quelle, die der Test nicht kennt, bleibt unentdeckt. „Einzige Quelle“ ist Konvention: der Konstruktor des Ereignisses nimmt jeden Text. Schwächer als die vier Textfelder aus ADR-0020, deren Quelle je einzeln benannt ist | keine Änderung | niedrigster Aufwand; jede neue Einstellung ist ein Eintrag in der Funktion plus ein Testfall | `getSettings()` liefert `Map<String,String>`, unverändert |
| B — typisierte öffentliche Felder: ein Lesefeld je Einstellung oder ein öffentliches Wertobjekt `EffectiveSettings` | Stärkste Form, Typ statt Test | Ein Lesefeld je Einstellung vergrößert die bereits flache Klasse `TelemetryEvent` um elf Mitglieder, von denen die meisten für andere Ereignisarten `null` sind; ein öffentliches Wertobjekt ist ein neuer Typname: Änderung der Liste in `SPEC-059`, Lauf von `make flaeche`, [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 verlangt die Aufnahme; jede neue Einstellung ändert die öffentliche Fläche | jede Einstellung ändert Fläche, Spezifikation und Adapter des Testservers (das `when` über Felder) | Getter je Einstellung oder ein Typ mit Gettern: gut lesbar, aber die Schlüssel werden Teil der Signatur (Bindung bis `1.0` nach Spezifikation) |
| C — `CONFIG_EFFECTIVE` nicht als Ereignis der Bibliothek, sondern Zeile des Testservers | Das Problem wandert in den Testserver: dort liest dieselbe Art Funktion dieselbe Konfiguration, ohne Modell, das sie begrenzt; die Zusage aus ADR-0020 gilt für „jeden Adapter“ und wäre für diese Information gegenstandslos | keine neue Fläche, aber `CONFIG_EFFECTIVE` und das Feld `settings` entfallen aus dem Modell; `ServerConfig` ist öffentlich, der Testserver kennt sie | wenig Bau; Embedder (Zweck der Bibliothek) erhalten das Ereignis nicht, obwohl [TDS-FA-09.a](../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose) die wirksame Konfiguration als Ereignis führt; die Spezifikation müsste zurückgenommen werden (Zielbild verengt) | entfällt |
| **D — Abbildung bleibt als Lesefeld, Ereignis entsteht nur aus einem internen typisierten Wertobjekt (gewählt)** | Geheimnisfreiheit folgt aus den Typen der Felder des Wertobjekts; der Konstruktor nimmt keinen freien Text; eine neue Einstellung mit Text- oder Byte-Typ fällt im Reflexionstest auf | kein neuer öffentlicher Name; `SPEC-059` und `make flaeche` unberührt | ein internes Objekt mehr und eine Ableitung an einer Stelle; neue Einstellung: ein Feld, ein Schlüssel | wie A: `Map<String,String>` |

## Konsequenzen

- Positiv: Die Zusage ist eine Eigenschaft der Typen am Bauort des Ereignisses; ein Leck braucht einen Typ, den das Wertobjekt nicht erlaubt. Die öffentliche Fläche bleibt gleich.
- Negativ: Die Schlüsselmenge der öffentlichen Abbildung ist weiter kein Typ; ein Embedder, der Schlüssel liest, hängt an der Spezifikation (Setzung). Das öffentliche Lesefeld hat weiter den Typ Text auf Text; die Zusage gilt, weil die Klasse keinen öffentlichen Konstruktor hat und nur die Bibliothek Ereignisse baut.
- Akzeptiertes Negativ: Die Reflexionsprüfung kennt nur die Feldtypen, nicht, ob ein `Int`-Feld semantisch ein Geheimnis ist (ein Zahlenfeld mit einer Prüfsumme des Passworts); das fängt der Review der Typen, nicht ein Test. Der Fall ist einmalig und unwahrscheinlich und hat keine eigene Folgepflicht.
- Folgepflicht: Umsetzung der fünf Punkte oben im laufenden Zweig; keine weitere Fitness Function über die unten genannten Tests hinaus.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Unit-Test (Reflexion) | Felder von `EffectiveSettings` sind nur Zahlentypen, Aufzählungen, `Boolean`; der Konstruktor von `ConfigEffective` nimmt keinen Parameter vom Typ `Map` oder `String` | `make test` |
| Unit-Test (Marken) | Marken in Pfad, Passwort und Benutzerliste der Konfiguration kommen in keinem Wert von `settings` vor; rot bei Leck | `make test` |
| `flaeche` | keine neue Zeile in der Liste von `SPEC-059` (Beleg, dass D keinen Typnamen anlegt) | `make flaeche` |

## Re-Evaluierungs-Trigger

Ein Feld des Wertobjekts bräuchte einen Textwert, der nicht aus einer Aufzählung oder Konstante stammt; ein Embedder verlangt die Einstellungen als Typ statt als Abbildung (dann Option B mit Aufnahme in `SPEC-059`); `CONFIG_EFFECTIVE` bekommt einen zweiten Schreiber außer `Bootstrap`; ein Review findet einen weiteren Fall, in dem eine Abbildung von Text auf Text in das Modell tritt.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-07 | Proposed (Fund F-5 aus dem Review der Zustandsereignisse) | — |
