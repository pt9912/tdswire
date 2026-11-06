**Vorgang:** slice-spec-abdeckung-liest-csharp
**Fund:** Der Bericht der C#-Tests hängt beim Bau der Stage am Layer-Cache; `make spec-abdeckung` erzwingt diesen Lauf deshalb mit `--no-cache-filter test-bericht` (Setzung des Implementers, `tools/mk/spec-abdeckung.mk`). Der Gradle-Teil der Matrix hängt weiter am Layer-Cache seiner Quellen und ist von dieser Wahl nicht berührt.

Verifikation V-5 (INFO), `slice-spec-abdeckung-liest-csharp`: der Gradle-Teil der Matrix zeigte sich im Verifikationslauf als `CACHED`; die Matrix war trotzdem gleich. Der Kopf des Erzeugers nennt nur den TRX-Teil.
