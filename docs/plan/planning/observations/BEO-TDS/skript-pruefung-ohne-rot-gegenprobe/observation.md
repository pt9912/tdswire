# Eine `grep`-Prüfung der Laufskripte hat keinen Rot-Fall im Selbsttest, oder eine reine Negativprüfung steht ohne Positivprobe

**Sub-Area:** `*` (Skripte, E2E)

Die Prüfung eines Laufskripts liest das Log mit einem `grep`-Muster, und kein Sensor hält, dass sie rot wird, wenn die Aussage nicht gilt: (a) Muster ohne Rot-Fall im Selbsttest (Review F-6: `anfrage_fehler()`, `benutzer_nur_im_principal()`); (b) reine `! grep`-Prüfungen, die bei leerem Log grün bleiben, in Stufen, die die Kennung `TDS-FA-09` tragen (Review F-5). Behoben im Slice durch `tools/log-muster.sh` und `tools/log-muster-selbsttest.sh` (Stage `skript-selbsttest`, acht Mutationen der Muster) und durch Positivproben. Rest: die Beispielzeilen des Selbsttests sind von Hand geschrieben, nicht aus einem Lauf; dass der Adapter diese Form schreibt, hält nur `TestserverLogProcessTest`. Verwandt, aber andere Klasse: `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe`.
