#!/usr/bin/env bash
# tools/lasttest-tabellen.sh - setzt aus einer Rohzeilen-Datei des Lasttests (TDS-QA-04, tools/lasttest.sh) die Tabellen eines Messprotokolls
# (Markdown auf der Standardausgabe): Export, Import, Punktabfrage unter Export- und Import-Last und Dauer je Schicht. Aufruf:
#   bash tools/lasttest-tabellen.sh <Rohzeilen-Datei> [<Stufenname> ...]
# Eingabeform: je Zelle ein Abschnitt `## <Stufe>-<Aufbau>-<Senke>-<Phase>-v<Versuch>` (Stufe z. B. `stufe8`, Aufbau B oder C, Senke
# mit oder ohne, Phase export, import, punkt-export oder punkt-import) mit den Zeilen `messung:` und `messung-span:` von tools/lasttest.sh
# und einer Zeile `Probe: <n> Proben, <m> über 300 %, Höchstwert <x> %` (die Probe der Fremdlast, ein Hilfsmittel ausserhalb des Repos,
# nicht reproduzierbar; eine andere `Probe:`-Zeile heisst "keine Angabe"). Andere Abschnitte und Zeilen werden uebergangen. Ohne Stufennamen
# gelten alle Abschnitte dieser Form, mit Stufennamen nur die genannten (zwei Wiederholungen derselben Stufe gehoeren nicht zusammen).
# Gelesen wird die Stufe (Zahl der Sitzungen) aus der Zeile `messung:`; die Tabellen stehen nach Sitzungen, Aufbau (B, C) und Senke (mit, ohne).
# Je Zelle gilt der erste Versuch mit hoechstens einer Probe ueber 300 %, sonst der letzte; "gestoert" heisst: kein Versuch dieser Art.
# Millisekunden und Zeilen/s stehen wie in der Zeile `messung:`, Mikrosekunden der Punktabfrage in Millisekunden mit zwei Stellen. Die
# Zeilen `messung-span:` stehen nur fuer Senke mit (ohne Senke gibt es keine Spans). Eingabefehler (Datei fehlt, keine Zelle) enden mit Exit 1.
# Host: bash und awk (gawk und mawk liefern dieselbe Ausgabe, gemessen an den Rohzeilen des Protokolls vom 2026-11-17). Grenzen: eine Zelle
# ohne Zeile `messung:` fehlt ohne Meldung, gleichnamige Abschnitte ueberschreiben sich, und "gestoert" bei mindestens zwei Proben ueber 300 % in
# allen Versuchen ist nur ueber "k. A." (Probe nicht zuzuordnen) belegt.
# Gehalten von tools/lasttest-tabellen-selbsttest.sh (Stage `skript-selbsttest`).
set -u
export LC_ALL=C
datei="${1:-}"
[ -f "$datei" ] || { echo "lasttest-tabellen: Rohzeilen-Datei fehlt: '$datei'" >&2; exit 1; }
shift
stufen=" $* "
awk -v stufen="$stufen" '
function feld(zeile, name,   n, t, i) {
	n = split(zeile, t, " ")
	for (i = 1; i < n; i++) if (t[i] == name) return t[i + 1]
	return ""
}
function nach(zeile, muster,   s) {
	s = zeile
	if (!match(s, muster)) return ""
	s = substr(s, RSTART, RLENGTH)
	sub(/^[^0-9]*/, "", s)
	return s
}
function ms(us) { return sprintf("%.2f", us / 1000) }
BEGIN { zellen = 0 }
/^## / {
	name = substr($0, 4); aktiv = 0
	n = split(name, p, "-")
	if (name ~ / / || n < 5 || (p[2] != "B" && p[2] != "C") || (p[3] != "mit" && p[3] != "ohne") || p[n] !~ /^v[0-9]+$/) next
	if (n == 5 && p[4] ~ /^(export|import)$/) phase = p[4]
	else if (n == 6 && p[4] == "punkt" && (p[5] == "export" || p[5] == "import")) phase = "punkt-" p[5]
	else next
	if (stufen != "  " && index(stufen, " " p[1] " ") == 0) next
	aktiv = 1
	zelle = p[1] "|" p[2] "|" p[3] "|" phase
	v = substr(p[n], 2) + 0
	if (!(zelle in anzahl)) { anzahl[zelle] = 0; ordnung[++zellen] = zelle }
	anzahl[zelle]++
	idx = zelle "|" v
	versuche[zelle, anzahl[zelle]] = v
	sitz[idx] = ""; probe_m[idx] = -1; probe_x[idx] = ""; nspan[idx] = 0
	cur = idx
	next
}
aktiv && /^messung: / {
	if (index($0, " richtung " phase " ") == 0) next
	zeile[cur] = $0
	sitz[cur] = feld($0, "sitzungen") + 0
	next
}
aktiv && /^messung-span: / { nspan[cur]++; span[cur, nspan[cur]] = $0; next }
aktiv && /^Probe: / {
	if ($0 ~ /^Probe: [0-9]+ Proben, [0-9]+ über 300 %, Höchstwert [0-9]+ %/) {
		s = $0; sub(/^.*über 300 %, Höchstwert /, "", s); sub(/ %.*$/, "", s); probe_x[cur] = s
		m = $0; sub(/^Probe: [0-9]+ Proben, /, "", m); sub(/ über.*$/, "", m); probe_m[cur] = m + 0
	}
	next
}
function waehle(zelle,   i, v, idx, best) {
	best = ""
	for (i = 1; i <= anzahl[zelle]; i++) {
		v = versuche[zelle, i]; idx = zelle "|" v
		if (probe_m[idx] >= 0 && probe_m[idx] < 2 && (idx in zeile)) { gewaehlt_nr = i; return idx }
	}
	for (i = anzahl[zelle]; i >= 1; i--) {
		idx = zelle "|" versuche[zelle, i]
		if (idx in zeile) { gewaehlt_nr = i; return idx }
	}
	return ""
}
function sauber(idx) { return probe_m[idx] >= 0 && probe_m[idx] < 2 }
function vorlast(z, w,   s) { s = z; if (!match(s, w " \\[last [0-9.]+")) return "?"; s = substr(s, RSTART, RLENGTH); sub(/^.*last /, "", s); return s }
function vermerk(z,   s) { s = z; sub(/^.*\]/, "", s); gsub(/^ +| +$/, "", s); return s == "" ? "-" : s }
function fremd(idx) { return probe_m[idx] >= 0 ? probe_m[idx] " / " probe_x[idx] " %" : "k. A." }
function versuch(zelle, idx) { return gewaehlt_nr " von " anzahl[zelle] (sauber(idx) ? "" : ", gestört") }
function sortiere_sitz(   i, j, t) {
	for (i = 2; i <= ns; i++) { t = sl[i]; for (j = i - 1; j >= 1 && sl[j] + 0 > t + 0; j--) sl[j + 1] = sl[j]; sl[j + 1] = t }
}
END {
	if (zellen == 0) { print "lasttest-tabellen: keine Zelle in der Eingabe" > "/dev/stderr"; exit 1 }
	for (i = 1; i <= zellen; i++) {
		z = ordnung[i]; idx = waehle(z)
		if (idx == "") continue
		s = sitz[idx]
		if (!(s in gesehen)) { gesehen[s] = 1; sl[++ns] = s }
	}
	sortiere_sitz()
	nph = split("export import punkt-export punkt-import", phn, " ")
	for (k = 1; k <= nph; k++) {
		ph = phn[k]
		print "\n### " ph "\n"
		if (ph ~ /^punkt/) {
			print "| Stufe | Aufbau | Senke | p50 ms (min–max) | p95 ms (min–max) | p99 ms (min–max) | Streuung p95 | Last 1 min vor/nach | Vermerk | Fremdlast, Probe außerhalb des Repos (Proben > 300 %, Höchstwert) | Versuch |"
			print "|---|---|---|---|---|---|---|---|---|---|---|"
		} else if (ph == "export") {
			print "| Stufe | Aufbau | Senke | Median ms | Min ms | Max ms | Zeilen/s gesamt | je Sitzung | erste Zeile ms (min–max) | Byte/Zeile | Streuung | Last 1 min vor/nach | Vermerk | Fremdlast, Probe außerhalb des Repos (Proben > 300 %, Höchstwert) | Versuch |"
			print "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"
		} else {
			print "| Stufe | Aufbau | Senke | Median ms | Min ms | Max ms | Zeilen/s gesamt | je Sitzung | Byte/Zeile | Streuung | Last 1 min vor/nach | Vermerk | Fremdlast, Probe außerhalb des Repos (Proben > 300 %, Höchstwert) | Versuch |"
			print "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"
		}
		for (si = 1; si <= ns; si++) for (ai = 1; ai <= 2; ai++) for (ei = 1; ei <= 2; ei++) {
			st = sl[si]; a = (ai == 1 ? "B" : "C"); se = (ei == 1 ? "mit" : "ohne")
			found = ""
			for (i = 1; i <= zellen; i++) {
				z = ordnung[i]; split(z, q, "|")
				if (q[2] == a && q[3] == se && q[4] == ph) { c = waehle(z); if (c != "" && sitz[c] == st) { found = z; fidx = c; fnr = gewaehlt_nr; break } }
			}
			if (found == "") continue
			gewaehlt_nr = fnr
			l = zeile[fidx]
			vl = vorlast(l, "vorher") " / " vorlast(l, "nachher")
			if (ph ~ /^punkt/) {
				printf "| %s | %s | %s | %s (%s–%s) | %s (%s–%s) | %s (%s–%s) | %s | %s | %s | %s | %s |\n", st, a, se, \
					ms(feld(l, "p50_us_median")), ms(feld(l, "p50_us_min")), ms(feld(l, "p50_us_max")), \
					ms(feld(l, "p95_us_median")), ms(feld(l, "p95_us_min")), ms(feld(l, "p95_us_max")), \
					ms(feld(l, "p99_us_median")), ms(feld(l, "p99_us_min")), ms(feld(l, "p99_us_max")), \
					feld(l, "streuung_p95"), vl, vermerk(l), fremd(fidx), versuch(found, fidx)
			} else {
				je = int(feld(l, "zeilen_je_s") / st)
				if (ph == "export") {
					printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s (%s–%s) | %s | %s | %s | %s | %s | %s |\n", st, a, se, feld(l, "median_ms"), feld(l, "min_ms"), feld(l, "max_ms"), \
						feld(l, "zeilen_je_s"), je, feld(l, "erste_zeile_ms_median"), feld(l, "erste_zeile_ms_min"), feld(l, "erste_zeile_ms_max"), \
						feld(l, "bytes_je_zeile"), feld(l, "streuung"), vl, vermerk(l), fremd(fidx), versuch(found, fidx)
				} else {
					printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", st, a, se, feld(l, "median_ms"), feld(l, "min_ms"), feld(l, "max_ms"), \
						feld(l, "zeilen_je_s"), je, feld(l, "bytes_je_zeile"), feld(l, "streuung"), vl, vermerk(l), fremd(fidx), versuch(found, fidx)
				}
			}
		}
	}
	print "\n### Dauer je Schicht (Senke mit)\n"
	print "| Stufe | Aufbau | Phase | Span | Art | n | Median ms | Min ms | Max ms |"
	print "|---|---|---|---|---|---|---|---|---|"
	for (si = 1; si <= ns; si++) for (ai = 1; ai <= 2; ai++) for (k = 1; k <= nph; k++) {
		st = sl[si]; a = (ai == 1 ? "B" : "C"); ph = phn[k]
		for (i = 1; i <= zellen; i++) {
			z = ordnung[i]; split(z, q, "|")
			if (q[2] != a || q[3] != "mit" || q[4] != ph) continue
			c = waehle(z)
			if (c == "" || sitz[c] != st) continue
			for (j = 1; j <= nspan[c]; j++) {
				sp = span[c, j]
				art = feld(sp, "art")
				if (ph ~ /^punkt/ && art == "sql_batch") art = art " (Last-Sitzungen)"
				printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", st, a, ph, feld(sp, "span"), art, nach(sp, "n=[0-9]+"), nach(sp, "median=[0-9]+"), nach(sp, "min=[0-9]+"), nach(sp, "max=[0-9]+")
			}
			break
		}
	}
}
' "$datei"
