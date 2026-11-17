#!/usr/bin/env bash
# tools/rueckstau-tabellen.sh - setzt aus der Rohzeilen-Datei der Rueckstau-Messung (TDS-QA-04, tools/rueckstau.sh) die Tabellen eines
# Messprotokolls (Markdown auf der Standardausgabe): Bedingungen aus den Zeilen `zelle:`, Leser, Blockierend, Abbruch, Import und
# Frist. Aufruf: bash tools/rueckstau-tabellen.sh <Rohzeilen-Datei>
# Eingabeform: die Zeilen `rueckstau:` und `zelle:` von tools/rueckstau.sh, je Zelle bis zur Zeile `zelle: <Name> ...` (sie schliesst die
# Zelle ab); andere Zeilen, auch mit `#`, werden uebergangen. Die Tabellen stehen in der Reihenfolge der Zellen der Datei; Zellen mit
# anderem Namen als leser-eigen-*, leser-sim-*, leser-frist, blk-*, abbruch-* und imp-* kommen nicht vor. Zahlen ab 10000 stehen mit
# Leerzeichen als Tausendertrenner, Dezimalzahlen mit Komma, Speicher in GiB als MiB durch 1024 (eine Stelle), die cgroup-Spitze in MiB
# durch 1048576. "Last vor -> nach" ist der Wert fuer 1 Minute. Eine Zelle ohne Zeile `zelle:` fehlt ohne Meldung. Eingabefehler (Datei
# fehlt, keine Zelle) enden mit Exit 1.
# Host: bash und awk (gawk, mawk). Gehalten von tools/rueckstau-tabellen-selbsttest.sh (Stage `skript-selbsttest`).
set -u
export LC_ALL=C
datei="${1:-}"
[ -f "$datei" ] || { echo "rueckstau-tabellen: Rohzeilen-Datei fehlt: '$datei'" >&2; exit 1; }
awk '
function feld(zeile, name,   n, t, i) {
	n = split(zeile, t, " ")
	for (i = 1; i < n; i++) if (t[i] == name) return t[i + 1]
	return ""
}
function trenn(zahl,   s, r) {
	s = sprintf("%d", zahl)
	if (zahl < 10000) return s
	r = ""
	while (length(s) > 3) { r = " " substr(s, length(s) - 2) r; s = substr(s, 1, length(s) - 3) }
	return s r
}
function komma(text) { gsub(/\./, ",", text); return text }
function eins(x) { return komma(sprintf("%.1f", x)) }
function klammer(zeile, wort,   s) {
	if (!match(zeile, wort " \\[[^]]*\\]")) return ""
	s = substr(zeile, RSTART, RLENGTH)
	sub(/^[a-z]+ \[/, "", s); sub(/\]$/, "", s)
	return s
}
function lastwert(inh,   t) { split(inh, t, " "); sub(/^last /, "", inh); split(inh, t, "/"); return t[1] }
function lastpaar(zeile,   v, n) {
	v = klammer(zeile, "vorher"); n = klammer(zeile, "nachher")
	return komma(lastwert(v)) " → " komma(lastwert(n))
}
function bereich(zeile,   w, i, k, inh, wert) {
	for (k = 1; k <= 2; k++) {
		inh = klammer(zeile, k == 1 ? "vorher" : "nachher")
		wert = lastwert(inh) + 0
		if (!(lmin != "") || wert < lmin) lmin = wert
		if (!(lmax != "") || wert > lmax) lmax = wert
		w = feld(inh, "frei_mb") + 0
		if (fmin == "" || w < fmin) fmin = w
		if (fmax == "" || w > fmax) fmax = w
		w = feld(inh, "swap_mb") + 0
		if (smin == "" || w < smin) smin = w
		if (smax == "" || w > smax) smax = w
	}
}
function fremd(zeile,   p, k, m) {
	p = feld(zeile, "proben"); k = feld(zeile, "ueber300"); m = feld(zeile, "max")
	sub(/%$/, "", m)
	return p ", " k ", " m " %"
}
function nachbar(zeile) {
	return feld(zeile, "n") "; " trenn(feld(zeile, "p50_us")) " / " trenn(feld(zeile, "p95_us")) " / " trenn(feld(zeile, "max_us"))
}
function grenzen(zeile, wort,   s, t) {
	if (!match(zeile, wort ": threads [0-9]+ offen [0-9]+")) return ""
	s = substr(zeile, RSTART, RLENGTH)
	split(s, t, " ")
	return t[3] "|" t[5]
}
function inhalt(zeile, marke,   s) {
	if (!match(zeile, marke " \x27[^\x27]*\x27")) return ""
	s = substr(zeile, RSTART, RLENGTH)
	sub("^" marke " \x27", "", s); sub(/\x27$/, "", s)
	return s
}
function zuruecksetzen() { aufbau = ""; callback = ""; peak = ""; delete z }
BEGIN { zuruecksetzen(); n = 0; lmin = ""; lmax = ""; fmin = ""; fmax = ""; smin = ""; smax = "" }
/^rueckstau: server aufbau=/ {
	aufbau = $3; sub(/^aufbau=/, "", aufbau)
	callback = feld($0, "callback"); if (callback == "") { match($0, /callback=[^ ]*/); callback = substr($0, RSTART + 9, RLENGTH - 9) }
	next
}
/^rueckstau: server peak / { peak = $4; next }
/^rueckstau: leser n / { z["leser"] = $0; next }
/^rueckstau: blockierend modus [a-z-]+ ohne_last / { z["ohne"] = $0; next }
/^rueckstau: blockierend modus [a-z-]+ mit_last / { z["mit"] = $0; next }
/^rueckstau: blockierend modus [a-z-]+ n / { z["blk"] = $0; next }
/^rueckstau: abbruch modus [a-z-]+ mit_last / { z["mit"] = $0; next }
/^rueckstau: abbruch modus [a-z-]+ block_ms / { z["abbruch"] = $0; next }
/^rueckstau: import mit_last / { z["mit"] = $0; next }
/^rueckstau: import n / { z["imp"] = $0; next }
/^zelle: / {
	name = $2
	n++
	zn[n] = name; za[n] = aufbau; zc[n] = callback; zp[n] = peak; zz[n] = $0
	for (k in z) zd[n, k] = z[k]
	bereich($0)
	if (feld($0, "ueber300") + 0 > 0) mehr = mehr (mehr == "" ? "" : ", ") name " (" feld($0, "ueber300") " von " feld($0, "proben") ", " substr(feld($0, "max"), 1, length(feld($0, "max")) - 1) " %)"
	zuruecksetzen()
}
END {
	if (n == 0) { print "rueckstau-tabellen: keine Zelle in der Eingabe" > "/dev/stderr"; exit 1 }
	print "### Bedingungen aus den Zeilen zelle:\n"
	print "| Angabe | Wert |\n|---|---|"
	print "| Zellen | " n " |"
	print "| Last 1 min vor und nach den Zellen | " komma(sprintf("%.2f", lmin)) " bis " komma(sprintf("%.2f", lmax)) " |"
	print "| Verfügbarer Speicher vor und nach den Zellen (GiB) | " eins(fmin / 1024) " bis " eins(fmax / 1024) " |"
	print "| Swap des Hosts belegt (GiB) | " eins(smin / 1024) " bis " eins(smax / 1024) " |"
	print "| Zellen mit Proben über 300 % fremder CPU | " (mehr == "" ? "keine" : mehr) " |"

	print "\n### Leser\n"
	print "| Zelle | Aufbau | Sitzungen | langsam | Vorlauf je langsamem Leser (Zeilen) | Zuwachs nach 1,5 s | Basis Zeilen/s | mit langsamem Leser Zeilen/s | Heap benutzt (Stichprobe) / nach GC MB | cgroup-Spitze MiB | Last 1 min vor → nach | Fremdlast (Proben, davon > 300 %, Höchstwert) |"
	print "|---|---|---|---|---|---|---|---|---|---|---|---|"
	for (i = 1; i <= n; i++) {
		if (zn[i] !~ /^leser-(eigen|sim)-/) continue
		l = zd[i, "leser"]
		basis = feld(l, "basis_zeilen_je_s") + 0; mit = feld(l, "schnell_zeilen_je_s") + 0
		printf "| %s | %s | %s | %s | %s%s | %s | %s | %s | %s / %s | %s | %s | %s |\n", zn[i], za[i], feld(l, "n"), feld(l, "langsam"), \
			trenn(feld(l, "vorlauf_je_langsam")), (feld(l, "langsam") + 0 > 1 ? " (Mittel)" : ""), feld(l, "plateau_zuwachs"), \
			(basis < 0 ? "–" : trenn(basis)), (mit < 0 ? "–" : trenn(mit)), feld(l, "heap_benutzt_mb"), feld(l, "heap_nach_gc_mb"), \
			eins(zp[i] / 1048576), lastpaar(zz[i]), fremd(zz[i])
	}

	print "\n### Blockierend\n"
	print "| Zelle | Art | Sitzungen | Gesamt ms | Sitzung (Median) ms | spätester Start ms | Spitze der Threads | Nachbar ohne Last (n; p50 / p95 / max µs) | Nachbar mit Last (n; p50 / p95 / max µs) | Last 1 min vor → nach | Fremdlast (Proben, davon > 300 %, Höchstwert) |"
	print "|---|---|---|---|---|---|---|---|---|---|---|"
	for (i = 1; i <= n; i++) {
		if (zn[i] !~ /^blk-/) continue
		b = zd[i, "blk"]
		printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", zn[i], feld(b, "modus"), feld(b, "n"), trenn(feld(b, "gesamt_ms")), \
			trenn(feld(b, "sitzung_median_ms")), feld(b, "start_max_ms"), feld(b, "spitze_threads"), nachbar(zd[i, "ohne"]), nachbar(zd[i, "mit"]), \
			lastpaar(zz[i]), fremd(zz[i])
	}

	print "\n### Abbruch\n"
	print "| Zelle | Art | Cancel bis Ende (ms) | Ende beim Client | gelesen | offene Ströme direkt danach / nach der Wartezeit | Threads direkt danach | Nachbar (n; p50 / p95 / max µs) | Last 1 min vor → nach | Fremdlast (Proben, davon > 300 %, Höchstwert) |"
	print "|---|---|---|---|---|---|---|---|---|---|"
	for (i = 1; i <= n; i++) {
		if (zn[i] !~ /^abbruch-/) continue
		a = zd[i, "abbruch"]
		d = grenzen(a, "direkt_danach"); split(d, dd, "|"); e = grenzen(a, "nach_block"); split(e, ee, "|")
		printf "| %s | %s | %s | `%s` | %s | %s / %s | %s | %s | %s | %s |\n", zn[i], feld(a, "modus"), trenn(feld(a, "cancel_bis_ende_ms")), inhalt(a, "ende"), \
			feld(a, "gelesen"), dd[2], ee[2], dd[1], nachbar(zd[i, "mit"]), lastpaar(zz[i]), fremd(zz[i])
	}

	print "\n### Import\n"
	print "| Zelle | Sitzungen | Zeilen je Sitzung | Callback | Sender (ms je Zeile) | Gesamt ms | größter Vorlauf des Senders (Zeilen, Proben) | Nachbar (n; p50 / p95 / max µs) | Spitze der Threads | Heap nach GC MB | Last 1 min vor → nach | Fremdlast (Proben, davon > 300 %, Höchstwert) |"
	print "|---|---|---|---|---|---|---|---|---|---|---|---|"
	for (i = 1; i <= n; i++) {
		if (zn[i] !~ /^imp-/) continue
		m = zd[i, "imp"]; c = zc[i]; split(c, cc, "/")
		cb = (cc[2] == "0ms" ? "ohne Warten" : cc[1] " " cc[2])
		pr = feld(m, "proben")
		printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", zn[i], feld(m, "n"), trenn(feld(m, "zeilen")), cb, feld(m, "sender_ms_je_zeile"), \
			trenn(feld(m, "gesamt_ms")), (pr + 0 == 0 ? "keine Probe (0)" : trenn(feld(m, "max_vorlauf_zeilen")) " (" pr ")"), nachbar(zd[i, "mit"]), \
			feld(m, "spitze_threads"), feld(m, "heap_nach_gc_mb"), lastpaar(zz[i]), fremd(zz[i])
	}

	print "\n### Frist\n"
	print "| Zelle | Weiterlesen ms | Ende beim langsamen Leser | abgebrochen_delta | offen_ende | Last 1 min vor → nach | Fremdlast (Proben, davon > 300 %, Höchstwert) |"
	print "|---|---|---|---|---|---|---|"
	for (i = 1; i <= n; i++) {
		if (zn[i] != "leser-frist") continue
		l = zd[i, "leser"]
		printf "| %s | %s | `%s` | %s | %s | %s | %s |\n", zn[i], feld(l, "weiterlesen_ms"), inhalt(l, "frist_ende"), feld(l, "abgebrochen_delta"), \
			feld(l, "offen_ende"), lastpaar(zz[i]), fremd(zz[i])
	}
}
' "$datei"
