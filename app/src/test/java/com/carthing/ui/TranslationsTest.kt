package com.carthing.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/** Every translatable string and plural in English must exist in Czech and Slovak, and nothing more. */
class TranslationsTest {
    private class Strings(val names: Set<String>, val pluralItems: Map<String, Set<String>>)

    private fun read(folder: String): Strings {
        val file = File("src/main/res/$folder/strings.xml")
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        fun elements(tag: String) = root.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }
        val strings = elements("string").filter { it.getAttribute("translatable") != "false" }.map { it.getAttribute("name") }
        val plurals = elements("plurals").associate { p ->
            val items = p.getElementsByTagName("item")
            p.getAttribute("name") to (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
        }
        return Strings((strings + plurals.keys).toSet(), plurals)
    }

    @Test fun czechAndSlovakMatchEnglish() {
        val english = read("values").names
        listOf("values-cs", "values-sk").forEach { folder ->
            val translated = read(folder)
            assertEquals("missing in $folder", emptySet<String>(), english - translated.names)
            assertEquals("extra in $folder", emptySet<String>(), translated.names - english)
        }
    }

    @Test fun czechAndSlovakPluralsHaveEveryQuantity() {
        listOf("values-cs", "values-sk").forEach { folder ->
            read(folder).pluralItems.forEach { (name, quantities) ->
                assertEquals("$name in $folder", setOf("one", "few", "many", "other"), quantities)
            }
        }
    }
}
