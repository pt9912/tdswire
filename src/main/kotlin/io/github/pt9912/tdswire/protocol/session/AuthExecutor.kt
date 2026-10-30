package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException

/** Was der Adapteraufruf ergab: ein Ergebnis oder der Typ einer Ausnahme. */
internal sealed interface AuthOutcome {
    class Decided(val result: AuthResult) : AuthOutcome

    class Failed(val exceptionType: String) : AuthOutcome
}

/**
 * Ruft den blockierenden [AuthAdapter] abseits des Transport-Threads (`spec/spezifikation.md` `SPEC-051`,
 * `SPEC-008`). Der Aufruf läuft auf [dispatcher] in einem eigenen Geltungsbereich, nicht als Kind des
 * Wartenden. Bricht der Aufrufer ab ([Deferred.cancel], etwa nach Fristablauf), gilt:
 *
 * - Ein laufender Adapteraufruf ist nicht unterbrechbar; er läuft zu Ende, sein Ergebnis wird
 *   verworfen, und er belegt bis dahin einen der [AuthPool.parallelism] Plätze.
 * - Ein Aufruf, der noch auf einen Platz wartet, beginnt nie; der Adapter wird nicht gerufen.
 *
 * Je [AuthPool] laufen nie mehr als [AuthPool.parallelism] Adapteraufrufe zugleich, auch über mehrere
 * Executor und Treiber hinweg (Obergrenze der verwaisten Aufrufe). Die
 * Wartenden reihen sich dahinter ein, ihre Zahl begrenzt nur die Zahl der Verbindungen; eine
 * Drosselung wiederholter Anmeldeversuche ist das nicht (`slice-qa-sicherheit`). Das Passwort ist
 * überschrieben, sobald der Aufruf endet, auch bei einer Ausnahme und auch wenn er nie begann; bei einem
 * wartenden, abgebrochenen Aufruf ist das erst, wenn die Warteschlange ihn erreicht.
 *
 * Eine `CancellationException` des Adapters gilt als Abbruch nur, wenn der Aufruf selbst abgebrochen ist
 * (Zustand des eigenen Jobs); sie wird dann weitergeworfen. Jede andere ist ein Adapterfehler und ergibt
 * [AuthOutcome.Failed] wie jede andere Ausnahme. Das ist eine Festlegung, nicht gemessen.
 *
 * Festlegung ohne Messung: Voreinstellung sind [AuthPool.DEFAULT_PARALLELISM] Plätze auf
 * `Dispatchers.IO`; ein Pbkdf2-Aufruf ist CPU-gebunden, eine Messung der Wirkung auf den Transport
 * und der richtigen Zahl liegt nicht vor.
 */
internal class AuthExecutor(pool: AuthPool) {
    private val scope = CoroutineScope(SupervisorJob() + pool.limited)

    fun start(adapter: AuthAdapter, request: AuthRequest): Deferred<AuthOutcome> {
        val call = scope.async {
            try {
                val attempt = runCatching { adapter.authenticate(request) }
                val failure = attempt.exceptionOrNull()
                if (failure is CancellationException && !currentCoroutineContext().isActive) throw failure
                if (failure != null) {
                    AuthOutcome.Failed(failure.javaClass.name)
                } else {
                    AuthOutcome.Decided(attempt.getOrThrow())
                }
            } finally {
                request.password.wipe()
            }
        }
        // Ein vor dem Start abgebrochener Aufruf führt seinen Rumpf nicht aus; das Passwort wird hier überschrieben.
        call.invokeOnCompletion { request.password.wipe() }
        return call
    }
}

/**
 * Wo und wie breit der Anmeldepfad den blockierenden Adapter ruft: auf [dispatcher] höchstens
 * [parallelism] Aufrufe zugleich. Festlegung ohne Messung: Voreinstellung `Dispatchers.IO` mit
 * [DEFAULT_PARALLELISM] Plätzen.
 */
class AuthPool(val dispatcher: CoroutineDispatcher = Dispatchers.IO, val parallelism: Int = DEFAULT_PARALLELISM) {
    /** Die begrenzte Sicht auf [dispatcher]; sie entsteht einmal je Pool und gilt für alle Executor, die ihn teilen. */
    internal val limited: CoroutineDispatcher = dispatcher.limitedParallelism(parallelism)

    companion object {
        const val DEFAULT_PARALLELISM = 4
    }
}
