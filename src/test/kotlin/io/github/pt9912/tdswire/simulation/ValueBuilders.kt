package io.github.pt9912.tdswire.simulation

/** Von Hand gebaute Erwartungen: Werte und Terme. */
internal fun num(text: String): SimValue = SimValue.Number(text)

internal fun txt(value: String): SimValue = SimValue.Text(value, national = false)

internal fun ntxt(value: String): SimValue = SimValue.Text(value, national = true)

internal fun param(name: String): SimValue = SimValue.Parameter(name)

internal fun eq(column: String, value: SimValue): Term = Term.In(column, listOf(value))

internal fun inList(column: String, vararg values: SimValue): Term = Term.In(column, values.toList())

internal fun isNull(column: String): Term = Term.IsNull(column, negated = false)

internal fun isNotNull(column: String): Term = Term.IsNull(column, negated = true)
