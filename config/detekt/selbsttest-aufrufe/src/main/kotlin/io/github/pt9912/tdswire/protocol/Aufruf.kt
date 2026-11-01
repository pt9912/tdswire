package io.github.pt9912.tdswire.protocol

internal object Aufruf {
    // erwartet: kotlin.io.println
    fun ausgeben() {
        println("x")
    }

    // erwartet: kotlin.io.print
    fun schreiben() {
        print("x")
    }

    // erwartet: java.io.PrintStream.println
    fun standardausgabe() {
        System.out.println("x")
    }

    // erwartet: java.io.PrintStream.print
    fun fehlerkanal() {
        System.err.print("x")
    }

    // erwartet: kotlin.Throwable.printStackTrace
    fun stapel(grund: Throwable) {
        grund.printStackTrace()
    }
}
