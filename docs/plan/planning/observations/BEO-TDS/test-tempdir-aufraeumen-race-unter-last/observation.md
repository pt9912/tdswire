# Ein Test mit Temp-Verzeichnis war unter Last einmal rot (Aufräumen des Verzeichnisbaums)

**Sub-Area:** `*` (Tests)

`SimulationWiringTest > ein Symlink …` meldete in einem Mutationslauf unter paralleler Last „rootDir must be verified to be directory beforehand“ aus `FileTreeWalk` beim Aufräumen des Temp-Verzeichnisses; in neun weiteren Läufen war er grün. Die Ursache ist nicht erfasst, ein Wettlauf zwischen Aufräumen und Anlegen ist die Vermutung. Verwandt, aber eigener Mechanismus: `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe` (dort fehlt die Ausgabe, hier liegt sie vor).
