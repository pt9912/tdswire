# Der Mutationsbeleg des Implementers steht in keiner Datei des Repos

**Sub-Area:** `*` (Verfahren, Closure)

Nach `harness/README.md` §Minimal agent workflow fährt der Implementer die Mutation je Zusage über enge Testklassen; Reviewer und Verifier fahren sie nicht (Zuständigkeit, Messsperre). Der Bericht des Implementers steht in keiner Datei des Repos: der Review kann die Mutationen nur im Kopf prüfen und „nicht nachvollziehen“, die Closure kann den Beleg nur als Bericht an den Auftraggeber zitieren, und eine Mutation, die nur als Log im Scratchpad liegt, ist nach dem Lauf nicht mehr nachprüfbar. Prüfpunkt: je Zusage steht Mutation, Testfall und Ergebnis (rot, grün) in einer versionierten Datei (Slice §7 oder Anhang des Review-Reports), sonst ist die Mutation für die Closure „nicht belegt“. Ob das eine Regel, ein Sensor oder eine Hinnahme wird, entscheidet der Nutzer.
