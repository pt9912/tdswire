#!/usr/bin/env bash
# tools/heap-abschluss-tabellen.sh - setzt aus der Rohzeilen-Datei der Heap- und Abschluss-Messung (TDS-QA-04, tools/heap-abschluss.sh) die
# Tabellen eines Messprotokolls (Markdown auf der Standardausgabe): Bedingungen aus den Zeilen `zelle:`, Heap (eine Zeile je Lauf) und
# Abschluss (eine Zeile je Form und Verzoegerung ueber die Wiederholungen 1 bis n). Aufruf: bash tools/heap-abschluss-tabellen.sh <Rohzeilen-Datei>
# Eingabeform: die Zeilen `heapabschluss:`, `heap:`, `abschluss:` und `zelle:` von tools/heap-abschluss.sh, je Zelle bis zur Zeile
# `zelle: <Name> ...` (sie schliesst die Zelle ab; ihr Name steht in jeder Zeile der Tabelle); andere Zeilen, auch mit `#`, werden uebergangen.
# Die Tabellen stehen in der Reihenfolge der Datei; Zellen mit anderem Namen als heap-* und abschluss kommen nicht vor. Zahlen ab 10000 stehen
# mit Leerzeichen als Tausendertrenner, Dezimalzahlen mit Komma, Speicher in GiB als MiB durch 1024 (eine Stelle), Heap und cgroup-Spitze in MiB
# (KiB durch 1024, eine Stelle; Byte durch 1048576). "Last vor -> nach" ist der Wert fuer 1 Minute. Die Wiederholung `wdh 0` des Abschluss-Clients
# ist der Warmlauf und geht in keine Zahl ein. Median ist der Wert an Stelle n / 2 + 1 der sortierten Werte (abgerundet; bei geradem n der obere).
# Eine Zelle ohne Zeile `zelle:` fehlt ohne Meldung. Eingabefehler (Datei fehlt, keine Zelle) enden mit Exit 1.
# Host: bash und awk (gawk, mawk). Gehalten von tools/heap-abschluss-tabellen-selbsttest.sh (Stage `skript-selbsttest`).
set -u
export LC_ALL=C
datei="${1:-}"
[ -f "$datei" ] || { echo "heap-abschluss-tabellen: Rohzeilen-Datei fehlt: '$datei'" >&2; exit 1; }
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
function lastwert(inh,   t) { sub(/^last /, "", inh); split(inh, t, "/"); return t[1] }
function lastpaar(zeile,   v, n) {
	v = klammer(zeile, "vorher"); n = klammer(zeile, "nachher")
	return komma(lastwert(v)) " → " komma(lastwert(n))
}
function bereich(zeile,   w, k, inh, wert) {
	for (k = 1; k <= 2; k++) {
		inh = klammer(zeile, k == 1 ? "vorher" : "nachher")
		wert = lastwert(inh) + 0
		if (lmin == "" || wert < lmin) lmin = wert
		if (lmax == "" || wert > lmax) lmax = wert
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
# Werte der Reihe <schluessel> (1 bis n): Median, Minimum, Maximum
function sortiert(schluessel, n,   i, j, t) {
	for (i = 1; i <= n; i++) tmp[i] = v[schluessel, i]
	for (i = 2; i <= n; i++) { t = tmp[i]; for (j = i - 1; j >= 1 && tmp[j] > t; j--) tmp[j + 1] = tmp[j]; tmp[j + 1] = t }
}
function dreier(schluessel, n, teiler, ganz,   m, lo, hi) {
	sortiert(schluessel, n)
	m = tmp[int(n / 2) + 1]; lo = tmp[1]; hi = tmp[n]
	if (ganz) return trenn(m / teiler) " (" trenn(lo / teiler) " bis " trenn(hi / teiler) ")"
	return eins(m / teiler) " (" eins(lo / teiler) " bis " eins(hi / teiler) ")"
}
BEGIN { n = 0; nr = 0; nk = 0; lmin = ""; lmax = ""; fmin = ""; fmax = ""; smin = ""; smax = ""; aufbau = ""; peak = ""; npending = 0 }
/^heapabschluss: server aufbau=/ { aufbau = $3; sub(/^aufbau=/, "", aufbau); next }
/^heapabschluss: server peak / { peak = $4; next }
/^heap: lauf / { pending[++npending] = $0; next }
/^abschluss: lauf / { pending[++npending] = $0; next }
/^zelle: / {
	name = $2
	n++
	zn[n] = name; za[n] = aufbau; zp[n] = peak; zz[n] = $0
	for (i = 1; i <= npending; i++) { nr++; rz[nr] = n; rl[nr] = pending[i] }
	npending = 0; aufbau = ""; peak = ""
	bereich($0)
	if (feld($0, "ueber300") + 0 > 0) mehr = mehr (mehr == "" ? "" : ", ") name " (" feld($0, "ueber300") " von " feld($0, "proben") ", " substr(feld($0, "max"), 1, length(feld($0, "max")) - 1) " %)"
}
END {
	if (n == 0) { print "heap-abschluss-tabellen: keine Zelle in der Eingabe" > "/dev/stderr"; exit 1 }
	print "### Bedingungen aus den Zeilen zelle:\n"
	print "| Angabe | Wert |\n|---|---|"
	print "| Zellen | " n " |"
	print "| Last 1 min vor und nach den Zellen | " komma(sprintf("%.2f", lmin)) " bis " komma(sprintf("%.2f", lmax)) " |"
	print "| Verfügbarer Speicher vor und nach den Zellen (GiB) | " eins(fmin / 1024) " bis " eins(fmax / 1024) " |"
	print "| Swap des Hosts belegt (GiB) | " eins(smin / 1024) " bis " eins(smax / 1024) " |"
	print "| Zellen mit Proben über 300 % fremder CPU | " (mehr == "" ? "keine" : mehr) " |"

	print "\n### Heap\n"
	print "| Zelle (Ende UTC) | Aufbau | Sitzungen | Zeilen je Sitzung | Wiederholung | Dauer s | Zeilen/s gesamt | Stichproben (offen) | Heap Minimum MiB | Median MiB | 95. Rang MiB | Maximum MiB | Leerlauf davor (Maximum) MiB | GC-Zeit s | cgroup-Spitze MiB | Last 1 min vor → nach | Fremdlast (Proben, davon > 300 %, Höchstwert) |"
	print "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"
	for (r = 1; r <= nr; r++) {
		if (rl[r] !~ /^heap: lauf /) continue
		i = rz[r]; l = rl[r]; split(zz[i], zt, " ")
		printf "| %s (%s) | %s | %s | %s | %s von %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", zn[i], substr(zt[3], 12, 5), za[i], feld(l, "n"), trenn(feld(l, "zeilen")), \
			feld(l, "wdh"), feld(l, "von"), eins(feld(l, "dauer_ms") / 1000), trenn(feld(l, "zeilen_je_s_gesamt")), feld(l, "proben_offen"), \
			eins(feld(l, "min_kb") / 1024), eins(feld(l, "median_kb") / 1024), eins(feld(l, "p95_kb") / 1024), eins(feld(l, "max_kb") / 1024), \
			eins(feld(l, "leerlauf_max_kb") / 1024), eins(feld(l, "gc_ms") / 1000), eins(zp[i] / 1048576), lastpaar(zz[i]), fremd(zz[i])
	}

	print "\n### Abschluss\n"
	print "| Form | Verzögerung ms | Läufe | Zeilen | Pakete | Nutzbytes des 1. Pakets | letzte Zeile und DONE im selben Paket | DONE_MORE | letzte Zeile → DONE am Client µs | Zeile emittiert → Zeile am Client µs | Zeile emittiert → DONE am Client µs | nächstes Ereignis emittiert → DONE am Client µs | Anfrage → DONE µs | Last 1 min vor → nach | Fremdlast (Proben, davon > 300 %, Höchstwert) |"
	print "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"
	for (r = 1; r <= nr; r++) {
		l = rl[r]
		if (l !~ /^abschluss: lauf / || feld(l, "wdh") + 0 == 0) continue
		key = feld(l, "form") "|" feld(l, "verzoegerung_ms") "|" rz[r]
		if (!(key in kn)) { nk++; korder[nk] = key; kn[key] = 0; kz[key] = rz[r]; kzeilen[key] = feld(l, "zeilen"); kform[key] = feld(l, "form"); kd[key] = feld(l, "verzoegerung_ms"); kpakete[key] = feld(l, "pakete"); kbytes[key] = feld(l, "paket_nutzbytes"); kmore[key] = feld(l, "more_bit") }
		k = ++kn[key]
		v[key "c", k] = feld(l, "zeile_bis_done_client_us") + 0
		v[key "z", k] = feld(l, "zeile_emit_bis_zeile_gesehen_us") + 0
		v[key "d", k] = feld(l, "zeile_emit_bis_done_gesehen_us") + 0
		v[key "n", k] = feld(l, "naechstes_bis_done_gesehen_us") + 0
		v[key "a", k] = feld(l, "anfrage_bis_done_us") + 0
		if (feld(l, "zeile_und_done_im_selben_paket") == "true") ksel[key]++
	}
	for (o = 1; o <= nk; o++) {
		key = korder[o]; m = kn[key]; i = kz[key]
		printf "| %s | %s | %s | %s | %s | %s | %s von %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", kform[key], kd[key], m, trenn(kzeilen[key]), kpakete[key], kbytes[key], ksel[key] + 0, m, kmore[key], \
			dreier(key "c", m, 1, 1), dreier(key "z", m, 1, 1), dreier(key "d", m, 1, 1), dreier(key "n", m, 1, 1), dreier(key "a", m, 1, 1), lastpaar(zz[i]), fremd(zz[i])
	}
}
' "$datei"
