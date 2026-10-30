package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlin.coroutines.cancellation.CancellationException

/** Was der Adapteraufruf ergab: ein Ergebnis oder der Typ einer Ausnahme. */
internal sealed interface AuthOutcome {
    class Decided(val result: AuthResult) : AuthOutcome

    class Failed(val exceptionType: String) : AuthOutcome
}

/**
 * Ruft den blockierenden [AuthAdapter] abseits des Transport-Threads (`contracts/auth-adapter.md`,
 * `SPEC-008`). Der Aufruf läuft auf [dispatcher] in einem eigenen Geltungsbereich, nicht als Kind des
 * Wartenden: bricht die Frist das Warten ab, läuft der blockierende Aufruf zu Ende (er ist nicht
 * unterbrechbar) und sein Ergebnis wird verworfen. Das Passwort ist nach dem Aufruf überschrieben,
 * auch wenn der Aufruf mit einer Ausnahme endet.
 *
 * Festlegung ohne Messung: Voreinstellung des Aufrufers ist `Dispatchers.IO`, der für blockierende
 * Aufrufe gedacht ist und die Coroutinen des Transports nicht verdrängt; ein Pbkdf2-Aufruf ist
 * CPU-gebunden, eine Messung der Wirkung auf den Transport liegt nicht vor.
 */
internal class AuthExecutor(dispatcher: CoroutineDispatcher) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun start(adapter: AuthAdapter, request: AuthRequest): Deferred<AuthOutcome> = scope.async {
        try {
            val attempt = runCatching { adapter.authenticate(request) }
            val failure = attempt.exceptionOrNull()
            if (failure is CancellationException) throw failure
            if (failure != null) {
                AuthOutcome.Failed(failure.javaClass.name)
            } else {
                AuthOutcome.Decided(attempt.getOrThrow())
            }
        } finally {
            request.password.wipe()
        }
    }
}
