# Beim Kürzen einer Tabellenzelle auf ein Zeichenlimit gehen Angaben verloren, die der Slice nicht berührt

**Sub-Area:** `*` (Doku, `harness/README.md` §Sensors)

Die Zelle „Vertrag“ der Zeile `make skript-selbsttest` wurde für die neue Angabe gekürzt; fünf bestehende Angaben (`manifest-pruefung.sh (Jar, POM)`, `publish.sh (je Ziel)`, `vergleichslauf-d.sh: EULA, CI, Live`, `test-lauf.sh: Argumente, Modi`, `lasttest*.sh: Werte, Statistik`) fielen auf bloße Namen, obwohl 39 Zeichen Luft waren. Der Review fand es; nach der Behebung blieb eine Kürzung (`e2e.sh` bricht an roter Stufe wurde `e2e.sh: Abbruch`) und eine Angabe dieses Slice (`release-*.sh: ZIP, Lizenzdateien` wurde `release-*.sh`). Kein Sensor vergleicht die Zelle mit dem Stand vor der Änderung.
