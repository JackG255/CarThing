package com.carthing.data.maintenance

import com.carthing.data.Language

/**
 * Names the app supplies (default components, service quick picks, deadline presets) in every language.
 * Entry names are stable keys; a stored name equal to any of an entry's labels is still a default and
 * follows the app's language (see `DefaultNameRelabeler`).
 */
enum class DefaultName(private val en: String, private val cs: String, private val sk: String) {
    ENGINE_OIL("Engine oil & filter", "Motorový olej a filtr", "Motorový olej a filter"),
    AIR_FILTER("Air filter", "Vzduchový filtr", "Vzduchový filter"),
    CABIN_FILTER("Cabin filter", "Kabinový filtr", "Kabínový filter"),
    BRAKE_FLUID("Brake fluid", "Brzdová kapalina", "Brzdová kvapalina"),
    BRAKE_PADS("Brake pads", "Brzdové destičky", "Brzdové platničky"),
    COOLANT("Coolant", "Chladicí kapalina", "Chladiaca kvapalina"),
    SPARK_PLUGS("Spark plugs", "Zapalovací svíčky", "Zapaľovacie sviečky"),
    TIMING_BELT("Timing belt", "Rozvodový řemen", "Rozvodový remeň"),
    TIRES("Tires", "Pneumatiky", "Pneumatiky"),
    TIRE_PRESSURE("Tire pressure", "Tlak v pneumatikách", "Tlak v pneumatikách"),
    BATTERY("Battery", "Akumulátor", "Akumulátor"),
    WIPER_BLADES("Wiper blades", "Lišty stěračů", "Lišty stieračov"),
    OIL_CHANGE("Oil change", "Výměna oleje", "Výmena oleja"),
    BRAKES("Brakes", "Brzdy", "Brzdy"),
    INSPECTION("Inspection", "Prohlídka", "Prehliadka"),
    FILTERS("Filters", "Filtry", "Filtre"),
    WIPERS("Wipers", "Stěrače", "Stierače"),
    REPAIR("Repair", "Oprava", "Oprava"),
    TECHNICAL_INSPECTION("Technical inspection (STK)", "Technická kontrola (STK)", "Technická kontrola (STK)"),
    EMISSIONS("Emissions check", "Měření emisí", "Emisná kontrola"),
    VIGNETTE("Motorway vignette", "Dálniční známka", "Diaľničná známka"),
    INSURANCE("Insurance", "Pojištění", "Poistenie");

    /** The name shown in [language]. */
    fun label(language: Language): String = when (language) {
        Language.EN -> en
        Language.CS -> cs
        Language.SK -> sk
    }

    /** Every label of this entry (en, cs, sk), without duplicates. */
    val all: List<String> = listOf(en, cs, sk).distinct()

    companion object {
        /** The entry whose label in any language equals `name.trim()` exactly (case-sensitive); null if none. */
        fun find(name: String): DefaultName? = name.trim().let { n -> entries.firstOrNull { n in it.all } }
    }
}
