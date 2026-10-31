# Ein Mutant wird erkannt, aber nur an der Zeitüberschreitung

**Sub-Area:** `*` (Tests, Verifikation)

Ein Test soll prüfen, dass eine Schleife nach einem Ereignis nicht weiterliest. Liest sie im Mutanten doch weiter, wartet sie an einem erschöpften Drehbuch, und der Test fällt erst an der Zeitüberschreitung der Testhilfe; die Zusicherungen dahinter werden nie erreicht. Der Mutant ist erkannt, aber langsam und mit einer Meldung, die nicht sagt, was verletzt wurde. Eine Zusicherung vor dem Warten (ein Drehbuch, das beim Weiterlesen eine Ausnahme wirft statt zu warten) würde die Aussage belegen. Verwandt, aber eigener Mechanismus: `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` (dort fehlt die Untergrenze, hier die Zusicherung vor dem Warten).
