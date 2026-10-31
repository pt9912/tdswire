# E2E-Abdeckung der Stufen des Voll-E2E

Erzeugt von `make e2e-abdeckung` aus den Deklarationen, die jede Stufe von
`tools/e2e.sh` an sich selbst trägt. Diese Datei ist eine **stabile
Abdeckungs-Deklaration, kein Lauf-Beleg**: der Erzeuger liest den Quelltext der Stufen,
er führt den E2E nicht aus, und er schreibt sie nur bei inhaltlicher Abweichung. Sie
ändert sich mit den Deklarationen und mit dem Ort ihrer Quellen — eine Einfügung
oberhalb einer Stufe verschiebt deren Zeile in der Spalte `Ort`.

Die Spalte `Spec-Kennung` nennt die Anforderung, die diese Stufe trägt, **als
Code-Span statt als Verweis**: ein Verweis brauchte einen Anker, und den müsste dieser
Erzeuger aus der Rohzeile einer Überschrift nachbilden — ein Link, der aussieht wie
einer und im Doku-Gate rot wird, wäre teurer als gar keiner. Gehalten wird die Sicht
deshalb gegen `spec/lastenheft.md`, und zwar vom Leser. Die Spalte
`Kurzbeschreibung` trägt keine Kennung. Die Spalte `Stufe` zählt die
Stufen-Kopfzeilen in der Reihenfolge des Skripts — eine Stufe eröffnet mit ihrer
Ausgabe-Kopfzeile und reicht bis zur nächsten. Die Spalte `Ort` adressiert sie.
Ob eine hier fehlende Anforderung eine Lücke ist, urteilt ebenfalls der Leser; ein
Waisen-Urteil fällt nicht hier.

| Spec-Kennung | Kurzbeschreibung | Stufe | Ort |
| --- | --- | --- | --- |
| `TDS-FA-01`, `TDS-FA-02`, `TDS-FA-09` | Kindprozess main mit Rohbytes-Client: PRELOGIN und LOGIN7 mit LOGINACK, Start ohne JVM-Flag bricht ab, fehlende Pflichtoption, ungültiger LOGIN_TIMEOUT und belegter Port beenden den Start mit Exit-Code 1 und der erwarteten Diagnosezeile (Wortlaut geprüft, ohne Wert der Option). Nicht belegt: Referenzclient, TLS, Anfragepfad, beschädigtes oder unzulässiges PRELOGIN (FA-01 Negative), abweichende Clientoption (FA-01 Boundary), protokollierte Zustandsereignisse bei gültigem Start (FA-09 Happy Path), ungültige sicherheitsrelevante Konfiguration über die geprüften Fälle hinaus (FA-09 Negative) | Stufe 1 | `tools/e2e.sh:35` |
| `TDS-RB-01`, `TDS-QA-06`, `TDS-QA-03` | Image startet, Login mit TEST_UNENCRYPTED aus zweitem Container gelingt, docker stop endet mit Exit-Code 143, die Ausgabe nennt weder Benutzer noch Passwort. Nicht belegt: TLS-Modus, Fehlpfade, Clean-Build in der CI (QA-06 nur der Docker-Teil) | Stufe 2 | `tools/e2e.sh:39` |
| `TDS-FA-11`, `TDS-FA-03`, `TDS-FA-06` | Microsoft.Data.SqlClient gegen den Serverprozess mit Einstieg runServer im Test-Quelltext, nicht main: Auswahl mit Bedingung, leeres Ergebnis, Zeilenquelle über dem Heap, Fehler der Zeilenquelle als SqlException mit nutzbarer Verbindung, ohne Parameter, TLS und Pool. Nicht belegt: Einfüge-, Änderungs- und Löschanweisungen, parametrisierte Abfrage, Cancel, Anweisung außerhalb der erkannten Formen | Stufe 3 | `tools/e2e.sh:43` |
| `TDS-RB-02` | Das versionierte Testprojekt baut gesperrt und läuft, der Smoke-Test verbindet zu einem unbelegten Port und erhält eine SqlException. Nicht belegt: irgendein Serververhalten, kein Serverprozess läuft | Stufe 4 | `tools/e2e.sh:47` |
| `TDS-FA-01`, `TDS-FA-02`, `TDS-FA-03`, `TDS-FA-04`, `TDS-FA-05`, `TDS-FA-06`, `TDS-QA-01`, `TDS-RB-02` | Microsoft.Data.SqlClient 7.1.1 in einem zweiten Container gegen den Serverprozess mit Einstieg runServer im Test-Quelltext (Richtlinie REQUIRED), TDS 7.4 mit TLS, Kette und Name geprüft: Anmeldung, gleiche Ablehnung bei falschem Kennwort und unbekanntem Benutzer, Auswahl im Batch mit Bedingung, Stern, NULL und neun Typen des Mindestkatalogs, parametrisierte Abfrage (Int, Text) und Einfügen mit NULL-Parameter, Einfügen, Ändern und Löschen mit Zeilenzahl, Fehler vor Zeilen (Verbindung geschlossen), außerhalb der Formen und nach Zeilen (Verbindung nutzbar); gemessen als Abweisung: zwei Anweisungen in einem Text, InputOutput-Parameter ohne Rückgabewert. Nicht belegt: mehrteilige Antworten und Ausgabewerte (das Simulations-Backend liefert sie nicht), Cancel (CO-004), andere Typen des Clients, ein echter SQL Server | Stufe 5 | `tools/e2e.sh:51` |
