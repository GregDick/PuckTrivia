package com.example.pucktrivia.model

import java.io.Serializable

/**
 * [Serializable] so implementations can ride inside [com.example.pucktrivia.data.GameSnapshot]
 * through [androidx.lifecycle.SavedStateHandle]. Java serialization is used instead of `@Parcelize`
 * because the parcelize compiler plugin does not activate under AGP 9's built-in Kotlin (see
 * https://issuetracker.google.com/issues/389977429).
 */
interface StatLeader : Serializable {
    val id: Int
    val firstName: String
    val lastName: String
    val sweaterNumber: Int?
    val teamAbbrev: String
    val value: Double
    val displayValue: String
}
