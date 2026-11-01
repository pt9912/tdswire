#!/usr/bin/env bash
# tools/stempel-liste.sh - Lesen einer Eingangsliste, eine Syntax fuer tools/stempel.sh und
# tools/stempel-eingaenge-pruefung.sh (ADR-0024 Entscheidung 10, TDS-QA-06). Wird mit `.` eingebunden, nicht aufgerufen.
#
# stempel_liste_lesen <datei> <array>: setzt <array> auf die Eintraege, sortiert und ohne Doppelte. Ein Eintrag ist ein
# literaler Pfad je Zeile, Verzeichnisse gelten rekursiv, `#` beginnt einen Kommentar, eine letzte Zeile ohne Zeilenende
# zaehlt. Platzhalter, Leerzeichen, absolute Pfade, `:` und `..` sind Fehler; ebenso eine fehlende oder leere Liste. Bei einem
# Fehler ist der Rueckgabewert 1 und WARUM gesetzt.
# Eingaenge: dieses Skript ist Eingang beider Klassen (tools/stempel/*.eingaenge nennen es).
stempel_liste_lesen() {
	local datei=$1 zeile
	local -n ziel=$2
	local -a roh=()
	ziel=()
	[ -f "$datei" ] || { WARUM="Eingangsliste fehlt: $datei"; return 1; }
	while IFS= read -r zeile || [ -n "$zeile" ]; do
		zeile=${zeile%%#*}
		zeile=${zeile#"${zeile%%[![:space:]]*}"}
		zeile=${zeile%"${zeile##*[![:space:]]}"}
		[ -n "$zeile" ] || continue
		while [[ $zeile == ./* ]]; do zeile=${zeile#./}; done
		while [[ $zeile == */ && $zeile != / ]]; do zeile=${zeile%/}; done
		if [[ $zeile == *[\*\?]* || $zeile == *\[* || $zeile == *\]* || $zeile == *' '* \
			|| $zeile == /* || $zeile == *..* || $zeile == *:* ]]; then
			WARUM="Eintrag '$zeile' in $datei ist kein literaler Pfad ohne Platzhalter und ohne '..'"
			return 1
		fi
		roh+=("$zeile")
	done < "$datei"
	[ ${#roh[@]} -gt 0 ] || { WARUM="Eingangsliste leer: $datei"; return 1; }
	mapfile -t ziel < <(printf '%s\n' "${roh[@]}" | sort -u)
}
