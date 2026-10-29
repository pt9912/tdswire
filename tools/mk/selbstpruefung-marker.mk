# tools/mk/selbstpruefung-marker.mk — Marker dieses Repos fuer `make selbstpruefung`
# (Fragment: harness/mk/selbstpruefung.mk, dort mit ?= vorbelegt).
#
# Der Traeger .githooks/commit-msg ist repo-eigen und erkennt zusaetzlich die Kennungen des
# Vertrags-Praefix TDS (harness/conventions/MR-001-commit-hook-tds-praefix.md). Die gruene
# Message traegt darum eine TDS-Kennung: die Selbstpruefung belegt damit diesen Zweig des
# Traegers. Die rote Message bleibt die Vorgabe des Fragments, sie traegt keine Kennung.
SELBSTPRUEFUNG_MSG_GRUEN = Selbstpruefung mit Kennung TDS-FA-01

# Kopplung: `make selbstpruefung` klont nach mktemp und mountet den Klon in den d-check-Container.
# Laeuft Docker in einer eigenen VM (Colima), ist das Temp-Verzeichnis des Hosts (/var/folders/...)
# im Container leer und d-check meldet "Scan-Wurzel ist leer". Dann liegt TMPDIR unter $HOME:
#   mkdir -p $HOME/.cache/tdswire-selbstpruefung && TMPDIR=$HOME/.cache/tdswire-selbstpruefung make selbstpruefung
