package io.github.pt9912.tdswire.simulation

/** Von Hand gebaute Erwartungen: Anweisungen. */
fun tableRef(name: String, schema: String? = null): TableRef = TableRef(schema, name)

fun select(table: TableRef, columns: List<String>?, vararg where: Term): Statement =
    Statement.Select(table, columns, where.toList())

fun insert(table: TableRef, columns: List<String>, vararg values: SimValue): Statement =
    Statement.Insert(table, columns, values.toList())

fun update(table: TableRef, set: List<Assignment>, vararg where: Term): Statement =
    Statement.Update(table, set, where.toList())

fun delete(table: TableRef, vararg where: Term): Statement = Statement.Delete(table, where.toList())

fun set(column: String, value: SimValue): Assignment = Assignment(column, value)
