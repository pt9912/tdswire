# Matrix und Slice-Plan nennen verschiedene Stände, weil jede Fix-Runde nur eines der beiden Dokumente nachzieht

**Sub-Area:** `*` (Planung, Abdeckungs-Dokumente)

Ein von Hand gepflegtes Abdeckungs-Dokument nennt einen Stand (Commit, Testzahl), und der Slice-Plan nennt denselben Stand noch einmal im Fließtext. Die Fix-Runde setzt die Matrix neu auf und lässt den Satz im Plan stehen; danach widersprechen sich beide, und nur ein Leser mit beiden Dateien sieht es. Maßgeblich ist die Stand-Zeile der Matrix; der Plan-Text sollte sie nennen statt zu wiederholen.
