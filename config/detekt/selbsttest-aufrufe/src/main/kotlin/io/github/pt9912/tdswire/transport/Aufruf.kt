package io.github.pt9912.tdswire.transport

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

    // erwartet: java.io.PrintStream.write
    fun bytes() {
        System.out.write(1)
    }

    // erwartet: java.io.PrintStream.printf
    fun formatiert() {
        System.out.printf("x")
    }

    // erwartet: java.io.PrintStream.format
    fun format() {
        System.out.format("x")
    }

    // erwartet: java.util.logging.Logger.info
    fun logger() {
        java.util.logging.Logger.getLogger("x").info("x")
    }

    // erwartet: java.util.logging.Logger.warning
    fun loggerWarnung() {
        java.util.logging.Logger.getLogger("x").warning("x")
    }

    // erwartet: java.util.logging.Logger.severe
    fun loggerFehler() {
        java.util.logging.Logger.getLogger("x").severe("x")
    }

    // erwartet: kotlin.Throwable.printStackTrace
    fun stapel(grund: Throwable) {
        grund.printStackTrace()
    }
}
