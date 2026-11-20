**Stand:** verkörpert — `.harness/skills/reviewer.md` (MEDIUM-Liste), Anker `seit welle-transport-tls`; Nachtrag in `welle-transport-tls-results.md` §Steering-Loop-Einträge


**Wiederauftreten (slice-qa-performance-durchsatz-sitzungen):** Review F-8: drei Teile des neuen Eigencodes in `tools/lasttest.sh` ohne Test, der bei Entfernung rot wird; der Reviewer fand sie per Mutation. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.


**Wiederauftreten (slice-qa-performance-rueckstau):** Review F-1 (ein Test, der nie lief, weil er einen Wert zurückgab) und F-5 (Mutation `delay` statt `Thread.sleep` blieb grün); der Reviewer fand es per Mutation. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.

**Wiederauftreten (slice-qa-performance-heap-abschluss):** Review F-1, F-2, Re-Review N-1, Verifikation V-1: eine Formel, ein ungetesteter Aufbau (neun von 18 Zellen) und ein Aufruf ohne rot gesehenen Test; der Aufruf (N-1) bleibt offen, die Aussage darauf ist eingeschränkt. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.

**Wiederauftreten (slice-simulation-mehrere-anweisungen-und-ausgabewert):** Review F-7: zwei Zweige des neuen Eigencodes ohne Test, der bei Entfernung rot wird; der Reviewer fand es per Mutation im Kopf. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.

**Wiederauftreten (slice-simulation-spaltenlaenge-in-auswahl):** Re-Review R-6 (INFO): `UPDATE` mit mehreren Werten in `SET` und die Zählung in UTF-16-Einheiten hatten keinen Test, der bei der Mutation rot wird; der Reviewer fand es per Mutation, der Re-Review 2 sah Mut-19 und Mut-20 rot. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.

**Wiederauftreten (slice-cancel-antwort-zwei-nachrichten-angleichen):** Review F-3: drei Fehlerzweige des neuen Codes ohne unterscheidenden Test (R11, R12, R13 grün); der Reviewer fand sie per Mutation, `PairConclusionTest` schloss sie. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.
