# `tools/testserver.sh` bricht bei `REQUIRED` ohne passende Benutzerliste erst mit „nicht bereit“ ab

**Sub-Area:** `*` (Werkzeug-Skript `tools/testserver.sh`, Betrieb des Testservers)

Wer `make testserver-up` mit der Verschlüsselungsrichtlinie `REQUIRED` startet und die Voreinstellungsliste (`{plain}secret`) behält, bekommt keinen frühen, klaren Abbruch des Skripts: der Server verweigert den Start (`plain` nur im Testmodus, [SPEC-051](../../../../../../spec/spezifikation.md#authentifizierungsadapter-vertrag-zu-spec-051)), und das Skript meldet erst nach Ablauf der Wartezeit „der Server wurde nicht bereit“. Die Ursache steht nur im Log des Containers. Die Anleitung beschreibt das Verhalten als abgeleitet und nicht gefahren.
