package com.example.pucktrivia.model

data class SkaterStatLeader(
    override val id: Int,
    override val firstName: String,
    override val lastName: String,
    override val sweaterNumber: Int?,
    override val teamAbbrev: String,
    val position: String,
    override val value: Double,
) : StatLeader {
    override val displayValue: String
        get() = value.toInt().toString()
}
