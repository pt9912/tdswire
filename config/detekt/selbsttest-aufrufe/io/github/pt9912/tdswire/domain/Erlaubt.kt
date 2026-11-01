package io.github.pt9912.tdswire.domain

internal object Erlaubt {
    fun zeit(): Long = System.currentTimeMillis()

    fun ausgeben() {
        println("x")
    }

    fun liste(): List<Int> = listOf(1)
}
