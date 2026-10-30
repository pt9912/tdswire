# Ein Vertragstest belegt nur das Test-Backend, nicht den Port

**Sub-Area:** `*` (Tests, Verifikation)

Ein Test, der als Vertragstest geführt wird, konstruiert sein Backend fest und gibt ein Skript zurück, das der Fake unverändert ausliefert; es gibt keine Verhaltensänderung am Produktionscode, die ihn rot macht (Mutation am Fake rot, am Port nur die Form). Er belegt dann, dass der Port die Ereignisfolge ausdrücken lässt, nicht, dass ein Backend sie einhält. Auch eine Zählung über eine selbst gebaute Liste (`assertEquals(7, ...)`) ist gültig, egal was der Code tut. Verwandt, aber eigener Mechanismus: `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (dort variiert die Eingabe nicht, hier ist die geprüfte Stelle der Fake).
