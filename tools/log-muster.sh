# tools/log-muster.sh - die Muster, mit denen die Laufskripte das Log des Testservers lesen (TDS-FA-09, TDS-QA-03, ADR-0020).
# Wird mit `.` eingelesen von tools/kompatibilitaetstest.sh, tools/simulationstest.sh, tools/laufzeittest.sh und
# tools/eigenes-backend-test.sh; tools/log-muster-selbsttest.sh haelt jedes Muster an Beispielzeilen und an Mutationen der
# Muster gruen und rot (Stage `skript-selbsttest`). Jede Funktion liest das Log auf der Standardeingabe und endet mit 0,
# wenn die Aussage gilt. Das Format einer Zeile (Feldnamen `event`, `error_code`, `cause`, `reason`, `principal`, `tables`
# mit Unterstrich, kein Leerzeichen nach `:`) ist Setzung des Adapters (testserver/kotlin/.../TelemetryJson.kt) und steht in
# docs/user/referenz-ereignisse.md (Abschnitt "Log des Testservers"); die Muster lesen es literal.

# log_nennt_ereignis <event> [<Fragment>]: mindestens eine Zeile des Ereignisses, mit <Fragment> (literal, etwa `"tables":5`).
log_nennt_ereignis() {
  grep -F "\"event\":\"$1\"" | grep -qF -- "${2:-\"event\":\"$1\"}"
}

# log_ohne_ereignis <event|event|...>: keine Zeile eines der Ereignisse (erweiterter regulaerer Ausdruck).
log_ohne_ereignis() {
  ! grep -Eq "\"event\":\"($1)\""
}

# log_ohne_text <ausdruck>: keine Zeile passt auf den erweiterten regulaeren Ausdruck.
log_ohne_text() {
  ! grep -Eq -- "$1"
}

# log_text_nur_im_ereignis <text> <event>: der Text steht in keiner Zeile, die nicht zu diesem Ereignis gehoert.
log_text_nur_im_ereignis() {
  ! grep -v "\"event\":\"$2\"" | grep -q -- "$1"
}

# log_zaehle_fehler <error_code> <cause>: Zahl der Zeilen `request_failed` mit diesem Code und dieser Ursache (auf der
# Standardausgabe; Exit 0 nur, wenn mindestens eine).
log_zaehle_fehler() {
  grep -F '"event":"request_failed"' | grep -F "\"error_code\":\"$1\"" | grep -cF "\"cause\":\"$2\""
}
