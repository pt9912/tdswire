package io.github.pt9912.tdswire.simulation

/** Von Hand gebaute Erwartungen: Werte und Terme. */
fun num(text: String): SimValue = SimValue.Number(text)

fun txt(value: String): SimValue = SimValue.Text(value, national = false)

fun ntxt(value: String): SimValue = SimValue.Text(value, national = true)

fun param(name: String): SimValue = SimValue.Parameter(name)

fun eq(column: String, value: SimValue): Term = Term.In(column, listOf(value))

fun inList(column: String, vararg values: SimValue): Term = Term.In(column, values.toList())

fun isNull(column: String): Term = Term.IsNull(column, negated = false)

fun isNotNull(column: String): Term = Term.IsNull(column, negated = true)
