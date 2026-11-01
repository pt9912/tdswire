**Vorgang:** slice-rpc-prepared-handles
**Fund:** Review MEDIUM-2: `withContext(NonCancellable) { handles.clear() }` in `RequestLoop` trug kein Test; die Zeile ohne `NonCancellable` ließ die Suite grün. Behoben, indem das `NonCancellable` entfiel (6e481cd).
