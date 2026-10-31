package io.github.pt9912.tdswire.runtime

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Prüft die Datei `LICENSE` im Wurzelverzeichnis gegen den MIT-Text (Stage `test` kopiert sie nach `/src`). */
internal class LizenzTest {
    /**
     * Die Datei beginnt mit `MIT License`, nennt den Rechteinhaber und enthält die Rechteeinräumung und den
     * Haftungsausschluss der MIT-Lizenz; fehlt die Datei, bricht der Test.
     *
     * @spec SPEC-042
     */
    @Test
    fun `LICENSE im Wurzelverzeichnis ist die MIT-Lizenz`() {
        val file = File("LICENSE")
        assertTrue(file.isFile, "LICENSE fehlt im Wurzelverzeichnis ${file.absoluteFile.parent}")
        val text = file.readText()
        assertTrue(text.startsWith("MIT License\n"), "erste Zeile: ${text.lineSequence().first()}")
        assertTrue("Copyright (c) 2026 pt9912" in text)
        assertTrue("Permission is hereby granted, free of charge, to any person obtaining a copy" in text)
        assertTrue("THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND" in text)
    }
}
