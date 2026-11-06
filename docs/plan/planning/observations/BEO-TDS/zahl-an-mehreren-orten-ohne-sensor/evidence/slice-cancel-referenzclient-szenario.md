**Vorgang:** slice-cancel-referenzclient-szenario
**Fund:** Review F-7: „50 Zeilen vor dem Abbruch“, „2 s“ und „5 s“ stehen in Test-Konstanten, SPEC-040, `tools/e2e.sh`, `tools/kompatibilitaetstest.sh` und Matrix; nur für die 5 s steht ein Kopplungskommentar auf beiden Seiten, `make kopplung-zahlen` deckt Heap und Text von `gross`, nicht diese Zahlen.
