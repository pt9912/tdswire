# tools/mk/selbstpruefung-marker.mk — Marker dieses Repos fuer `make selbstpruefung`
# (Fragment: harness/mk/selbstpruefung.mk, dort mit ?= vorbelegt).
#
# Der Traeger .githooks/commit-msg ist repo-eigen und erkennt zusaetzlich die Kennungen des
# Vertrags-Praefix TDS (harness/conventions/MR-001-commit-hook-tds-praefix.md). Die gruene
# Message traegt darum eine TDS-Kennung: die Selbstpruefung belegt damit diesen Zweig des
# Traegers. Die rote Message bleibt die Vorgabe des Fragments, sie traegt keine Kennung.
SELBSTPRUEFUNG_MSG_GRUEN = Selbstpruefung mit Kennung TDS-FA-01
