# Die committete Abdeckungsmatrix veraltet, ohne dass ein Sensor es bemerkt

**Sub-Area:** `*` (`docs/plan/spec-abdeckung.md`, `make spec-abdeckung`)

Die Matrix wird nur durch einen Lauf von `make spec-abdeckung` aktuell; sie ist kein Gate und nennt weder Commit noch Lauf. Jeder Slice, der Tests mit `@spec`-Tag ändert oder hinzufügt, lässt die committete Fassung hinter dem Bestand zurück, und nichts im Repo meldet das. Aufgefallen ist es im Review (10/8/33 gegen frisch 14/6/31), in der Verifikation und bei der Closure (eine Zählung von `SPEC-051` nach dem Merge anderer Slices).
