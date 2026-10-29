# E2E-Abdeckung der Stufen des Voll-E2E

Erzeugt von `make e2e-abdeckung` aus den Deklarationen, die jede Stufe von
`tools/harness/selbstpruefung.sh` an sich selbst trägt. Diese Datei ist eine **stabile
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
| — | Der Traeger der Commit-Kennung greift im frischen Klon, das Gate-Kommando laeuft dort gruen, und der Stop-Hook gibt im Default ein Turn-Ende ohne Commit frei, haelt es im strengen Modus auf und haelt einen Commit ohne Nachweis auf | Stufe 1 | `tools/harness/selbstpruefung.sh:154` |
