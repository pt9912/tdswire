package io.github.pt9912.tdswire.runtime

internal object Erlaubt {
    fun zeit(): Long = System.currentTimeMillis()

    fun text(): String = StringBuilder().append("x").toString()

    fun liste(): List<Int> = listOf(1)
}
