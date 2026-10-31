**Vorgang:** slice-release-artefakt-lizenz
**Fund:** Der neue `LizenzTest` erscheint im Testlauf nur mit `docker build --no-cache-filter test --target test .` frisch (PASSED in Review, Verifikation und Closure); ein Wiederholungslauf von `make test` ohne Änderung der Eingänge ist ein Cache-Treffer. Die Closure lief den frischen Lauf einmal (Exit 0, 89 s, 735 Zeilen PASSED).
