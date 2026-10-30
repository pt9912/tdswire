# Katalog und Lock werden von keinem Gate gegeneinander gehalten

**Sub-Area:** `*` (Transport, Build und Gates)

Die Version einer gepinnten Abhängigkeit steht in `gradle/libs.versions.toml` und im Lock. Ein von Hand geänderter Katalog ohne `make deps-lock` bleibt grün, weil die aufgelöste Version vom Lock bestimmt wird; der Pin-Test vergleicht nur den aufgelösten Jar-Namen mit dem Katalogwert.
