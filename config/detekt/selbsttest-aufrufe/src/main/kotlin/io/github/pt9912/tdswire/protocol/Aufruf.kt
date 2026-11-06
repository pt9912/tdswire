package io.github.pt9912.tdswire.protocol

internal object AusgabeAufruf {
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

    // erwartet: kotlin.Throwable.printStackTrace
    fun stapelAnStrom(grund: Throwable) {
        grund.printStackTrace(System.err)
    }

    // erwartet: kotlin.Throwable.printStackTrace
    fun stapelAnSchreiber(grund: Throwable) {
        grund.printStackTrace(java.io.PrintWriter(java.io.StringWriter()))
    }

    // erwartet: java.lang.Thread.dumpStack
    fun stapelDesThreads() {
        Thread.dumpStack()
    }

    // Eine Zeile `erwartet` je Aufruf: der Selbsttest zaehlt die Funde je Eintrag gegen die Zeilen.
    // erwartet: java.util.logging.Logger.log
    // erwartet: java.util.logging.Logger.log
    // erwartet: java.util.logging.Logger.log
    // erwartet: java.util.logging.Logger.log
    // erwartet: java.util.logging.Logger.log
    fun loggerLog(grund: Throwable) {
        val logger = java.util.logging.Logger.getLogger("x")
        logger.log(java.util.logging.Level.INFO, "x")
        logger.log(java.util.logging.Level.INFO, "x", grund)
        logger.log(java.util.logging.Level.INFO, "x", "p")
        logger.log(java.util.logging.Level.INFO) { "x" }
        logger.log(java.util.logging.LogRecord(java.util.logging.Level.INFO, "x"))
    }

    // erwartet: java.util.logging.Logger.logp
    fun loggerLogp() {
        java.util.logging.Logger.getLogger("x").logp(java.util.logging.Level.INFO, "c", "m", "x")
    }

    // erwartet: java.util.logging.Logger.logrb
    fun loggerLogrb() {
        java.util.logging.Logger.getLogger("x").logrb(java.util.logging.Level.INFO, null as java.util.ResourceBundle?, "x")
    }
}
