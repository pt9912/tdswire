package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryEvent

/** Die erste Implementierung der Suite [QueryBackendContract]: das skriptbare Test-Backend. */
class ScriptedBackendContractTest : QueryBackendContract() {
    override fun backend(script: List<QueryEvent>): QueryBackend = ScriptedBackend.ofList(*script.toTypedArray())

    override fun generating(rows: Int): GeneratingBackend {
        val scripted = ScriptedBackend.generating(rows)
        return object : GeneratingBackend {
            override val backend: QueryBackend = scripted
            override val rowsPulled: Int get() = scripted.rowsPulled
        }
    }
}
