# Ein roter Lauf nennt den Testfall nicht, der Flake bleibt unbenannt und unreproduziert

**Sub-Area:** `*` (Werkzeug, Tests)

Ein Werkzeug-Target (`make matrix-zitate`) zeigte bei roter Stage `test` nur die letzten 40 Zeilen des Berichts; der einmalige rote Lauf (938 Tests, einer rot) ging ohne Namen des Testfalls verloren und ließ sich in neun Folgeläufen nicht reproduzieren. Die Behebung macht den nächsten roten Lauf lesbar (`tools/matrix-zitate-rot.sh`), den Flake behebt sie nicht, und das Skript hat keinen Selbsttest. Verwandt: `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` (dort ein Fix ohne reproduzierten Fehler, hier eine Diagnose ohne Fund). Prüfpunkt: ein Werkzeug, das rote Läufe zusammenfasst, nennt jeden roten Testfall mit Namen, und die Closure führt den Flake als unbenannt, nicht als behoben.
