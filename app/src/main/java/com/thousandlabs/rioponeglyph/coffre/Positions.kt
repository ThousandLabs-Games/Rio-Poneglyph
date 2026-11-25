package com.thousandlabs.rioponeglyph.coffre

import android.content.Context

/** Position de reprise des vidéos (identifiants neutres uniquement). */
object Positions {
    private const val PREFS = "positions"

    fun lire(context: Context, id: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(id, 0L)

    fun enregistrer(context: Context, id: String, position: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(id, position).apply()
    }
}
