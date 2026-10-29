package io.github.pt9912.tdswire.port

import kotlin.system.exitProcess

internal object Aufruf {
    // erwartet: java.lang.System.exit
    fun beenden() {
        System.exit(0)
    }

    // erwartet: kotlin.system.exitProcess
    fun beendenKotlin() {
        exitProcess(1)
    }

    // erwartet: java.lang.Runtime.getRuntime
    // erwartet: java.lang.Runtime.exec
    fun prozess() {
        val laufzeit = Runtime.getRuntime()
        laufzeit.exec("x")
    }

    // erwartet: java.lang.ProcessBuilder.<init>
    fun prozessBauen() {
        ProcessBuilder("x")
    }

    // erwartet: java.lang.ProcessHandle.current
    fun prozessKennung() {
        ProcessHandle.current()
    }
}
