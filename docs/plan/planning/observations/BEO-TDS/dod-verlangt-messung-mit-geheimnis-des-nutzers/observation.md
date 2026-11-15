# Ein Liefer-Punkt verlangt eine Messung, die ein Geheimnis des Nutzers braucht

**Sub-Area:** `*` (Planung, Slice-Schnitt)

Die DoD verlangt eine Messung mit einem Token, das nur der Nutzer hält (hier der Konsum aus GitHub Packages mit `read:packages`). Kein Agent darf oder kann sie ausführen; der Plan nennt den Aufruf des Nutzers, aber keinen Träger und keine Rückfallfrage. Folge: der Haken bleibt offen, die Closure hängt an einer Entscheidung des Nutzers über Streichung oder Folge-Slice. Der Schnitt gehört in die Planung: vorab fragen, ob die Messung gewollt ist, und den Liefer-Punkt entsprechend schneiden. Verwandt, eigener Mechanismus: `BEO-TDS/dod-nennt-nutzerentscheidung-als-lieferinhalt` (dort Entscheidung als Inhalt, hier Geheimnis als Voraussetzung der Messung).
