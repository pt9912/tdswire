# Eine Alternation unter einem unbegrenzten `+` in einem Regex rekursiert je Zeichen (JVM `StackOverflowError`)

**Sub-Area:** `*` (Simulations-Backend, Erkennung von Anweisungsformen)

`(?:[^\]']|\]\]|'')+` in der Erkennung der Probe der Zieltabelle (`BulkCopyForms`) rekursierte je Zeichen des Namens: ab etwa 3000 Zeichen warf `recognize` einen `StackOverflowError`, der als `Error` nicht gefangen wird. Die besitzende Wiederholung `(?:[^\]']++|\]\]|'')++` ist linear und ändert die erkannte Menge nicht (Differentialtest, 40 000 Fälle, im Verifikationsbericht). Gesehen im Review `d4331dc4` (F-1), behoben in `82f62eae`, gehalten durch den Test mit langen Namen (Mutation R rot).
