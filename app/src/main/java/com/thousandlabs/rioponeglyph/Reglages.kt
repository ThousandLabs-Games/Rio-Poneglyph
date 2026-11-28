package com.thousandlabs.rioponeglyph

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class StyleAnimation(val libelle: String, val detail: String) {
    VAGUE("Vague de lumière", "La lumière descend du haut vers le bas"),
    SYMBOLES("Symboles un par un", "Chaque symbole s'allume à son tour, la serrure en dernier")
}

enum class DureeAnimation(val libelle: String, val revelationMs: Int) {
    COURTE("Courte (1,5 s)", 1500),
    NORMALE("Normale (2,5 s)", 2500),
    LONGUE("Longue (3,5 s)", 3500),
    TRES_LONGUE("Très longue (5 s)", 5000)
}

enum class DelaiVerrou(val libelle: String, val ms: Long) {
    IMMEDIAT("Immédiatement", 0L),
    S30("Après 30 secondes", 30_000L),
    M1("Après 1 minute", 60_000L),
    M5("Après 5 minutes", 5 * 60_000L)
}

/**
 * Réglages de l'application (sans aucune donnée sensible).
 * Chaque valeur est observable par l'interface et enregistrée immédiatement.
 */
object Reglages {

    private lateinit var prefs: SharedPreferences

    fun init(contexte: Context) {
        prefs = contexte.applicationContext.getSharedPreferences("reglages", Context.MODE_PRIVATE)
        animationOuverture = prefs.getBoolean(K_ANIMATION, true)
        styleAnimation = enumOu(prefs.getString(K_STYLE, null), StyleAnimation.VAGUE)
        dureeAnimation = enumOu(prefs.getString(K_DUREE, null), DureeAnimation.NORMALE)
        verrouArrierePlan = prefs.getBoolean(K_VERROU_AP, true)
        delaiVerrou = enumOu(prefs.getString(K_DELAI, null), DelaiVerrou.IMMEDIAT)
        confidentialite = prefs.getBoolean(K_CONFIDENTIALITE, true)
        episodeSuivantAuto = prefs.getBoolean(K_EPISODE, true)
        repriseVideo = prefs.getBoolean(K_REPRISE, true)
        gestesVideo = prefs.getBoolean(K_GESTES, true)
        chapitreSuivant = prefs.getBoolean(K_CHAPITRE, true)
        willOfD = prefs.getBoolean(K_WILL_OF_D, true)
    }

    var animationOuverture by mutableStateOf(true)
        private set
    var styleAnimation by mutableStateOf(StyleAnimation.VAGUE)
        private set
    var dureeAnimation by mutableStateOf(DureeAnimation.NORMALE)
        private set
    var verrouArrierePlan by mutableStateOf(true)
        private set
    var delaiVerrou by mutableStateOf(DelaiVerrou.IMMEDIAT)
        private set
    var confidentialite by mutableStateOf(true)
        private set
    var episodeSuivantAuto by mutableStateOf(true)
        private set
    var repriseVideo by mutableStateOf(true)
        private set
    var gestesVideo by mutableStateOf(true)
        private set
    var chapitreSuivant by mutableStateOf(true)
        private set
    var willOfD by mutableStateOf(true)
        private set

    fun changerAnimationOuverture(v: Boolean) { animationOuverture = v; ecrire(K_ANIMATION, v) }
    fun changerStyleAnimation(v: StyleAnimation) { styleAnimation = v; ecrire(K_STYLE, v.name) }
    fun changerDureeAnimation(v: DureeAnimation) { dureeAnimation = v; ecrire(K_DUREE, v.name) }
    fun changerVerrouArrierePlan(v: Boolean) { verrouArrierePlan = v; ecrire(K_VERROU_AP, v) }
    fun changerDelaiVerrou(v: DelaiVerrou) { delaiVerrou = v; ecrire(K_DELAI, v.name) }
    fun changerConfidentialite(v: Boolean) { confidentialite = v; ecrire(K_CONFIDENTIALITE, v) }
    fun changerEpisodeSuivantAuto(v: Boolean) { episodeSuivantAuto = v; ecrire(K_EPISODE, v) }
    fun changerRepriseVideo(v: Boolean) { repriseVideo = v; ecrire(K_REPRISE, v) }
    fun changerGestesVideo(v: Boolean) { gestesVideo = v; ecrire(K_GESTES, v) }
    fun changerChapitreSuivant(v: Boolean) { chapitreSuivant = v; ecrire(K_CHAPITRE, v) }
    fun changerWillOfD(v: Boolean) { willOfD = v; ecrire(K_WILL_OF_D, v) }

    private fun ecrire(cle: String, v: Boolean) = prefs.edit().putBoolean(cle, v).apply()
    private fun ecrire(cle: String, v: String) = prefs.edit().putString(cle, v).apply()

    private inline fun <reified T : Enum<T>> enumOu(nom: String?, defaut: T): T =
        nom?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: defaut

    private const val K_ANIMATION = "animation_ouverture"
    private const val K_STYLE = "style_animation"
    private const val K_DUREE = "duree_animation"
    private const val K_VERROU_AP = "verrou_arriere_plan"
    private const val K_DELAI = "delai_verrou"
    private const val K_CONFIDENTIALITE = "confidentialite"
    private const val K_EPISODE = "episode_suivant_auto"
    private const val K_REPRISE = "reprise_video"
    private const val K_GESTES = "gestes_video"
    private const val K_CHAPITRE = "chapitre_suivant"
    private const val K_WILL_OF_D = "will_of_d"
}
