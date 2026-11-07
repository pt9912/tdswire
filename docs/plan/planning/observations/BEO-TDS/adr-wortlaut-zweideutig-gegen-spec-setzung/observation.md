# Der Wortlaut einer angenommenen ADR lässt zwei Lesarten zu, der Bau wählt eine und die Spezifikation führt sie als Setzung

**Sub-Area:** `*` (ADRs, Spezifikation, Bau)

Eine angenommene ADR nennt einen Geltungsbereich in einer Aufzählung (Entscheidung 1) und sagt an anderer Stelle, sie ändere eine Trennlinie nicht (Entscheidung 5). Der Bau wählt die eine Lesart, die Spezifikation führt sie als „Setzung, nicht gemessen“, und erst der Review findet den Widerspruch zum Wortlaut der ADR. Weil die ADR nach `Accepted` unveränderlich ist, braucht die Auflösung eine Folge-ADR; bis dahin steht die Abweichung ohne Entscheidung. Prüfpunkt beim Schreiben einer Spec-Setzung zu einem ADR-Thema: die Setzung gegen jeden Satz der ADR lesen, nicht nur gegen die Aufzählung, und eine Zweideutigkeit vor dem Bau als Frage an den Architect melden.
