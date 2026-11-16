# Der Plan nennt für die Tests eine falsche SPEC-Kennung, und der Bau übernimmt sie

**Sub-Area:** `*` (Planung, Tests, Abdeckungsmatrix)

Die DoD eines Slice nennt eine `SPEC-<NNN>`, die den Gegenstand nicht trägt (hier `SPEC-028`, PRELOGIN, TLS und LOGIN7, für PLP und NBCROW im Codec; gemeint war `SPEC-003`). Der Implementer setzt `@spec SPEC-028` an die neuen Tests, und `docs/plan/spec-abdeckung.md` zählt sie als Beleg für eine Festlegung, die sie nicht belegen (132 auf 135). Unterschied zu `BEO-TDS/spec-tag-nennt-lastenheft-kennung`: dort ist das Tag kein `SPEC-*`, hier ein gültiges, aber falsches. Die Abdeckungsmatrix und das Gate sehen es nicht, nur der Review (F-3, MEDIUM).
